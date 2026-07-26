package com.envanter.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.io.File

/**
 * Uygulama içi video kaydı.
 *
 * Neden sistem kamerası değil: ACTION_VIDEO_CAPTURE çözünürlük seçtirmiyor (yalnızca
 * "düşük/yüksek" var, düşük çoğu cihazda 176x144'e inip etiketleri okunmaz yapıyor).
 * Telefon varsayılanı 1080p/4K olduğu için dosyalar gereksiz büyüyordu. CameraX ile
 * [Quality.HD] (720p) sabitleniyor: dosya birkaç kat küçülüyor, etiketler okunur kalıyor.
 *
 * Ses KASITLI olarak açık: kullanıcı kamerayla okunmayan bilgiyi (silik son kullanma
 * tarihi, poşetin içindeki ürün, miktar) sesli söyleyebiliyor ve Gemini bunu da
 * değerlendiriyor. Ses saniyede 32 token tutuyor ama karşılığını veriyor.
 */
@SuppressLint("MissingPermission")
@Composable
fun VideoRecorderOverlay(
    output: File,
    maxSeconds: Int,
    onFinished: (File?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val finish by rememberUpdatedState(onFinished)

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var cameraGranted by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var audioGranted by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    var askedPermissions by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var elapsed by remember { mutableStateOf(0) }
    var stopping by remember { mutableStateOf(false) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] ?: cameraGranted
        audioGranted = result[Manifest.permission.RECORD_AUDIO] ?: audioGranted
        if (!cameraGranted) error = "Kayıt için kamera izni gerekiyor."
    }

    LaunchedEffect(Unit) {
        if (askedPermissions) return@LaunchedEffect
        askedPermissions = true
        val missing = buildList {
            if (!cameraGranted) add(Manifest.permission.CAMERA)
            if (!audioGranted) add(Manifest.permission.RECORD_AUDIO)
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Kamerayı yalnızca izin geldikten sonra bağla.
    LaunchedEffect(cameraGranted) {
        if (!cameraGranted) return@LaunchedEffect
        runCatching {
            val cameraProvider = ProcessCameraProvider.getInstance(context).await()
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            // 720p sabit; cihaz HD desteklemiyorsa en yakın kaliteye düşer, kayıt kaybolmaz.
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HD,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
                    )
                )
                .build()
            val capture = VideoCapture.withOutput(recorder)
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture
            )
            provider = cameraProvider
            videoCapture = capture
        }.onFailure { error = "Kamera açılamadı: ${it.message}" }
    }

    // Ekran kapanınca kaydı bitir ve kamerayı serbest bırak.
    DisposableEffect(Unit) {
        onDispose {
            recording?.stop()
            provider?.unbindAll()
        }
    }

    fun stop() {
        if (stopping) return
        stopping = true
        recording?.stop() ?: finish(null)
    }

    fun start() {
        val capture = videoCapture ?: return
        runCatching { output.delete() }
        elapsed = 0
        val options = FileOutputOptions.Builder(output).build()
        recording = capture.output
            .prepareRecording(context, options)
            // Ses yalnızca izin verildiyse; izin yoksa kayıt sessiz devam etsin, iptal olmasın.
            .apply { if (audioGranted) withAudioEnabled() }
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    recording = null
                    val file = output.takeIf { it.length() > 0 }
                    if (event.hasError() && file == null) {
                        error = "Kayıt tamamlanamadı."
                        stopping = false
                    } else {
                        finish(file)
                    }
                }
            }
    }

    // Süre sayacı; üst sınıra gelince kayıt kendi kendine biter.
    LaunchedEffect(recording) {
        if (recording == null) return@LaunchedEffect
        while (recording != null && elapsed < maxSeconds) {
            delay(1000)
            elapsed += 1
        }
        if (elapsed >= maxSeconds) stop()
    }

    BackHandler { if (recording != null) stop() else finish(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        IconButton(
            onClick = { if (recording != null) stop() else finish(null) },
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        ) { Icon(Icons.Filled.Close, "Kapat", tint = Color.White) }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                if (recording != null) "● ${elapsed} / $maxSeconds sn" else "720p • en fazla $maxSeconds sn",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Ürünleri tek tek kameraya göster. Okunmayan tarihi veya miktarı " +
                    "yüksek sesle söyleyebilirsin — Gemini konuştuklarını da dikkate alır.",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall
            )
            if (!audioGranted) {
                Text(
                    "Mikrofon izni verilmedi: kayıt sessiz olacak, sesli anlatım kullanılamaz.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }

        Button(
            onClick = { if (recording != null) stop() else start() },
            enabled = cameraGranted && videoCapture != null && !stopping,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (recording != null) Color.White else Color(0xFFD32F2F)
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .size(84.dp)
        ) {
            Icon(
                if (recording != null) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                contentDescription = if (recording != null) "Kaydı bitir" else "Kaydı başlat",
                tint = if (recording != null) Color(0xFFD32F2F) else Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        if (recording == null && !stopping) {
            Text(
                "Kaydı bitirince ürünler otomatik analiz edilir",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
            )
        }
    }
}
