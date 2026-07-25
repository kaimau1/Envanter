package com.envanter.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.envanter.app.data.CategoryStore
import com.envanter.app.data.Draft
import com.envanter.app.data.DraftStore
import com.envanter.app.data.UNITS
import com.envanter.app.gemini.MediaAnalyzer
import com.envanter.app.util.Media
import com.envanter.app.util.ParsedProduct
import com.envanter.app.util.TextParse
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun cacheUri(context: Context, dir: String, name: String): Pair<Uri, File> {
    val folder = File(context.cacheDir, dir).apply { mkdirs() }
    val file = File(folder, name)
    return FileProvider.getUriForFile(context, "com.envanter.app.fileprovider", file) to file
}

/**
 * Toplu ekleme: bir videoda ya da bir/birden çok fotoğrafta görünen tüm ürünleri,
 * veya tek seferde söylenen birden fazla ürünü Gemini'ye çıkarttırır; kullanıcı
 * listeyi düzeltip hepsini tek dokunuşla envantere ekler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchAddScreen(nav: NavHostController, mode: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drafts by DraftStore.flow.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var datePickerFor by remember { mutableStateOf<Long?>(null) }
    // Video ve çoklu ürün çıkarımı Gemini gerektirir; anahtar yoksa kullanıcı bilsin.
    var geminiReady by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { geminiReady = MediaAnalyzer.hasGemini(context) }

    fun report(count: Int, source: String) {
        status = when {
            count > 0 -> "$count ürün bulundu ✓ Kontrol edip ekle."
            DraftStore.all.isEmpty() -> "$source içinde ürün bulunamadı. Elle ekleyebilirsin."
            else -> "Yeni ürün bulunamadı (aynı ürünler zaten listede)."
        }
    }

    suspend fun runAnalysis(source: String, block: suspend ((String) -> Unit) -> Result<List<ParsedProduct>>) {
        busy = true
        status = "Hazırlanıyor…"
        block { s -> status = s }
            .onSuccess { report(DraftStore.addParsed(it, source), source) }
            .onFailure { status = it.message ?: "Analiz başarısız oldu." }
        busy = false
    }

    // --- Kaynak seçiciler ---
    val photoTarget = remember { cacheUri(context, "photos", "batch_capture.jpg") }
    val videoTarget = remember { cacheUri(context, "videos", "batch_capture.mp4") }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch {
            runAnalysis("fotoğraf") { onStatus ->
                MediaAnalyzer.fromImageFile(context, photoTarget.second, onStatus)
            }
        }
    }
    val captureVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        // Bazı kamera uygulamaları kayıt başarılı olsa da false döndürüyor; dosyaya bak.
        val recorded = videoTarget.second.length() > 0
        if (ok || recorded) scope.launch {
            if (!recorded) {
                status = "Video kaydedilemedi."
            } else runAnalysis("video") { onStatus ->
                MediaAnalyzer.fromVideo(context, videoTarget.second, "video/mp4", onStatus)
            }
        }
    }
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            runAnalysis("fotoğraflar") { onStatus -> MediaAnalyzer.fromImages(context, uris, onStatus) }
        }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            status = "Video kopyalanıyor…"
            val mime = Media.mimeOf(context, uri)
            val copied = Media.copyToCache(context, uri, "videos", "picked_video")
            busy = false
            if (copied == null) status = "Video okunamadı."
            else runAnalysis("video") { onStatus -> MediaAnalyzer.fromVideo(context, copied, mime, onStatus) }
        }
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) scope.launch {
            runAnalysis("konuşma") { onStatus -> MediaAnalyzer.fromSpeech(context, spoken, onStatus) }
        }
    }

    // Kayıt öncesi eski dosyayı sil: kullanıcı vazgeçerse bir önceki video analiz edilmesin.
    fun startVideoCapture() {
        runCatching { videoTarget.second.delete() }
        captureVideo.launch(videoTarget.first)
    }

    fun startPhotoCapture() {
        runCatching { photoTarget.second.delete() }
        takePhoto.launch(photoTarget.first)
    }

    fun launchSpeech() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Örn: 2 litre süt, 3 adet yumurta ve bir paket ekmek")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 12000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        }
        runCatching { speech.launch(i) }
            .onFailure { status = "Ses tanıma bu cihazda kullanılamıyor." }
    }

    var autoLaunched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(mode) {
        if (autoLaunched) return@LaunchedEffect
        autoLaunched = true
        when (mode) {
            "video" -> startVideoCapture()
            "videoGallery" -> pickVideo.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
            "gallery" -> pickPhotos.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            "photo" -> startPhotoCapture()
            "voice" -> launchSpeech()
        }
    }

    val selectedCount = drafts.count { it.selected && it.name.isNotBlank() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Toplu Ekle") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "geri") }
                },
                actions = {
                    if (drafts.isNotEmpty()) {
                        TextButton(onClick = { DraftStore.clear(); status = "" }) { Text("Temizle") }
                    }
                }
            )
        },
        bottomBar = {
            if (drafts.isNotEmpty()) {
                Button(
                    onClick = {
                        val n = DraftStore.commitSelected()
                        status = ""
                        if (n > 0) nav.popBackStack()
                    },
                    enabled = selectedCount > 0 && !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("$selectedCount ürünü envantere ekle")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Dört kaynak tek satırda: basılı tutmak galeriden seçmeye yarar.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        SourceTile(
                            Modifier.weight(1f), Icons.Filled.Videocam, "Video",
                            onClick = { startVideoCapture() },
                            onLongClick = {
                                pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            },
                            enabled = !busy
                        )
                        SourceTile(
                            Modifier.weight(1f), Icons.Filled.PhotoCamera, "Fotoğraf",
                            onClick = { startPhotoCapture() },
                            onLongClick = {
                                pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = !busy
                        )
                        SourceTile(
                            Modifier.weight(1f), Icons.Filled.KeyboardVoice, "Sesle",
                            onClick = { launchSpeech() },
                            enabled = !busy
                        )
                        SourceTile(
                            Modifier.weight(1f), Icons.Filled.Add, "Satır",
                            onClick = { DraftStore.addBlank() },
                            enabled = !busy
                        )
                    }
                    Text(
                        "Bir videoda, fotoğrafta veya cümlede kaç ürün varsa hepsi birden eklenir • " +
                            "galeriden seçmek için 📷 veya 🎥 tuşunu basılı tut",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (!geminiReady) {
                        Text(
                            "⚠️ Video ve çoklu ürün analizi için Ayarlar → Gemini API anahtarı gerekir. " +
                                "Anahtar olmadan fotoğraf başına tek ürün okunur.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            if (busy || status.isNotBlank()) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            if (busy) "  $status" else status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (drafts.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Bulunan ürünler (${drafts.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { DraftStore.setAllSelected(selectedCount != drafts.size) }) {
                            Text(if (selectedCount == drafts.size) "Hiçbirini seçme" else "Hepsini seç", fontSize = 12.sp)
                        }
                    }
                }
            }
            items(drafts, key = { it.key }) { draft ->
                DraftCard(
                    draft = draft,
                    onPickDate = { datePickerFor = draft.key }
                )
            }
            item { Box(Modifier.padding(bottom = 24.dp)) {} }
        }
    }

    datePickerFor?.let { key ->
        val draft = drafts.firstOrNull { it.key == key }
        val initial = runCatching { LocalDate.parse(draft?.expiryDate) }.getOrNull() ?: LocalDate.now().plusDays(7)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { datePickerFor = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        DraftStore.update(key) { it.copy(expiryDate = date, expiryAutoDays = 0) }
                    }
                    datePickerFor = null
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { datePickerFor = null }) { Text("İptal") } }
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftCard(draft: Draft, onPickDate: () -> Unit) {
    val categories by CategoryStore.flow.collectAsState()
    var catExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = draft.selected,
                    onCheckedChange = { v -> DraftStore.update(draft.key) { it.copy(selected = v) } }
                )
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { v -> DraftStore.update(draft.key) { it.copy(name = v) } },
                    label = { Text("Ürün adı") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { DraftStore.remove(draft.key) }) {
                    Icon(Icons.Filled.Close, "kaldır")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Metni yerel tut: "1," yazarken her tuşta biçimlendirilirse ondalık girilemiyor.
                var qtyText by remember(draft.key) { mutableStateOf(fmtQty(draft.quantity)) }
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { v ->
                        qtyText = v.filter { c -> c.isDigit() || c == '.' || c == ',' }
                        val q = qtyText.replace(',', '.').toDoubleOrNull() ?: 0.0
                        DraftStore.update(draft.key) { it.copy(quantity = q) }
                    },
                    label = { Text("Miktar") },
                    singleLine = true,
                    modifier = Modifier.weight(0.9f)
                )
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = draft.unit,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Birim") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.matchParentSize().clickable { unitExpanded = true })
                    DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                        UNITS.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u) },
                                onClick = {
                                    DraftStore.update(draft.key) { it.copy(unit = u) }
                                    unitExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = CategoryStore.byId(draft.category).let { "${it.emoji} ${it.label}" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.matchParentSize().clickable { catExpanded = true })
                    DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text("${cat.emoji} ${cat.label}") },
                                onClick = {
                                    DraftStore.update(draft.key) { it.copy(category = cat.id) }
                                    catExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = onPickDate,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 14.dp)
                ) {
                    val label = runCatching { TextParse.formatDate(LocalDate.parse(draft.expiryDate)) }
                        .getOrDefault("Tarih seç")
                    Text(
                        if (draft.expiryAutoDays > 0) "$label ~" else label,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
            if (draft.expiryAutoDays > 0) {
                Text(
                    "~${draft.expiryAutoDays} günlük tahmin — elle seçersen sabitlenir",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
