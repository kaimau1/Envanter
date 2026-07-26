package com.envanter.app.gemini

import android.util.Base64
import com.envanter.app.data.CategoryDef
import com.envanter.app.data.CategoryStore
import com.envanter.app.data.ShelfLife
import com.envanter.app.util.ParsedProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Gemini'nin envanter incelemesi sonucu: güncel/yeni kategoriler + ürün atamaları. */
data class InventoryReview(
    val categories: List<CategoryDef>,
    val itemCategories: Map<String, String>,
    val shelfLife: List<ShelfLife> = emptyList(),
    /** Ürün id -> tahmini raf ömrü (gün). Tarihi boş ürünler için Gemini'nin doğrudan cevabı. */
    val itemDays: Map<String, Int> = emptyMap()
)

/**
 * Gemini REST istemcisi. Model listesi API'den otomatik çekilir,
 * seçilen modelle metin/görsel analizi ve toplu envanter incelemesi yapılır.
 */
object GeminiClient {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    private const val UPLOAD_BASE = "https://generativelanguage.googleapis.com/upload/v1beta"

    /** Tek istekte gönderilen en fazla fotoğraf; fazlası parçalara bölünür. */
    const val MAX_IMAGES_PER_REQUEST = 8

    /** Bu boyutun altındaki videolar doğrudan (inline) gönderilir, üstündekiler Files API ile yüklenir. */
    private const val INLINE_VIDEO_LIMIT = 14L * 1024 * 1024

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()
    private val JSON_MT = "application/json; charset=utf-8".toMediaType()

    /**
     * İstek gövdesine eklenecek görsel/video parçası: ya inline base64 ya da yüklenmiş dosya.
     * [fps] yalnızca videoda anlamlı: Gemini'nin saniyede kaç kare örnekleyeceğini belirler.
     */
    private data class MediaPart(
        val mime: String,
        val base64: String? = null,
        val fileUri: String? = null,
        val fps: Double? = null
    )

    /**
     * Video analizinin token maliyetini belirleyen ayarlar.
     * [estimatedTokens] yalnızca kullanıcıya gösterilen kaba tahmindir.
     */
    data class VideoTuning(
        val fps: Double = 1.0,
        val lowRes: Boolean = false,
        val estimatedTokens: Int = 0
    )

    /** Gemini'nin HTTP hatası; 400'de "ayarsız" isteğe geri düşebilmek için kodu taşır. */
    private class ApiException(val code: Int, message: String) : IllegalStateException(message)

