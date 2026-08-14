package com.envanter.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.envanter.app.data.HomeStore
import com.envanter.app.data.Repository
import com.envanter.app.data.Settings
import com.envanter.app.gemini.GeminiClient
import com.envanter.app.util.TextParse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Gemini asistanı: envanter özetiyle birlikte serbest soru sorulabilir.
 * Hazır kısayollar: ne pişirsem, önce ne tüketmeliyim, alışveriş önerisi.
 */
@Composable
fun AssistantScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiKey by Settings.geminiKey(context).collectAsState(initial = "")
    val homes by HomeStore.flow.collectAsState()
    val activeHomeId by HomeStore.activeId.collectAsState()
    val activeHome = homes.firstOrNull { it.id == activeHomeId } ?: HomeStore.DEFAULT
    val model by Settings.geminiModel(context).collectAsState(initial = "gemini-2.0-flash")

    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun ask(q: String) {
        if (apiKey.isBlank()) {
            error = "Önce Ayarlar'dan Gemini API anahtarını ekle."
            return
        }
        scope.launch {
            busy = true; error = ""; answer = ""
            val items = Repository.items()
            val inventory = if (items.isEmpty()) "(envanter boş)"
            else items.joinToString("\n") { i ->
                val date = i.effectiveExpiry?.let { TextParse.formatDate(it) } ?: "tarih yok"
                // Açılmış paketler ayrıca belirtilir: "önce ne tüketeyim" cevabı buna göre değişir.
                val opened = if (i.opened) {
                    " | AÇILMIŞ (${i.openedDate})" +
                        if (i.openedDays > 0) ", açıldıktan sonra ${i.openedDays} gün" else ""
                } else ""
                "- ${i.name} | ${fmtQty(i.quantity)} ${i.unit} | ${i.categoryEnum.label} | SKT: $date$opened"
            }
            val prompt = "Sen bir mutfak envanteri asistanısın. Türkçe, kısa ve pratik yanıt ver.\n" +
                "Bugünün tarihi: ${java.time.LocalDate.now()}\n" +
                "Envanter şu evin: ${HomeStore.active.title}\n" +
                "Not: AÇILMIŞ işaretli ürünlerde paketin üzerindeki tarih değil, açıldıktan " +
                "sonraki süre geçerlidir; bunları öncelikli tüketilecekler arasında say.\n" +
                "\nEnvanterim:\n$inventory\n\nSoru: $q"
            GeminiClient.generate(apiKey, model, prompt)
                .onSuccess { answer = it }
                .onFailure { error = "Hata: ${it.message}" }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("✨ Gemini Asistan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "${activeHome.title} envanterine göre yanıtlar",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (apiKey.isBlank()) {
            Card {
                Text(
                    "Asistanı kullanmak için Ayarlar sekmesinden ücretsiz Gemini API anahtarını ekle.\n" +
                        "(aistudio.google.com/apikey adresinden alabilirsin)",
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            AssistChip(onClick = { ask("Envanterimdeki malzemelerle ne pişirebilirim? 2-3 tarif öner.") },
                label = { Text("🍳 Ne pişirsem?") })
            AssistChip(onClick = { ask("Hangi ürünleri önce tüketmeliyim? Öncelik sırası ver.") },
                label = { Text("⏰ Önce ne tüketeyim?") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            AssistChip(onClick = { ask("Envanterime bakarak eksik temel gıdalar için kısa bir alışveriş listesi öner.") },
                label = { Text("🛒 Alışveriş önerisi") })
            AssistChip(onClick = { ask("Envanterimi israf açısından analiz et, kısa öneriler ver.") },
                label = { Text("📊 Analiz") })
        }

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Kendi sorunu yaz") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { if (question.isNotBlank()) ask(question) },
            enabled = !busy && question.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Sor") }

        if (busy) {
            Row { CircularProgressIndicator(Modifier.padding(end = 8.dp)); Text("Düşünüyor…") }
        }
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        if (answer.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                MarkdownText(answer, modifier = Modifier.padding(14.dp))
            }
        }
    }
}
