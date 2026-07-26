package com.envanter.app.gemini

import android.content.Context
import android.net.Uri
import com.envanter.app.data.Settings
import com.envanter.app.util.Media
import com.envanter.app.util.ParsedProduct
import com.envanter.app.util.TextParse
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Fotoğraf / video / konuşma girdilerinden BİRDEN FAZLA ürün çıkarır.
 * Gemini anahtarı varsa Gemini kullanılır; yoksa cihaz-içi OCR ve metin
 * ayrıştırıcısıyla elden geldiğince sonuç üretilir.
 */
object MediaAnalyzer {

    /** Gemini anahtarı yoksa video analizi mümkün değildir; ekranlar buna göre uyarır. */
    suspend fun hasGemini(context: Context): Boolean = Settings.geminiKey(context).first().isNotBlank()

    /**
     * Bir veya birden çok fotoğrafı analiz eder. Fotoğraflar
     * [GeminiClient.MAX_IMAGES_PER_REQUEST]'lik gruplara bölünüp sırayla gönderilir.
     */
    suspend fun fromImages(
        context: Context,
        uris: List<Uri>,
        onStatus: (String) -> Unit = {}
    ): Result<List<ParsedProduct>> = runCatching {
        val images = uris.mapIndexedNotNull { i, uri ->
            onStatus("Fotoğraflar hazırlanıyor… (${i + 1}/${uris.size})")
            Media.jpegBytes(context, uri)
        }
        if (images.isEmpty()) error("Fotoğraf okunamadı.")
        analyzeImageBytes(context, images, uris, onStatus).getOrThrow()
    }

    /** Kamerayla çekilen tek dosya için. */
    suspend fun fromImageFile(
        context: Context,
        file: File,
        onStatus: (String) -> Unit = {}
    ): Result<List<ParsedProduct>> = runCatching {
        val bytes = Media.jpegBytes(file) ?: error("Fotoğraf okunamadı.")
        analyzeImageBytes(context, listOf(bytes), listOf(Uri.fromFile(file)), onStatus).getOrThrow()
    }

    private suspend fun analyzeImageBytes(
        context: Context,
        images: List<ByteArray>,
        uris: List<Uri>,
        onStatus: (String) -> Unit
    ): Result<List<ParsedProduct>> = runCatching {
        val key = Settings.geminiKey(context).first()
        if (key.isBlank()) {
            // Gemini yoksa: her fotoğrafı ayrı ayrı cihaz-içi OCR ile oku (fotoğraf başına 1 ürün).
            onStatus("Cihaz-içi metin tanıma çalışıyor…")
            return@runCatching uris.mapNotNull { uri -> ocr(context, uri) }
        }
        val model = Settings.geminiModel(context).first()
        val chunks = images.chunked(GeminiClient.MAX_IMAGES_PER_REQUEST)
        val all = mutableListOf<ParsedProduct>()
        chunks.forEachIndexed { i, chunk ->
            onStatus(
                if (chunks.size == 1) "Gemini fotoğraf${if (chunk.size > 1) "ları" else "ı"} inceliyor…"
                else "Gemini inceliyor… (grup ${i + 1}/${chunks.size})"
            )
            all += GeminiClient.extractManyFromImages(key, model, chunk).getOrThrow()
        }
        all
    }

    /** Videodan çoklu ürün çıkarımı — yalnızca Gemini ile mümkün. */
    suspend fun fromVideo(
        context: Context,
        file: File,
        mime: String,
        onStatus: (String) -> Unit = {}
    ): Result<List<ParsedProduct>> = runCatching {
        val key = Settings.geminiKey(context).first()
        if (key.isBlank()) error("Video analizi için Ayarlar'dan Gemini API anahtarı girmelisin.")
        if (file.length() <= 0) error("Video okunamadı.")
        val model = Settings.geminiModel(context).first()
        // Token maliyeti videonun süresine bağlı; kullanıcının seçtiği kaliteye göre
        // kare örnekleme sıklığını düşürüp tahmini tüketimi de ekranda gösteriyoruz.
        val quality = Settings.videoQuality(context).first()
        val seconds = Media.videoSeconds(file)
        onStatus("Video: ${Media.humanSize(file.length())} • ${seconds} sn (${quality.label} mod)")
        val tuning = GeminiClient.VideoTuning(
            fps = quality.fps,
            lowRes = quality.lowRes,
            estimatedTokens = quality.tokensFor(seconds)
        )
        GeminiClient.extractManyFromVideo(key, model, file, mime, tuning, onStatus).getOrThrow()
    }

    /** Konuşma metninden çoklu ürün çıkarımı; anahtar yoksa cihaz-içi ayrıştırıcı devreye girer. */
    suspend fun fromSpeech(
        context: Context,
        text: String,
        onStatus: (String) -> Unit = {}
    ): Result<List<ParsedProduct>> = runCatching {
        val key = Settings.geminiKey(context).first()
        if (key.isBlank()) {
            onStatus("Konuşma ayrıştırılıyor…")
            return@runCatching TextParse.parseSpeechMany(text)
        }
        onStatus("Gemini konuşmayı ayrıştırıyor…")
        val model = Settings.geminiModel(context).first()
        GeminiClient.extractManyFromText(key, model, text)
            .getOrElse { TextParse.parseSpeechMany(text) }
            .ifEmpty { TextParse.parseSpeechMany(text) }
    }

    private suspend fun ocr(context: Context, uri: Uri): ParsedProduct? = runCatching {
        val image = InputImage.fromFilePath(context, uri)
        val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await().text
        TextParse.parseOcr(text).takeIf { !it.name.isNullOrBlank() }
    }.getOrNull()
}
