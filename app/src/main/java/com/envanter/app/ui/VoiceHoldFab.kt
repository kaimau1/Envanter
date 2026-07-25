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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.envanter.app.data.DraftStore
import com.envanter.app.gemini.MediaAnalyzer

/**
 * Sağ alt köşedeki "basılı tut, konuş" mikrofon tuşu — uygulamanın en kısa yolu.
 *
 * Basar basmaz dinlemeye başlar (sistem diyaloğu açılmaz), parmağı çekince
 * kaydı kapatıp metni analiz eder. Tek cümlede bir ya da birden fazla ürün
 * olabilir; ikisi de desteklenir. Ekleme/onay ekranı ancak analiz bittikten
 * sonra açılır, böylece konuşurken araya ekran girmez.
 */
@Composable
fun VoiceHoldFab(
    modifier: Modifier = Modifier,
    onProductsReady: () -> Unit
) {
    val context = LocalContext.current
    val onReady by rememberUpdatedState(onProductsReady)

    var listening by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var toAnalyze by remember { mutableStateOf<String?>(null) }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        message = if (ok) "Hazır — basılı tutup konuş." else "Mikrofon izni verilmedi."
    }

    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizer = remember { if (available) SpeechRecognizer.createSpeechRecognizer(context) else null }

    // Tanıyıcının geri çağrıları yalnızca durum yazar; analiz aşağıdaki
    // LaunchedEffect'te yapılır, böylece eski closure'lara takılmaz.
    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { message = "Dinleniyor…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                // Kısa basışlarda sonuç gelmeden hata düşebiliyor; o ana kadar
                // duyulan kısmi metin varsa onu kullan, yoksa kullanıcıyı yönlendir.
                val partial = heard.trim()
                if (partial.isNotBlank() && error in RETRYABLE_ERRORS) {
                    toAnalyze = partial
                    return
                }
                message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Duyamadım — tuşu basılı tutup konuş."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Ses tanıma için internet gerekiyor."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni gerekiyor."
                    SpeechRecognizer.ERROR_CLIENT -> ""
                    else -> "Ses tanıma hatası ($error)"
                }
            }
            override fun onPartialResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?.let { heard = it }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: heard.trim().takeIf { it.isNotBlank() }
                if (spoken != null) { heard = spoken; toAnalyze = spoken }
                else message = "Duyamadım — tuşu basılı tutup konuş."
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { recognizer?.destroy() }
    }

    LaunchedEffect(toAnalyze) {
        val text = toAnalyze ?: return@LaunchedEffect
        toAnalyze = null
        analyzing = true
        message = "İşleniyor…"
        val products = MediaAnalyzer.fromSpeech(context, text) { message = it }
            .getOrElse { emptyList() }
        analyzing = false
        if (products.isEmpty()) {
            message = "\"$text\" içinde ürün bulunamadı."
            return@LaunchedEffect
        }
        DraftStore.addParsed(products, "ses")
        heard = ""
        message = ""
        onReady()
    }

    fun startListening() {
        if (!available) { message = "Bu cihazda ses tanıma yok."; return }
        if (!granted) { permission.launch(Manifest.permission.RECORD_AUDIO); return }
        if (analyzing) return
        heard = ""
        message = "Dinleniyor…"
        listening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        runCatching { recognizer?.startListening(intent) }
            .onFailure { listening = false; message = "Ses tanıma başlatılamadı." }
    }

    fun stopListening() {
        if (!listening) return
        runCatching { recognizer?.stopListening() }
    }

    val diameter by animateDpAsState(if (listening) 76.dp else 64.dp, label = "mic")
    val bubble = heard.ifBlank { message }

    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        if (bubble.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .widthIn(max = 280.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (analyzing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(bubble, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Surface(
            shape = CircleShape,
            color = if (listening) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(diameter)
                .pointerInput(available, granted, analyzing) {
                    detectTapGestures(
                        onPress = {
                            startListening()
                            tryAwaitRelease()
                            stopListening()
                        }
                    )
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.KeyboardVoice,
                    "sesle ekle — basılı tut",
                    modifier = Modifier.size(30.dp),
                    tint = if (listening) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/** Kısmi metin varsa analize devam edilebilecek hatalar. */
private val RETRYABLE_ERRORS = setOf(
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    SpeechRecognizer.ERROR_CLIENT
)
