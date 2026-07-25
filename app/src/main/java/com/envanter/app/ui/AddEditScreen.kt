package com.envanter.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.envanter.app.data.CategoryStore
import com.envanter.app.data.DraftStore
import com.envanter.app.data.FoodItem
import com.envanter.app.data.Repository
import com.envanter.app.data.Settings
import com.envanter.app.data.ShelfLifeStore
import com.envanter.app.data.UNITS
import com.envanter.app.data.VoiceMode
import com.envanter.app.gemini.MediaAnalyzer
import com.envanter.app.util.ParsedProduct
import com.envanter.app.util.TextParse
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun photoUri(context: Context): Pair<Uri, File> {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File(dir, "capture.jpg")
    return FileProvider.getUriForFile(context, "com.envanter.app.fileprovider", file) to file
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(nav: NavHostController, itemId: String?, mode: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val categories by CategoryStore.flow.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(CategoryStore.DEFAULT_ID) }
    var qtyText by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf("adet") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var catExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }
    var userTouchedCategory by rememberSaveable { mutableStateOf(false) }
    var userTouchedExpiry by rememberSaveable { mutableStateOf(false) }
    // 0 = tarihi kullanıcı verdi (elle/sesle/fotoğrafla), >0 = otomatik tahmin (kaç günlük)
    var expiryAutoDays by rememberSaveable { mutableStateOf(0) }
    var suggestions by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    var suggestionsDismissed by remember { mutableStateOf(false) }

    val isEdit = itemId != null

    LaunchedEffect(itemId) {
        if (itemId != null && !loaded) {
            Repository.get(itemId)?.let {
                name = it.name; category = it.category
                qtyText = fmtQty(it.quantity); unit = it.unit
                expiry = it.expiryDate; note = it.note
                expiryAutoDays = it.expiryAutoDays
                userTouchedCategory = true
                userTouchedExpiry = true
            }
            loaded = true
        }
    }

    fun applyParsed(p: ParsedProduct) {
        p.name?.let { if (name.isBlank()) name = it }
        p.quantity?.let { qtyText = fmtQty(it) }
        p.unit?.let { unit = it }
        // Sesten/fotoğraftan gelen tarih kullanıcının verisidir: otomatik sayılmaz,
        // sonradan raf ömrü tahminiyle de üzerine yazılmaz.
        p.expiry?.let {
            expiry = it.toString()
            expiryAutoDays = 0
            userTouchedExpiry = true
        }
        // Etiketinde tarih yoksa Gemini'nin raf ömrü tahminini kullan (tahmini işaretlenir).
        if (p.expiry == null && (p.days ?: 0) > 0 && !userTouchedExpiry) {
            expiry = LocalDate.now().plusDays(p.days!!.toLong()).toString()
            expiryAutoDays = p.days
        }
        // Kategori: önce ayrıştırıcının verdiği (DIGER değilse), yoksa isimden yerel tahmin.
        val resolved = p.category?.takeIf { it.id != CategoryStore.DEFAULT_ID }
            ?: p.name?.let { CategoryStore.guess(it) }?.takeIf { it.id != CategoryStore.DEFAULT_ID }
        if (resolved != null) {
            category = resolved.id
            userTouchedCategory = true
        }
        status = if (p.name == null && p.expiry == null)
            "Bilgi çıkarılamadı, elle doldurabilirsin."
        else "Tarandı ✓ Kontrol edip kaydet."
    }

    // Tek fotoğrafta/cümlede birden çok ürün çıkarsa hepsini toplu ekleme ekranına devret.
    fun goBatch(products: List<ParsedProduct>, source: String) {
        DraftStore.addParsed(products, source)
        nav.popBackStack()
        nav.navigate("batch")
    }

    suspend fun analyzePhoto(file: File) {
        busy = true
        status = "Fotoğraf analiz ediliyor…"
        val products = MediaAnalyzer.fromImageFile(context, file) { status = it }
            .getOrElse { emptyList() }
        busy = false
        when {
            products.size > 1 -> goBatch(products, "fotoğraf")
            products.size == 1 -> applyParsed(products.first())
            else -> applyParsed(ParsedProduct())
        }
    }

    suspend fun analyzeSpeech(text: String) {
        busy = true
        status = "\"$text\" işleniyor…"
        val products = MediaAnalyzer.fromSpeech(context, text) { status = it }
            .getOrElse { emptyList() }
        busy = false
        when {
            products.size > 1 -> goBatch(products, "ses")
            products.size == 1 -> applyParsed(products.first())
            else -> applyParsed(ParsedProduct())
        }
    }

    val photoFile = remember { photoUri(context) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { analyzePhoto(photoFile.second) }
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) scope.launch { analyzeSpeech(spoken) }
    }

    fun launchCamera() = cameraLauncher.launch(photoFile.first)
    fun launchSpeech() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Örn: 3 adet süt son kullanma 12 ağustos")
            // Konuşma arasında erken kapanmayı önlemek için sessizlik toleransını uzat.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 8000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        }
        runCatching { speechLauncher.launch(i) }
            .onFailure { status = "Ses tanıma bu cihazda kullanılamıyor." }
    }

    // Basılı-tut modu: sistem diyaloğu yerine SpeechRecognizer'ı doğrudan sür,
    // parmak butondayken dinler, çekilince durur.
    val voiceMode by Settings.voiceMode(context).collectAsState(initial = VoiceMode.TAP)
    val holdInteraction = remember { MutableInteractionSource() }
    val isHoldPressed by holdInteraction.collectIsPressedAsState()
    var recordAudioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val recognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status = "Dinleniyor…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_CLIENT) {
                        status = "Ses tanıma hatası ($error)"
                    }
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) scope.launch { analyzeSpeech(spoken) }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }
    val recordAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        recordAudioGranted = granted
        if (!granted) status = "Basılı tut modu için mikrofon izni gerekiyor."
    }

    fun startHoldListening() {
        if (!recordAudioGranted) { recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO); return }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
        }
        runCatching { recognizer.startListening(i) }
    }
    fun stopHoldListening() {
        runCatching { recognizer.stopListening() }
    }
    LaunchedEffect(isHoldPressed) {
        if (voiceMode == VoiceMode.HOLD) {
            if (isHoldPressed) startHoldListening() else stopHoldListening()
        }
    }
    DisposableEffect(Unit) { onDispose { recognizer.destroy() } }

    var autoLaunched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(mode) {
        if (!autoLaunched) {
            autoLaunched = true
            when (mode) {
                "photo" -> launchCamera()
                "voice" -> launchSpeech()
            }
        }
    }

    // Ad yazıldıkça daha önce eklenen ürünlerden öneri getir (düzenlemede gerekmez).
    LaunchedEffect(name) {
        suggestions = if (isEdit) emptyList()
        else Repository.suggestNames(name).filter { !it.name.equals(name, ignoreCase = true) }
    }

    // Ad değiştikçe kategori tahmini (kullanıcı elle seçmediyse)
    LaunchedEffect(name) {
        if (!userTouchedCategory && name.length >= 3) {
            category = CategoryStore.guess(name).id
        }
    }

    // Etiketinde tarih olmayan ürünler (taze meyve/sebze vb.) için tahmini raf ömrü öner.
    LaunchedEffect(name) {
        if (!userTouchedExpiry && expiry.isBlank() && name.length >= 3) {
            ShelfLifeStore.guess(name)?.let { days ->
                expiry = LocalDate.now().plusDays(days.toLong()).toString()
                expiryAutoDays = days
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Ürünü Düzenle" else "Ürün Ekle") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "geri") }
                },
                actions = {
                    if (isEdit) {
                        IconButton(onClick = {
                            scope.launch {
                                Repository.get(itemId!!)?.let { Repository.delete(it) }
                                nav.popBackStack()
                            }
                        }) { Icon(Icons.Filled.Delete, "sil") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { launchCamera() }, modifier = Modifier.weight(1f), enabled = !busy) {
                    Icon(Icons.Filled.PhotoCamera, null)
                    Text(" Fotoğrafla Tara")
                }
                OutlinedButton(
                    onClick = { if (voiceMode == VoiceMode.TAP) launchSpeech() },
                    interactionSource = holdInteraction,
                    modifier = Modifier.weight(1f),
                    enabled = !busy
                ) {
                    Icon(Icons.Filled.KeyboardVoice, null)
                    Text(if (voiceMode == VoiceMode.HOLD) " Basılı Tut, Konuş" else " Sesle Söyle")
                }
            }
            if (!isEdit) {
                Text(
                    "İpucu: fotoğrafta veya söylediğin cümlede birden fazla ürün varsa " +
                        "hepsi otomatik ayrıştırılıp kontrol listesine düşer.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (busy) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text(status)
                }
            } else if (status.isNotBlank()) {
                Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            Box {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; suggestionsDismissed = false },
                    label = { Text("Ürün adı") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                // Daha önce eklenen ürünler (fotoğraftan gelen markalı adlar dahil).
                DropdownMenu(
                    expanded = suggestions.isNotEmpty() && !suggestionsDismissed,
                    onDismissRequest = { suggestionsDismissed = true },
                    properties = PopupProperties(focusable = false)
                ) {
                    suggestions.forEach { s ->
                        DropdownMenuItem(
                            text = { Text("${CategoryStore.byId(s.category).emoji} ${s.name}") },
                            onClick = {
                                name = s.name
                                category = s.category
                                unit = s.unit
                                userTouchedCategory = true
                                suggestionsDismissed = true
                            }
                        )
                    }
                }
            }

            Box {
                OutlinedTextField(
                    value = CategoryStore.byId(category).let { "${it.emoji} ${it.label}" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Kategori") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable { catExpanded = true }
                )
                DropdownMenu(
                    expanded = catExpanded,
                    onDismissRequest = { catExpanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text("${cat.emoji} ${cat.label}") },
                            onClick = {
                                category = cat.id
                                userTouchedCategory = true
                                catExpanded = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Miktar") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Birim") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable { unitExpanded = true }
                    )
                    DropdownMenu(
                        expanded = unitExpanded,
                        onDismissRequest = { unitExpanded = false }
                    ) {
                        UNITS.forEach { u ->
                            DropdownMenuItem(text = { Text(u) }, onClick = { unit = u; unitExpanded = false })
                        }
                    }
                }
            }

            OutlinedTextField(
                value = expiry.let { e ->
                    runCatching { TextParse.formatDate(LocalDate.parse(e)) }.getOrDefault("")
                },
                onValueChange = {},
                readOnly = true,
                label = { Text("Son kullanma tarihi") },
                placeholder = { Text("Seçmek için dokun") },
                supportingText = if (expiryAutoDays > 0) {
                    { Text("~$expiryAutoDays günlük tahmin — elle seçersen sabitlenir") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Row {
                        if (expiry.isNotBlank()) {
                            TextButton(onClick = {
                                expiry = ""; expiryAutoDays = 0; userTouchedExpiry = true
                            }) { Text("Temizle") }
                        }
                        TextButton(onClick = { showDatePicker = true }) { Text("Seç") }
                    }
                }
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Not (isteğe bağlı)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val qty = qtyText.replace(',', '.').toDoubleOrNull() ?: 1.0
                    val finalName = name.trim().ifBlank { "İsimsiz ürün" }
                    // Güvenlik ağı: kullanıcı kategoriyi elle seçmediyse ve mevcut kategori
                    // DIGER ise, isimden son bir tahmin dene.
                    val finalCategory =
                        if (!userTouchedCategory && category == CategoryStore.DEFAULT_ID)
                            CategoryStore.guess(finalName).id
                        else category
                    scope.launch {
                        val base = if (isEdit) Repository.get(itemId!!) else null
                        Repository.save(
                            (base ?: FoodItem()).copy(
                                name = finalName,
                                category = finalCategory,
                                quantity = qty,
                                unit = unit,
                                expiryDate = expiry,
                                expiryAutoDays = expiryAutoDays,
                                note = note.trim()
                            )
                        )
                        nav.popBackStack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                enabled = name.isNotBlank() && !busy
            ) {
                Text(if (isEdit) "Kaydet" else "Envantere Ekle")
            }
        }
    }

    if (showDatePicker) {
        val initial = runCatching { LocalDate.parse(expiry) }.getOrNull() ?: LocalDate.now().plusDays(7)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        expiry = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        expiryAutoDays = 0 // elle seçilen tarih otomatik güncellenmez
                        userTouchedExpiry = true
                    }
                    showDatePicker = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("İptal") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
