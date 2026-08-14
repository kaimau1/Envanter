package com.envanter.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.envanter.app.data.Settings
import com.envanter.app.gemini.VoiceInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Basılı tut, konuş" durumu. [press] ile dinleme başlar, [release] ile biter;
 * sonuç ancak [release] sonrasında tek parça hâlinde teslim edilir.
 */
@Stable
class HoldToTalk internal constructor(
    private val listeningState: MutableState<Boolean>,
    private val heardState: MutableState<String>,
    private val messageState: MutableState<String>,
    private val onPress: () -> Unit,
    private val onRelease: () -> Unit
) {
    /** Şu an mikrofon açık mı? */
    val listening: Boolean get() = listeningState.value

    /** O ana kadar duyulan metin (yalnız cihaz tanıyıcısı modunda dolar). */
    val heard: String get() = heardState.value

    /** Kullanıcıya gösterilecek durum/hata metni. */
    val message: String get() = messageState.value

    fun press() = onPress()
    fun release() = onRelease()

    fun setMessage(text: String) { messageState.value = text }
    fun clear() { heardState.value = ""; messageState.value = "" }
}

/**
 * Parmak basılı olduğu sürece kesilmeyen ses girişi.
 *
 * İki mod vardır ve hangisinin kullanılacağına Gemini anahtarının varlığı karar verir:
 *
 * 1. **Kayıt modu (varsayılan, anahtar varsa):** parmak basılıyken ses doğrudan
 *    dosyaya kaydedilir ve bırakınca kayıt Gemini'ye dinletilir. Cihazın ses
 *    tanıyıcısı hiç devreye girmediği için sessizlikte oturum kapanması,
 *    "yeniden başlatma" boşlukları ve yarıda kesilme diye bir şey yoktur —
 *    cümleler arasında istediğin kadar durabilirsin.
 * 2. **Cihaz tanıyıcısı modu (anahtar yoksa):** SpeechRecognizer sürülür; tanıyıcı
 *    sessizlikte oturumu kapatırsa metin biriktirilip dinleme sürdürülür.
 *
 * [onCaptured] yalnızca parmak çekildikten sonra, bir kez çağrılır.
 */