    /** generateContent destekleyen modellerin adlarını döndürür. */
    suspend fun listModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE/models?pageSize=100&key=$apiKey")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error(apiError(body, resp.code))
                val arr = JSONObject(body).optJSONArray("models") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    val m = arr.getJSONObject(i)
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    val supports = methods != null && (0 until methods.length())
                        .any { methods.getString(it) == "generateContent" }
                    if (supports) m.getString("name").removePrefix("models/") else null
                }.sortedDescending()
            }
        }
    }

    suspend fun generate(apiKey: String, model: String, prompt: String, imageBase64: String? = null): Result<String> =
        generateWithMedia(
            apiKey, model, prompt,
            listOfNotNull(imageBase64?.let { MediaPart("image/jpeg", base64 = it) })
        )

    private suspend fun generateWithMedia(
        apiKey: String,
        model: String,
        prompt: String,
        media: List<MediaPart>,
        lowRes: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // fps / mediaResolution her modelde (ör. eski flash sürümlerinde) tanınmıyor.
            // Tanınmazsa API 400 döner; bu durumda isteği ayarsız haliyle bir kez tekrarlarız.
            val tunable = lowRes || media.any { it.fps != null }
            try {
                callGenerate(apiKey, model, prompt, media, lowRes, tuned = tunable)
            } catch (e: ApiException) {
                if (tunable && e.code == 400) callGenerate(apiKey, model, prompt, media, lowRes, tuned = false)
                else throw e
            }
        }
    }

    private fun callGenerate(
        apiKey: String,
        model: String,
        prompt: String,
        media: List<MediaPart>,
        lowRes: Boolean,
        tuned: Boolean
    ): String {
        val parts = JSONArray()
        parts.put(JSONObject().put("text", prompt))
        media.forEach { m ->
            val part = when {
                m.base64 != null -> JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", m.mime).put("data", m.base64)
                )
                m.fileUri != null -> JSONObject().put(
                    "file_data",
                    JSONObject().put("mime_type", m.mime).put("file_uri", m.fileUri)
                )
                else -> return@forEach
            }
            // Kare örnekleme sıklığı: 1.0 varsayılan, 0.5 -> yarısı kadar kare -> yarısı kadar token.
            if (tuned && m.fps != null) part.put("video_metadata", JSONObject().put("fps", m.fps))
            parts.put(part)
        }
        val payload = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", parts))
        )
        // Düşük çözünürlük: kare başına 258 yerine 66 token.
        if (tuned && lowRes) {
            payload.put("generationConfig", JSONObject().put("mediaResolution", "MEDIA_RESOLUTION_LOW"))
        }
        val req = Request.Builder()
            .url("$BASE/models/$model:generateContent?key=$apiKey")
            .post(payload.toString().toRequestBody(JSON_MT))
            .build()
        return http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, apiError(body, resp.code))
            val candidate = JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)
                ?: error("Model yanıt vermedi")
            val textParts = candidate.optJSONObject("content")?.optJSONArray("parts")
                ?: error("Model boş yanıt döndü")
            // Düşünme adımı olan modeller birden fazla parça döndürebiliyor: hepsini birleştir.
            (0 until textParts.length())
                .mapNotNull { textParts.optJSONObject(it)?.optString("text")?.takeIf { t -> t.isNotBlank() } }
                .joinToString("\n")
                .ifBlank { error("Model boş yanıt döndü") }
        }
    }

    /**
     * Büyük dosyaları (video) Files API ile yükler ve işlenmesini bekler.
     * Dönen değer generateContent'te `file_data.file_uri` olarak kullanılır.
     */
    private suspend fun uploadFile(
        apiKey: String,
        file: File,
        mime: String,
        onStatus: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        onStatus("Video yükleniyor (${humanSize(file.length())})…")
        val start = Request.Builder()
            .url("$UPLOAD_BASE/files?key=$apiKey")
            .addHeader("X-Goog-Upload-Protocol", "resumable")
            .addHeader("X-Goog-Upload-Command", "start")
            .addHeader("X-Goog-Upload-Header-Content-Length", file.length().toString())
            .addHeader("X-Goog-Upload-Header-Content-Type", mime)
            .post(
                JSONObject().put("file", JSONObject().put("display_name", file.name))
                    .toString().toRequestBody(JSON_MT)
            )
            .build()
        val uploadUrl = http.newCall(start).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error(apiError(body, resp.code))
            resp.header("X-Goog-Upload-URL") ?: error("Yükleme adresi alınamadı")
        }

        val upload = Request.Builder()
            .url(uploadUrl)
            .addHeader("X-Goog-Upload-Offset", "0")
            .addHeader("X-Goog-Upload-Command", "upload, finalize")
            .post(file.asRequestBody(mime.toMediaType()))
            .build()
        var info = http.newCall(upload).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error(apiError(body, resp.code))
            JSONObject(body).optJSONObject("file") ?: error("Yükleme yanıtı okunamadı")
        }

        var state = info.optString("state")
        val name = info.optString("name")
        var waited = 0
        while (state == "PROCESSING" && waited < 300) {
            onStatus("Video Gemini'de işleniyor… (${waited}s)")
            delay(3000)
            waited += 3
            val poll = Request.Builder().url("$BASE/$name?key=$apiKey").get().build()
            info = http.newCall(poll).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error(apiError(body, resp.code))
                JSONObject(body)
            }
            state = info.optString("state")
        }
        if (state == "FAILED") error("Video işlenemedi, farklı bir video dene.")
        if (state != "ACTIVE") error("Video zamanında işlenemedi, daha kısa bir video dene.")
        info.optString("uri").takeIf { it.isNotBlank() } ?: error("Video adresi alınamadı")
    }

    private fun humanSize(bytes: Long): String =
        if (bytes >= 1024L * 1024) String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        else "${bytes / 1024} KB"

    private fun apiError(body: String, code: Int): String =
        runCatching { JSONObject(body).getJSONObject("error").getString("message") }
            .getOrDefault("HTTP $code")

    private fun extractPrompt(): String {
        val cats = CategoryStore.all.joinToString(", ") { it.id }
        return "Bu bir gıda ürünü bilgisidir. Ürün adını, miktarını, birimini, son kullanma tarihini ve kategorisini çıkar. " +
            "SADECE şu JSON formatında yanıt ver, başka hiçbir şey yazma: " +
            """{"name":"...","quantity":1,"unit":"adet","expiry":"yyyy-MM-dd","category":"..."} """ +
            "Kategori şunlardan biri olmalı: $cats. " +
            "Bilinmeyen alanları null yap. Bugünün tarihi: "
    }

    /** Fotoğraftan ürün bilgisi çıkarma (Gemini vision). */
    suspend fun extractFromImage(apiKey: String, model: String, jpegBytes: ByteArray): Result<ParsedProduct> {
        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        return generate(apiKey, model, extractPrompt() + LocalDate.now() + "\nGörseldeki ürünü analiz et.", b64)
            .mapCatching { parseProductJson(it) }
    }

    /** Serbest metinden (ses kaydı dökümü) ürün bilgisi çıkarma. */
    suspend fun extractFromText(apiKey: String, model: String, text: String): Result<ParsedProduct> =
        generate(apiKey, model, extractPrompt() + LocalDate.now() + "\nMetin: \"$text\"")
            .mapCatching { parseProductJson(it) }

    // ---- Çoklu ürün çıkarımı (video / çoklu fotoğraf / çok ürünlü konuşma) ----

    /** Birden fazla ürün bekleyen ortak yönerge. [source] kaynağı tarif eder. */
    private fun multiPrompt(source: String): String {
        val cats = CategoryStore.all.joinToString(", ") { it.id }
        return buildString {
            append("Bir mutfak envanteri asistanısın. $source\n")
            append("İçerikte BİRDEN FAZLA gıda ürünü olabilir. HEPSİNİ eksiksiz listele, hiçbirini atlama.\n")
            append("Aynı ürün birden fazla karede/fotoğrafta görünüyorsa TEK kayıt yaz, tekrar etme; ")
            append("gerçekten birden çok adet varsa bunu \"quantity\" alanına yansıt.\n")
            append("Her ürün için: adı (paketteki marka + ürün adı okunuyorsa onu yaz), miktar, birim, ")
            append("son kullanma tarihi ve kategori.\n")
            append("Son kullanma tarihi okunamıyorsa \"expiry\" alanını null bırak ve \"days\" alanına ")
            append("o ürünün makul raf ömrünü GÜN olarak yaz (ör. taze ekmek 3, domates 7, konserve 365).\n")
            append("Kategori tam olarak şunlardan biri olmalı: $cats\n")
            append("Birim şunlardan biri olsun: adet, kg, g, L, ml, paket, kutu, şişe\n")
            append("Gıda olmayan nesneleri (poşet, masa, telefon, insan vb.) listeleme.\n")
            append("SADECE şu JSON'u döndür, başka hiçbir metin/açıklama yazma:\n")
            append("""{"products":[{"name":"...","quantity":1,"unit":"adet","expiry":"yyyy-MM-dd","category":"...","days":7}]}""")
            append("\nHiç ürün bulamazsan {\"products\":[]} döndür. Bugünün tarihi: ")
            append(LocalDate.now())
        }
    }

    /**
     * Birden fazla fotoğrafı tek istekte analiz eder; her fotoğrafta birden çok ürün olabilir.
     * Fotoğraf sayısı [MAX_IMAGES_PER_REQUEST]'i aşarsa çağıran taraf parçalara bölmelidir.
     */
    suspend fun extractManyFromImages(
        apiKey: String,
        model: String,
        images: List<ByteArray>
    ): Result<List<ParsedProduct>> {
        if (images.isEmpty()) return Result.success(emptyList())
        val parts = images.map { MediaPart("image/jpeg", base64 = Base64.encodeToString(it, Base64.NO_WRAP)) }
        val source = if (images.size == 1) "Sana bir ürün fotoğrafı veriyorum."
        else "Sana ${images.size} adet ürün fotoğrafı veriyorum (hepsi aynı alışverişten)."
        return generateWithMedia(apiKey, model, multiPrompt(source), parts)
            .mapCatching { parseProductsJson(it) }
    }

    /**
     * Videoyu analiz eder. 14 MB altındaki videolar doğrudan, büyükler Files API ile yüklenerek
     * gönderilir. [tuning] kare örnekleme sıklığını ve kare çözünürlüğünü — yani token
     * maliyetini — belirler. [onStatus] yükleme/işleme durumunu ekrana yansıtmak içindir.
     */
    suspend fun extractManyFromVideo(
        apiKey: String,
        model: String,
        file: File,
        mime: String,
        tuning: VideoTuning = VideoTuning(),
        onStatus: (String) -> Unit = {}
    ): Result<List<ParsedProduct>> = runCatching {
        val part = if (file.length() <= INLINE_VIDEO_LIMIT) {
            onStatus("Video hazırlanıyor (${humanSize(file.length())})…")
            val b64 = withContext(Dispatchers.IO) { Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) }
            MediaPart(mime, base64 = b64, fps = tuning.fps)
        } else {
            MediaPart(mime, fileUri = uploadFile(apiKey, file, mime, onStatus), fps = tuning.fps)
        }
        onStatus(
            if (tuning.estimatedTokens > 0) "Gemini videodaki ürünleri çıkarıyor… (~${tuning.estimatedTokens} token)"
            else "Gemini videodaki ürünleri çıkarıyor…"
        )
        // Videonun sesi de bilgi kaynağı: kullanıcı okunmayan tarihi/miktarı sesli söylüyor.
        val source = "Sana bir market alışverişi videosu veriyorum. " +
            "Video boyunca kameranın önünden geçen tüm ürünleri sırayla incele.\n" +
            "ÖNEMLİ: Videonun SESİNİ de dinle. Kullanıcı kameranın okuyamadığı bilgileri " +
            "(silik son kullanma tarihi, poşetin içindeki ürün, adet/ağırlık) sesli söylüyor olabilir. " +
            "Bir ürün hakkında söylenen söz ile görüntü çelişiyorsa SÖYLENENİ esas al. " +
            "Sesli söylenip görüntüde hiç görünmeyen ürünleri de listeye ekle."
        generateWithMedia(apiKey, model, multiPrompt(source), listOf(part), lowRes = tuning.lowRes)
            .mapCatching { parseProductsJson(it) }
            .getOrThrow()
    }

    /** Tek cümlede sayılan birden fazla ürünü ayrıştırır. */
    suspend fun extractManyFromText(
        apiKey: String,
        model: String,
        text: String
    ): Result<List<ParsedProduct>> {
        val source = "Kullanıcı aldığı ürünleri sesli olarak söyledi, konuşmanın metni aşağıda. " +
            "Konuşmada birden fazla ürün sayılmış olabilir; söylenen her ürünü ayrı kayıt yap. " +
            "Kullanıcı bir ürün için tarih söylediyse onu kullan, söylemediyse \"days\" tahmini ver.\n" +
            "Konuşma metni: \"$text\""
        return generate(apiKey, model, multiPrompt(source))
            .mapCatching { parseProductsJson(it) }
    }

    private fun parseProductsJson(raw: String): List<ParsedProduct> {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr: JSONArray = when {
            cleaned.indexOf('{').let { it >= 0 && (cleaned.indexOf('[') < 0 || it < cleaned.indexOf('[')) } -> {
                val start = cleaned.indexOf('{')
                val end = cleaned.lastIndexOf('}')
                require(start >= 0 && end > start) { "JSON bulunamadı" }
                JSONObject(cleaned.substring(start, end + 1)).optJSONArray("products") ?: JSONArray()
            }
            else -> {
                val start = cleaned.indexOf('[')
                val end = cleaned.lastIndexOf(']')
                require(start >= 0 && end > start) { "JSON bulunamadı" }
                JSONArray(cleaned.substring(start, end + 1))
            }
        }
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { productFromJson(it) }
        }.filter { !it.name.isNullOrBlank() }
    }

    /**
     * Tüm envanteri TEK istekte inceletir. Gemini:
     *  - her ürünü doğru kategoriye atar,
     *  - gerekirse YENİ kategori önerir (kendi kırmızı/sarı gün eşikleriyle),
     *  - mevcut kategorilerin eşiklerini düzenleyebilir.
     * items: (id, ad) çiftleri.
     */
    suspend fun reviewInventory(
        apiKey: String,
        model: String,
        currentCategories: List<CategoryDef>,
        items: List<Pair<String, String>>,
        currentShelfLife: List<ShelfLife> = emptyList(),
        datelessNames: List<String> = emptyList()
    ): Result<InventoryReview> {
        if (items.isEmpty()) return Result.success(InventoryReview(emptyList(), emptyMap()))
        val catLines = currentCategories.joinToString("\n") {
            "${it.id} | ${it.label} | kırmızı<=${it.redDays}gün | sarı<=${it.yellowDays}gün"
        }
        val itemLines = items.joinToString("\n") { "${it.first} | ${it.second}" }
        val shelfLines = currentShelfLife.joinToString("\n") { "${it.keyword} | ${it.days} gün" }
        val prompt = buildString {
            append("Bir mutfak envanteri asistanısın. Aşağıda mevcut kategoriler ve ürünler var.\n\n")
            append("MEVCUT KATEGORİLER (id | ad | kırmızı eşiği | sarı eşiği):\n$catLines\n\n")
            append("ÜRÜNLER (id | ad):\n$itemLines\n\n")
            append("MEVCUT ÜRÜN BAZLI RAF ÖMÜRLERİ (anahtar kelime | gün):\n")
            append(if (shelfLines.isBlank()) "(henüz yok)" else shelfLines)
            append("\n\n")
            append("TARİHİ BOŞ ÜRÜNLER (envanterde son kullanma tarihi girilmemiş olanlar):\n")
            append(if (datelessNames.isEmpty()) "(yok)" else datelessNames.joinToString("\n"))
            append("\n\n")
            append("Görevin:\n")
            append("1) Her ürünü en doğru kategoriye ata.\n")
            append("2) Ürünler mevcut kategorilere iyi oturmuyorsa YENİ kategori ekleyebilirsin; ")
            append("yeni kategoriye o gıda türüne uygun kırmızı ve sarı gün eşiği ver ")
            append("(ör. çabuk bozulan taze ürünler kısa, konserve/kuru gıda uzun).\n")
            append("3) Mevcut bir kategorinin gün eşiği ürün türüne göre yanlışsa düzeltebilirsin.\n")
            append("4) TARİHİ BOŞ ÜRÜNLER listesindeki HER ürün için items kaydına \"days\" ekle — ")
            append("hiçbirini atlama. Ürün adından makul bir raf ömrü (gün) tahmin et; ")
            append("paketinde tarih yazan ürünlerde bile açıldıktan sonrasına göre tahmin ver ")
            append("(ör. açılmış peynir ~10 gün, açılmış salça ~20 gün). ")
            append("Tarihi zaten olan ürünlere \"days\" YAZMA.\n")
            append("5) Aynı tahminleri ileride tekrar kullanabilmek için shelfLife'a da ekle; ")
            append("keyword olarak ürünün markasız, sade adını kullan ")
            append("(ör. \"Sütaş beyaz peynir 500g\" -> \"beyaz peynir\"). ")
            append("Mevcut bir raf ömrü yanlışsa düzelt.\n\n")
            append("SADECE şu JSON'u döndür, başka metin yazma:\n")
            append("""{"categories":[{"id":"KISA_BUYUK_HARF_ID","label":"Ad","emoji":"🍎","redDays":3,"yellowDays":7,"keywords":["kelime1","kelime2"]}],""")
            append(""""items":[{"id":"URUN_ID","category":"KATEGORI_ID","days":10}],""")
            append(""""shelfLife":[{"keyword":"domates","days":7}]}""")
            append("\nNot: categories ve shelfLife listelerine sadece YENİ veya DEĞİŞTİRDİĞİN kayıtları koy. ")
            append("id'ler büyük harf ve alt çizgili olsun (ör. BEBEK_MAMASI). Bugünün tarihi: ")
            append(LocalDate.now())
        }
        return generate(apiKey, model, prompt).mapCatching { raw ->
            val cleaned = raw.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            require(start >= 0 && end > start) { "JSON bulunamadı" }
            val root = JSONObject(cleaned.substring(start, end + 1))

            val existingById = currentCategories.associateBy { it.id }
            val maxOrder = (currentCategories.maxOfOrNull { it.sortOrder } ?: 0)
            val cats = mutableListOf<CategoryDef>()
            root.optJSONArray("categories")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id").takeIf { it.isNotBlank() }
                        ?.uppercase()?.replace(Regex("[^A-Z0-9]+"), "_")?.trim('_') ?: continue
                    if (id == CategoryStore.DEFAULT_ID) continue
                    val existing = existingById[id]
                    val red = o.optInt("redDays", existing?.redDays ?: 7).coerceIn(0, 3650)
                    var yellow = o.optInt("yellowDays", existing?.yellowDays ?: 30).coerceIn(0, 3650)
                    if (yellow < red) yellow = red
                    val kwFromJson = o.optJSONArray("keywords")?.let { k ->
                        (0 until k.length()).joinToString(",") { k.optString(it).trim() }
                    }.orEmpty()
                    val keywords = when {
                        existing != null && kwFromJson.isBlank() -> existing.keywords
                        existing != null -> (existing.keywordList + kwFromJson.split(",")).map { it.trim() }
                            .filter { it.isNotBlank() }.distinct().joinToString(",")
                        else -> kwFromJson
                    }
                    cats += CategoryDef(
                        id = id,
                        label = o.optString("label").takeIf { it.isNotBlank() } ?: existing?.label ?: id,
                        emoji = o.optString("emoji").takeIf { it.isNotBlank() } ?: existing?.emoji ?: "📦",
                        redDays = red,
                        yellowDays = yellow,
                        keywords = keywords,
                        sortOrder = existing?.sortOrder ?: (maxOrder + 1 + i)
                    )
                }
            }

            val map = mutableMapOf<String, String>()
            val days = mutableMapOf<String, Int>()
            root.optJSONArray("items")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                    o.optInt("days", -1).takeIf { it > 0 }?.let { days[id] = it.coerceIn(1, 3650) }
                    val catRaw = o.optString("category").takeIf { it.isNotBlank() } ?: continue
                    val catId = catRaw.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')
                    map[id] = catId
                }
            }

            val shelfLife = mutableListOf<ShelfLife>()
            root.optJSONArray("shelfLife")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val keyword = o.optString("keyword").trim().lowercase()
                    if (keyword.isBlank()) continue
                    val days = o.optInt("days", -1)
                    if (days <= 0) continue
                    shelfLife += ShelfLife(keyword, days.coerceIn(1, 3650))
                }
            }
            InventoryReview(cats, map, shelfLife, days)
        }
    }

    private fun parseProductJson(raw: String): ParsedProduct {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        require(start >= 0 && end > start) { "JSON bulunamadı" }
        return productFromJson(JSONObject(cleaned.substring(start, end + 1)))
    }

    private fun productFromJson(o: JSONObject): ParsedProduct {
        fun str(k: String): String? = if (o.isNull(k)) null else o.optString(k).takeIf { it.isNotBlank() && it != "null" }
        val name = str("name")
        // Gemini kategorisi eşleşmezse ada göre yerel tahmine düşülür.
        val geminiCat = CategoryStore.fromGemini(str("category"))
        val category = when {
            geminiCat != null && geminiCat.id != CategoryStore.DEFAULT_ID -> geminiCat
            name != null -> CategoryStore.guess(name).takeIf { it.id != CategoryStore.DEFAULT_ID } ?: geminiCat
            else -> geminiCat
        }
        return ParsedProduct(
            name = name,
            quantity = if (o.isNull("quantity")) null else o.optDouble("quantity").takeIf { !it.isNaN() && it > 0 },
            unit = str("unit"),
            expiry = str("expiry")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            category = category,
            days = o.optInt("days", -1).takeIf { it > 0 }?.coerceIn(1, 3650)
        )
    }
}
