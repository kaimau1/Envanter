package com.envanter.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.envanter.app.data.DraftStore
import com.envanter.app.gemini.MediaAnalyzer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Sağ alt köşedeki "basılı tut, konuş" mikrofon tuşu — uygulamanın en kısa yolu.
 *
 * Basar basmaz dinlemeye başlar (sistem diyaloğu açılmaz) ve parmak basılı olduğu
 * SÜRECE kesilmez: Gemini anahtarı varsa ses doğrudan kaydedilip bırakınca
 * Gemini'ye dinletilir, yani cihazın ses tanıyıcısının sessizlikte oturumu
 * kapatması diye bir sorun kalmaz. Analiz ve onay ekranı ancak parmağı çekince
 * başlar. Tek ürün de, tek seferde sayılan birden fazla ürün de desteklenir.
 */
@Composable
fun VoiceHoldFab(
    modifier: Modifier = Modifier,
    onProductsReady: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onReady by rememberUpdatedState(onProductsReady)
    var analyzing by remember { mutableStateOf(false) }

    var analyzeMessage by remember { mutableStateOf("") }

    // Analiz parmak çekildikten sonra, kaydın/metnin tamamıyla bir kez çalışır.
    val voice = rememberHoldToTalk { input ->
        scope.launch {
            analyzing = true
            analyzeMessage = "İşleniyor…"
            val result = try {
                withTimeout(120_000) {
                    MediaAnalyzer.fromVoice(context, input) { analyzeMessage = it }
                }
            } catch (e: TimeoutCancellationException) {
                Result.failure(IllegalStateException("Analiz zaman aşımına uğradı, tekrar dene."))
            } finally {
                analyzing = false
            }
            result
                .onSuccess { voiceResult ->
                    if (voiceResult.products.isEmpty()) {
                        analyzeMessage = if (voiceResult.transcript.isBlank()) {
                            "Konuşma anlaşılamadı, tekrar dene."
                        } else {
                            "\"${voiceResult.transcript}\" içinde ürün bulunamadı, tekrar dene."
                        }
                        return@onSuccess
                    }
                    analyzeMessage = ""
                    DraftStore.addParsed(voiceResult.products, "ses")
                    onReady()
                }
                .onFailure { analyzeMessage = it.message ?: "Ses çözümlenemedi, tekrar dene." }
        }
    }

    val diameter by animateDpAsState(if (voice.listening) 76.dp else 64.dp, label = "mic")
    // Onay ekranından dönüldüğünde eski metin baloncukta kalmasın.
    val bubble = when {
        voice.listening -> voice.heard.ifBlank { voice.message }
        analyzing -> analyzeMessage.ifBlank { "İşleniyor…" }
        analyzeMessage.isNotBlank() -> analyzeMessage
        voice.message.isNotBlank() -> voice.message
        else -> ""
    }

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
            color = if (voice.listening) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(diameter)
                // Parmak kaysa bile basış iptal edilmez; dinleme ancak parmak
                // kalkınca biter.
                .holdToTalkGesture(
                    enabled = !analyzing,
                    onPress = {
                        analyzeMessage = ""
                        voice.press()
                    },
                    onRelease = { voice.release() }
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.KeyboardVoice,
                    "sesle ekle — basılı tut",
                    modifier = Modifier.size(30.dp),
                    tint = if (voice.listening) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
