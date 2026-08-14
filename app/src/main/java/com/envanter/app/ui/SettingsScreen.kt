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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.navigation.NavHostController
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.envanter.app.data.FirebaseSync
import com.envanter.app.data.HomeStore
import com.envanter.app.data.Settings
import com.envanter.app.data.ThemeMode
import com.envanter.app.data.VideoQuality
import com.envanter.app.data.VoiceMode
import com.envanter.app.gemini.GeminiClient
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavHostController? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val homes by HomeStore.flow.collectAsState()
    val activeHomeId by HomeStore.activeId.collectAsState()

    val themeMode by Settings.themeMode(context).collectAsState(initial = ThemeMode.SYSTEM)
    val voiceMode by Settings.voiceMode(context).collectAsState(initial = VoiceMode.TAP)
    val videoQuality by Settings.videoQuality(context).collectAsState(initial = VideoQuality.BALANCED)
    val savedKey by Settings.geminiKey(context).collectAsState(initial = "")
    val savedModel by Settings.geminiModel(context).collectAsState(initial = "gemini-2.0-flash")
    val notifEnabled by Settings.notifEnabled(context).collectAsState(initial = true)
    val syncStatus by FirebaseSync.status.collectAsState()
    val userEmail by FirebaseSync.userEmail.collectAsState()
    val webClientId = remember { FirebaseSync.webClientId(context) }
    var googleError by remember { mutableStateOf("") }

    fun signInWithGoogle() {
        scope.launch {
            googleError = ""
            try {
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = CredentialManager.create(context).getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    FirebaseSync.signInWithGoogle(idToken)
                } else {
                    googleError = "Beklenmeyen kimlik bilgisi türü."
                }
            } catch (e: GetCredentialException) {
                googleError = "Giriş iptal edildi veya başarısız: ${e.message}"
            }
        }
    }

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
                Text("Evler", fontWeight = FontWeight.Bold)
                Text(
                    "Her evin kendi envanteri var. Şu an açık ev: " +
                        (homes.firstOrNull { it.id == activeHomeId } ?: HomeStore.DEFAULT).title +
                        " • toplam ${homes.size} ev.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { nav?.navigate("homes") }, enabled = nav != null) {
                    Text("Evleri Yönet")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tema", fontWeight = FontWeight.Bold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { scope.launch { Settings.setThemeMode(context, mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sesle Ekleme Modu", fontWeight = FontWeight.Bold)
                Text(
                    "Dokun: butona bas, konuş, sistem sessizlikte otomatik durur. " +
                        "Basılı Tut: parmağın buton üzerindeyken dinler, çekince durur. " +
                        "Gemini anahtarı girildiyse basılı tutarken ses doğrudan kaydedilip " +
                        "Gemini'ye dinletilir; böylece konuşma sessizlikte kesilmez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    VoiceMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = voiceMode == mode,
                            onClick = { scope.launch { Settings.setVoiceMode(context, mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index, VoiceMode.entries.size),
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Video Analiz Kalitesi", fontWeight = FontWeight.Bold)
                Text(
                    "Gemini token'ı videonun SÜRESİNDEN hesaplar, dosya boyutundan değil. " +
                        "Kayıt çözünürlüğünü düşürmek yalnızca yüklemeyi hızlandırır; tasarruf " +
                        "kısa video çekmekten ve daha seyrek kare örneklemekten gelir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    VideoQuality.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = videoQuality == mode,
                            onClick = { scope.launch { Settings.setVideoQuality(context, mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index, VideoQuality.entries.size),
                            label = { Text(mode.label) }
                        )
                    }
                }
                Text(
                    "${videoQuality.label}: saniyede ${videoQuality.fps} kare" +
                        (if (videoQuality.lowRes) ", düşük kare çözünürlüğü (etiket okuma zayıflar)" else "") +
                        " • ~${videoQuality.tokensPerSecond} token/sn " +
                        "(1 dakikalık video ≈ ${videoQuality.tokensFor(60)} token)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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

        if (FirebaseSync.isConfigured) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hesap", fontWeight = FontWeight.Bold)
                    when {
                        webClientId.isBlank() -> Text(
                            "Cihazlar arası aynı envanteri görmek için Firebase Console'da " +
                                "Authentication → Sign-in method → Google'ı etkinleştirip " +
                                "google-services.json'ı yeniden indirmen gerekiyor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        userEmail != null -> {
                            Text("Giriş yapıldı: $userEmail")
                            Button(onClick = { FirebaseSync.signOut(context) }) { Text("Çıkış Yap") }
                        }
                        else -> {
                            Text(
                                "Şu an bu cihaza özel anonim senkron kullanılıyor. Google ile giriş " +
                                    "yaparsan aynı hesapla girdiğin tüm cihazlar aynı envanteri paylaşır.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { signInWithGoogle() }) { Text("Google ile Giriş Yap") }
                        }
                    }
                    if (googleError.isNotBlank()) Text(googleError, color = MaterialTheme.colorScheme.error)
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
