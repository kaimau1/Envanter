package com.envanter.app.gemini

import android.util.Base64
import com.envanter.app.data.Category
import com.envanter.app.util.ParsedProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Gemini REST istemcisi. Model listesi API'den otomatik çekilir,
 * seçilen modelle metin ve görsel analizi yapılır.
 */
object GeminiClient {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val JSON_MT = "application/json; charset=utf-8".toMediaType()

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
        withContext(Dispatchers.IO) {
            runCatching {
                val parts = JSONArray()
                parts.put(JSONObject().put("text", prompt))
                if (imageBase64 != null) {
                    parts.put(
                        JSONObject().put(
                            "inline_data",
                            JSONObject().put("mime_type", "image/jpeg").put("data", imageBase64)
                        )
                    )
                }
                val payload = JSONObject().put(
                    "contents",
                    JSONArray().put(JSONObject().put("parts", parts))
                )
                val req = Request.Builder()
                    .url("$BASE/models/$model:generateContent?key=$apiKey")
                    .post(payload.toString().toRequestBody(JSON_MT))
                    .build()
                http.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error(apiError(body, resp.code))
                    JSONObject(body)
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text")
                }
            }
        }

    private fun apiError(body: String, code: Int): String =
        runCatching { JSONObject(body).getJSONObject("error").getString("message") }
            .getOrDefault("HTTP $code")

    private const val EXTRACT_PROMPT =
        "Bu bir gıda ürünü bilgisidir. Ürün adını, miktarını, birimini, son kullanma tarihini ve kategorisini çıkar. " +
            "SADECE şu JSON formatında yanıt ver, başka hiçbir şey yazma: " +
            """{"name":"...","quantity":1,"unit":"adet","expiry":"yyyy-MM-dd","category":"..."} """ +
            "Kategori şunlardan biri olmalı: SUT_URUNLERI, ET_TAVUK_BALIK, MEYVE_SEBZE, EKMEK_UNLU, DONDURULMUS, KONSERVE, BAKLIYAT_KURU, ATISTIRMALIK, ICECEK, KAHVALTILIK, SOS_BAHARAT, DIGER. " +
            "Bilinmeyen alanları null yap. Bugünün tarihi: "

    /** Fotoğraftan ürün bilgisi çıkarma (Gemini vision). */
    suspend fun extractFromImage(apiKey: String, model: String, jpegBytes: ByteArray): Result<ParsedProduct> {
        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        return generate(apiKey, model, EXTRACT_PROMPT + LocalDate.now() + "\nGörseldeki ürünü analiz et.", b64)
            .mapCatching { parseProductJson(it) }
    }

    /** Serbest metinden (ses kaydı dökümü) ürün bilgisi çıkarma. */
    suspend fun extractFromText(apiKey: String, model: String, text: String): Result<ParsedProduct> =
        generate(apiKey, model, EXTRACT_PROMPT + LocalDate.now() + "\nMetin: \"$text\"")
            .mapCatching { parseProductJson(it) }

    private val VALID_CATS = Category.entries.filter { it != Category.DIGER }
        .joinToString(", ") { it.name }

    /**
     * Tüm envanteri Gemini'ye gönderip her ürün için en doğru kategoriyi ister.
     * items: (id, ad) çiftleri. Dönen: id -> Category (yalnız güvenli eşleşmeler).
     */
    suspend fun fixCategories(
        apiKey: String,
        model: String,
        items: List<Pair<String, String>>
    ): Result<Map<String, Category>> {
        if (items.isEmpty()) return Result.success(emptyMap())
        val list = items.joinToString("\n") { "${it.first} | ${it.second}" }
        val prompt = "Aşağıda bir mutfak envanterindeki ürünler var (id | ad). " +
            "Her ürün için en uygun gıda kategorisini seç. " +
            "SADECE şu JSON dizisini döndür, başka metin yazma: " +
            """[{"id":"...","category":"KATEGORI"}] """ +
            "KATEGORI şunlardan biri olmalı: $VALID_CATS, DIGER.\n\nÜrünler:\n$list"
        return generate(apiKey, model, prompt).mapCatching { raw ->
            val cleaned = raw.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val start = cleaned.indexOf('[')
            val end = cleaned.lastIndexOf(']')
            require(start >= 0 && end > start) { "JSON dizisi bulunamadı" }
            val arr = JSONArray(cleaned.substring(start, end + 1))
            val map = mutableMapOf<String, Category>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                val cat = Category.fromGemini(o.optString("category")) ?: continue
                map[id] = cat
            }
            map
        }
    }

    private fun parseProductJson(raw: String): ParsedProduct {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        require(start >= 0 && end > start) { "JSON bulunamadı" }
        val o = JSONObject(cleaned.substring(start, end + 1))
        fun str(k: String): String? = if (o.isNull(k)) null else o.optString(k).takeIf { it.isNotBlank() && it != "null" }
        val name = str("name")
        // Gemini'nin döndürdüğü kategori enum/eş anlamlıya eşlenir; tutmazsa ada göre
        // yerel tahmine düşülür. Böylece beklenmedik bir kategori metni DIGER'e sıkışmaz.
        val geminiCat = Category.fromGemini(str("category"))
        val category = when {
            geminiCat != null && geminiCat != Category.DIGER -> geminiCat
            name != null -> Category.guess(name).takeIf { it != Category.DIGER } ?: geminiCat
            else -> geminiCat
        }
        return ParsedProduct(
            name = name,
            quantity = if (o.isNull("quantity")) null else o.optDouble("quantity").takeIf { !it.isNaN() && it > 0 },
            unit = str("unit"),
            expiry = str("expiry")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            category = category
        )
    }
}
