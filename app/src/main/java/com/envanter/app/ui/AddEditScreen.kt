package com.envanter.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.envanter.app.data.Category
import com.envanter.app.data.FoodItem
import com.envanter.app.data.Repository
import com.envanter.app.data.Settings
import com.envanter.app.data.UNITS
import com.envanter.app.gemini.GeminiClient
import com.envanter.app.util.ParsedProduct
import com.envanter.app.util.TextParse
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun photoUri(context: Context): Pair<Uri, File> {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File(dir, "capture.jpg")
    return FileProvider.getUriForFile(context, "com.envanter.app.fileprovider", file) to file
}

private fun File.toJpegBytes(maxDim: Int = 1280): ByteArray {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts)
    var sample = 1
    while (maxOf(opts.outWidth, opts.outHeight) / sample > maxDim) sample *= 2
    val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
    bmp.recycle()
    return out.toByteArray()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(nav: NavHostController, itemId: String?, mode: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Category.DIGER.name) }
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

    val isEdit = itemId != null

    LaunchedEffect(itemId) {
        if (itemId != null && !loaded) {
            Repository.get(itemId)?.let {
                name = it.name; category = it.category
                qtyText = fmtQty(it.quantity); unit = it.unit
                expiry = it.expiryDate; note = it.note
                userTouchedCategory = true
            }
            loaded = true
        }
    }

    fun applyParsed(p: ParsedProduct) {
        p.name?.let { if (name.isBlank()) name = it }
        p.quantity?.let { qtyText = fmtQty(it) }
        p.unit?.let { unit = it }
        p.expiry?.let { expiry = it.toString() }
        // Kategori: önce ayrıştırıcının verdiği (DIGER değilse), yoksa isimden yerel tahmin.
        val resolved = p.category?.takeIf { it != Category.DIGER }
            ?: p.name?.let { Category.guess(it) }?.takeIf { it != Category.DIGER }
        if (resolved != null) {
            category = resolved.name
            userTouchedCategory = true
        }
        status = if (p.name == null && p.expiry == null)
            "Bilgi çıkarılamadı, elle doldurabilirsin."
        else "Tarandı ✓ Kontrol edip kaydet."
    }

    suspend fun analyzePhoto(file: File) {
        busy = true
        status = "Fotoğraf analiz ediliyor…"
        val key = Settings.geminiKey(context).first()
        val parsed: ParsedProduct? = if (key.isNotBlank()) {
            val model = Settings.geminiModel(context).first()
            GeminiClient.extractFromImage(key, model, file.toJpegBytes()).getOrNull()
        } else null
        val result = parsed ?: runCatching {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image).await().text
            TextParse.parseOcr(text)
        }.getOrElse { ParsedProduct() }
        applyParsed(result)
        busy = false
    }

    suspend fun analyzeSpeech(text: String) {
        busy = true
        status = "\"$text\" işleniyor…"
        val key = Settings.geminiKey(context).first()
        val parsed = if (key.isNotBlank()) {
            val model = Settings.geminiModel(context).first()
            GeminiClient.extractFromText(key, model, text).getOrNull()
        } else null
        applyParsed(parsed ?: TextParse.parseSpeech(text))
        busy = false
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

    // Ad değiştikçe kategori tahmini (kullanıcı elle seçmediyse)
    LaunchedEffect(name) {
        if (!userTouchedCategory && name.length >= 3) {
            category = Category.guess(name).name
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
                OutlinedButton(onClick = { launchSpeech() }, modifier = Modifier.weight(1f), enabled = !busy) {
                    Icon(Icons.Filled.KeyboardVoice, null)
                    Text(" Sesle Söyle")
                }
            }
            if (busy) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text(status)
                }
            } else if (status.isNotBlank()) {
                Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Ürün adı") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Box {
                OutlinedTextField(
                    value = Category.fromName(category).let { "${it.emoji} ${it.label}" },
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
                    Category.entries.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text("${cat.emoji} ${cat.label}") },
                            onClick = {
                                category = cat.name
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
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Row {
                        if (expiry.isNotBlank()) {
                            TextButton(onClick = { expiry = "" }) { Text("Temizle") }
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
                        if (!userTouchedCategory && category == Category.DIGER.name)
                            Category.guess(finalName).name
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
