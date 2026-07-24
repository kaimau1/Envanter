package com.envanter.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.envanter.app.data.FirebaseSync
import com.envanter.app.data.Settings
import com.envanter.app.gemini.GeminiClient
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedKey by Settings.geminiKey(context).collectAsState(initial = "")
    val savedModel by Settings.geminiModel(context).collectAsState(initial = "gemini-2.0-flash")
    val notifEnabled by Settings.notifEnabled(context).collectAsState(initial = true)
    val syncStatus by FirebaseSync.status.collectAsState()

    var keyInput by remember { mutableStateOf("") }
    var keyLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(savedKey) {
        if (!keyLoaded && savedKey.isNotBlank()) { keyInput = savedKey; keyLoaded = true }
    }

    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelsBusy by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf("") }
    var modelMenuOpen by remember { mutableStateOf(false) }

    fun fetchModels() {
        val key = keyInput.trim()
        if (key.isBlank()) { modelsError = "Önce API anahtarı gir."; return }
        scope.launch {
            modelsBusy = true; modelsError = ""
            GeminiClient.listModels(key)
                .onSuccess { models = it; if (it.isEmpty()) modelsError = "Model bulunamadı." }
                .onFailure { modelsError = "Hata: ${it.message}" }
            modelsBusy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("⚙️ Ayarlar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gemini API", fontWeight = FontWeight.Bold)
                Text(
                    "Ücretsiz anahtar: aistudio.google.com/apikey",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("API Anahtarı") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch { Settings.setGeminiKey(context, keyInput.trim()) }
                        fetchModels()
                    }) { Text("Kaydet ve Modelleri Getir") }
                    if (modelsBusy) CircularProgressIndicator(Modifier.padding(4.dp))
                }
                if (modelsError.isNotBlank()) Text(modelsError, color = MaterialTheme.colorScheme.error)

                Box {
                    OutlinedTextField(
                        value = savedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model") },
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { fetchModels() }) { Icon(Icons.Filled.Refresh, "modelleri yenile") }
                                Icon(Icons.Filled.ArrowDropDown, null, Modifier.align(Alignment.CenterVertically))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable {
                                if (models.isEmpty()) fetchModels()
                                modelMenuOpen = true
                            }
                    )
                    DropdownMenu(expanded = modelMenuOpen && models.isNotEmpty(), onDismissRequest = { modelMenuOpen = false }) {
                        models.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    scope.launch { Settings.setGeminiModel(context, m) }
                                    modelMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Firebase Senkron", fontWeight = FontWeight.Bold)
                Text(syncStatus, style = MaterialTheme.typography.bodyMedium)
                if (!FirebaseSync.isConfigured) {
                    Text(
                        "Cihazlar arası senkron için Firebase projesi oluşturup google-services.json " +
                            "dosyasını uygulamaya ekleyerek yeniden derlemen gerekir (README'de anlatıldı). " +
                            "Senkron olmadan da uygulama tamamen çalışır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Bildirimler", fontWeight = FontWeight.Bold)
                    Text(
                        "Tarihi kritik ürünler için günde 2 kez kontrol",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notifEnabled,
                    onCheckedChange = { v -> scope.launch { Settings.setNotifEnabled(context, v) } }
                )
            }
        }

        Text(
            "Envanter v1.0 • Son kullanma tarihlerini takip et, israfı önle 🌱",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