@Composable
fun rememberHoldToTalk(onCaptured: (VoiceInput) -> Unit): HoldToTalk {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onFinal by rememberUpdatedState(onCaptured)

    val listening = remember { mutableStateOf(false) }
    val heard = remember { mutableStateOf("") }
    val message = remember { mutableStateOf("") }
    val holding = remember { mutableStateOf(false) }

    // Anahtar varsa kayıt modu; yoksa cihazın tanıyıcısı. Ayarlar değişirse anında yansır.
    var recordMode by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        Settings.geminiKey(context).map { it.isNotBlank() }.collect { recordMode = it }
    }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        message.value = if (ok) "Hazır — basılı tutup konuş." else "Mikrofon izni verilmedi."
    }

    // ---- Kayıt modu ----
    val recorder = remember { HoldRecorder(context) }
    DisposableEffect(recorder) { onDispose { recorder.discard() } }

    // Kayıt sürerken geçen saniyeyi göster: kullanıcı dinlemenin sürdüğünü görsün.
    LaunchedEffect(listening.value, recordMode) {
        if (!listening.value || !recordMode) return@LaunchedEffect
        while (listening.value) {
            val sec = (recorder.elapsedMs / 1000).toInt()
            message.value = "🔴 Dinleniyor… $sec sn (parmağını çekince gönderilir)"
            delay(500)
        }
    }

    // ---- Cihaz tanıyıcısı modu ----
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizer = remember { if (available) SpeechRecognizer.createSpeechRecognizer(context) else null }
    val active = remember { mutableStateOf(false) }
    val transcript = remember { mutableStateOf("") }
    val partial = remember { mutableStateOf("") }
    val startedAt = remember { mutableStateOf(0L) }
    val delivered = remember { AtomicBoolean(false) }

    // Tek örnek: tanıyıcının geri çağrıları bu nesneyi yakalar, her yeniden
    // çizimde yenisi oluşmaz.
    val ctrl = remember {
        object {
            fun begin() {
                if (recognizer == null) return
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    // Sessizlik toleransını uzat: yeniden başlatma sayısı azalsın.
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 60_000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8_000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8_000L)
                }
                active.value = true
                listening.value = true
                runCatching { recognizer.startListening(intent) }.onFailure {
                    active.value = false
                    listening.value = false
                    message.value = "Ses tanıma başlatılamadı."
                }
            }

            /** Gelen parçayı biriken metne ekler. */
            fun absorb(text: String?) {
                val t = text?.trim().orEmpty()
                if (t.isNotBlank()) {
                    // Aynı parça tekrar gelirse (kısmi + nihai) iki kez eklenmesin.
                    if (!transcript.value.endsWith(t)) {
                        transcript.value = "${transcript.value} $t".trim()
                    }
                }
                partial.value = ""
                heard.value = transcript.value
            }

            /**
             * Parmak hâlâ basılıysa dinlemeyi sürdür. Sınır "kaç kez yeniden
             * başladık" değil, "ne kadar süredir basılı" olmalı: sessiz bir ortamda
             * tanıyıcı saniyede bir kapanabiliyor ve sayı sınırı konuşmanın
             * ortasında dinlemeyi öldürüyordu.
             */
            fun keepListening(busy: Boolean = false) {
                if (SystemClock.elapsedRealtime() - startedAt.value > MAX_HOLD_MS) {
                    message.value = "Dinleme sınırına ulaşıldı — parmağını çek."
                    listening.value = false
                    return
                }
                scope.launch {
                    // Tanıyıcı "meşgul" derse biraz daha bekle, yoksa aynı hataya düşeriz.
                    delay(if (busy) 350 else 120)
                    if (holding.value && !active.value) begin()
                }
            }

            /** Parmak çekildikten sonra biriken metni bir kez teslim eder. */
            fun deliver() {
                listening.value = false
                if (!delivered.compareAndSet(false, true)) return
                val text = "${transcript.value} ${partial.value}".trim()
                if (text.isBlank()) {
                    message.value = "Duyamadım — tuşu basılı tutup konuş."
                    return
                }
                heard.value = text
                onFinal(VoiceInput.Text(text))
            }

            fun pressRecognizer() {
                if (!available) { message.value = "Bu cihazda ses tanıma yok."; return }
                delivered.set(false)
                transcript.value = ""
                partial.value = ""
                heard.value = ""
                startedAt.value = SystemClock.elapsedRealtime()
                message.value = "Dinleniyor…"
                holding.value = true
                begin()
            }

            fun releaseRecognizer() {
                holding.value = false
                if (active.value) {
                    runCatching { recognizer?.stopListening() }
                    // Emniyet ağı: bazı cihazlarda stopListening sonrası hiç
                    // geri çağrı gelmiyor; metin elimizde kalmasın.
                    scope.launch {
                        delay(2500)
                        if (!delivered.get()) deliver()
                    }
                } else deliver()
            }

            fun pressRecorder() {
                if (!recorder.start()) {
                    message.value = "Mikrofon açılamadı, tekrar dene."
                    return
                }
                holding.value = true
                heard.value = ""
                listening.value = true
                message.value = "🔴 Dinleniyor… (parmağını çekince gönderilir)"
            }

            fun releaseRecorder() {
                holding.value = false
                listening.value = false
                val capture = recorder.stop()
                when {
                    capture == null -> message.value = "Çok kısa — tuşu basılı tutup konuş."
                    else -> {
                        message.value = "İşleniyor…"
                        onFinal(VoiceInput.Audio(capture.file, HoldRecorder.MIME, capture.seconds))
                    }
                }
            }

            fun press() {
                if (holding.value) return
                if (!granted) { permission.launch(Manifest.permission.RECORD_AUDIO); return }
                if (recordMode) pressRecorder() else pressRecognizer()
            }

            fun release() {
                if (!holding.value) return
                if (recordMode) releaseRecorder() else releaseRecognizer()
            }
        }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (transcript.value.isBlank()) message.value = "Dinleniyor…"
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { active.value = false }

            override fun onPartialResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?.let {
                        partial.value = it
                        heard.value = "${transcript.value} $it".trim()
                    }
            }

            override fun onResults(results: Bundle?) {
                active.value = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: partial.value
                ctrl.absorb(text)
                // Parmak basılıysa konuşma bitmedi: analize geçmek yerine dinlemeyi sürdür.
                if (holding.value) ctrl.keepListening() else ctrl.deliver()
            }

            override fun onError(error: Int) {
                active.value = false
                if (partial.value.isNotBlank()) ctrl.absorb(partial.value)
                val soft = error in SOFT_ERRORS
                when {
                    holding.value && soft -> ctrl.keepListening(busy = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
                    holding.value -> {
                        listening.value = false
                        message.value = errorText(error)
                    }
                    transcript.value.isNotBlank() -> ctrl.deliver()
                    else -> {
                        listening.value = false
                        message.value = errorText(error)
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { recognizer?.destroy() }
    }

    return remember { HoldToTalk(listening, heard, message, ctrl::press, ctrl::release) }
}

/**
 * "Basılı tut" hareketi. Compose'un hazır `detectTapGestures` / `clickable`
 * çözümleri parmak birkaç piksel kayınca ya da kaydırılabilir bir kap araya
 * girince basışı İPTAL ediyor; kullanıcı hâlâ tuşa basıyorken dinleme
 * kesiliyordu. Burada ilk dokunuş tüketilip parmak kalkana kadar hiçbir şey
 * hareketi geri alamıyor.
 */
fun Modifier.holdToTalkGesture(
    enabled: Boolean = true,
    onPress: () -> Unit,
    onRelease: () -> Unit
): Modifier = this.pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        onPress()
        try {
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { if (it.pressed) it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        } finally {
            onRelease()
        }
    }
}

/**
 * Basılı tutulduğu sürece ses kaydeden basit sarmalayıcı. AAC/ADTS seçildi:
 * Gemini'nin doğrudan kabul ettiği biçimlerden biri ve 16 kHz mono 32 kbit ile
 * bir dakikalık konuşma ~240 KB ediyor.
 */
private class HoldRecorder(private val context: Context) {
    data class Capture(val file: File, val seconds: Int)

    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L

    val elapsedMs: Long get() = if (startedAt == 0L) 0L else SystemClock.elapsedRealtime() - startedAt

    fun start(): Boolean {
        stopQuietly()
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        // Eski kayıtlar birikmesin; hâlâ analiz edilmekte olabilecek son
        // dakikalardaki dosyalara dokunulmaz.
        runCatching {
            val cutoff = System.currentTimeMillis() - STALE_FILE_MS
            dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        }
        val file = File(dir, "hold_${System.currentTimeMillis()}.aac")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context)
        else @Suppress("DEPRECATION") MediaRecorder()
        return runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(32_000)
            r.setMaxDuration(MAX_RECORD_MS)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            output = file
            startedAt = SystemClock.elapsedRealtime()
            true
        }.getOrElse {
            runCatching { r.release() }
            runCatching { file.delete() }
            false
        }
    }

    /** Kaydı bitirir. Çok kısa ya da boş kayıtta null döner. */
    fun stop(): Capture? {
        val ms = elapsedMs
        val file = output
        // Süre dolduğunda MediaRecorder kendi durur; stop() o hâlde hata atar ama
        // dosya geçerlidir, bu yüzden hatayı yutup dosya boyutuna bakıyoruz.
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        output = null
        startedAt = 0L
        if (file == null) return null
        if (ms < MIN_RECORD_MS || file.length() < MIN_RECORD_BYTES) {
            runCatching { file.delete() }
            return null
        }
        return Capture(file, ((ms + 999) / 1000).toInt())
    }

    fun discard() {
        stopQuietly()
    }

    private fun stopQuietly() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { output?.delete() }
        output = null
        startedAt = 0L
    }

    companion object {
        const val MIME = "audio/aac"

        /** Bu yaşı geçmiş kayıt dosyaları bir sonraki basışta silinir. */
        private const val STALE_FILE_MS = 10 * 60 * 1000L
    }
}

/** Sessizlik/eşleşmeme gibi, dinlemeyi sürdürmeyi engellemeyen hatalar. */
private val SOFT_ERRORS = setOf(
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    SpeechRecognizer.ERROR_CLIENT,
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY
)

/** Tek basışta en fazla ne kadar dinlenir (cihaz tanıyıcısı modu). */
private const val MAX_HOLD_MS = 3 * 60 * 1000L

/** Kayıt modunda üst sınır; unutulan parmak diski doldurmasın. */
private const val MAX_RECORD_MS = 3 * 60 * 1000

/** Bunun altındaki basışlar "yanlışlıkla dokundum" sayılır. */
private const val MIN_RECORD_MS = 500L
private const val MIN_RECORD_BYTES = 600L

private fun errorText(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Duyamadım — tuşu basılı tutup konuş."
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Ses tanıma için internet gerekiyor."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni gerekiyor."
    SpeechRecognizer.ERROR_AUDIO -> "Mikrofona erişilemedi."
    SpeechRecognizer.ERROR_SERVER -> "Ses tanıma sunucusu yanıt vermedi."
    else -> "Ses tanıma hatası ($error)"
}
