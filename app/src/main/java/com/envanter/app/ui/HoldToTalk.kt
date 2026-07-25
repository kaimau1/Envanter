package com.envanter.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Basılı tut, konuş" durumu. [press] ile dinleme başlar, [release] ile biter;
 * metin ancak [release] sonrasında tek parça hâlinde teslim edilir.
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

    /** O ana kadar duyulan (biriken) metin. */
    val heard: String get() = heardState.value

    /** Kullanıcıya gösterilecek durum/hata metni. */
    val message: String get() = messageState.value

    fun press() = onPress()
    fun release() = onRelease()

    fun setMessage(text: String) { messageState.value = text }
    fun clear() { heardState.value = ""; messageState.value = "" }
}

/**
 * Parmak basılı olduğu sürece dinlemeye devam eden ses tanıma.
 *
 * Cihazın tanıyıcısı sessizlik görünce oturumu kendi kapatıp sonuç döndürüyor;
 * bu yüzden konuşmanın ortasında kesiliyordu. Burada parmak hâlâ basılıyken
 * gelen her sonuç metne EKLENİP tanıyıcı yeniden başlatılıyor, yani cümleler
 * arasında durabilirsin. [onFinalText] yalnızca parmak çekildikten sonra,
 * biriken metnin tamamıyla bir kez çağrılır.
 */
@Composable
fun rememberHoldToTalk(onFinalText: (String) -> Unit): HoldToTalk {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onFinal by rememberUpdatedState(onFinalText)

    val listening = remember { mutableStateOf(false) }
    val heard = remember { mutableStateOf("") }
    val message = remember { mutableStateOf("") }
    val holding = remember { mutableStateOf(false) }
    val active = remember { mutableStateOf(false) }
    val transcript = remember { mutableStateOf("") }
    val partial = remember { mutableStateOf("") }
    val restarts = remember { mutableStateOf(0) }
    val delivered = remember { AtomicBoolean(false) }

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

    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizer = remember { if (available) SpeechRecognizer.createSpeechRecognizer(context) else null }

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

            /** Parmak hâlâ basılıysa dinlemeyi sürdür. */
            fun keepListening() {
                if (restarts.value >= MAX_RESTARTS) {
                    message.value = "Dinleme sınırına ulaşıldı — parmağını çek."
                    listening.value = false
                    return
                }
                restarts.value++
                scope.launch {
                    delay(80)
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
                onFinal(text)
            }

            fun press() {
                if (!available) { message.value = "Bu cihazda ses tanıma yok."; return }
                if (!granted) { permission.launch(Manifest.permission.RECORD_AUDIO); return }
                if (holding.value) return
                delivered.set(false)
                restarts.value = 0
                transcript.value = ""
                partial.value = ""
                heard.value = ""
                message.value = "Dinleniyor…"
                holding.value = true
                begin()
            }

            fun release() {
                if (!holding.value) return
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
                    holding.value && soft -> ctrl.keepListening()
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

/** Sessizlik/eşleşmeme gibi, dinlemeyi sürdürmeyi engellemeyen hatalar. */
private val SOFT_ERRORS = setOf(
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    SpeechRecognizer.ERROR_CLIENT,
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY
)

/** Bir basışta en fazla kaç kez yeniden dinlemeye geçilir (sonsuz döngü emniyeti). */
private const val MAX_RESTARTS = 60

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
