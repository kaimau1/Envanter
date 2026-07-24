package com.envanter.app.gemini

import android.util.Base64
import com.envanter.app.data.CategoryDef
import com.envanter.app.data.CategoryStore
import com.envanter.app.data.ShelfLife
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

/** Gemini'nin envanter incelemesi sonucu: güncel/yeni kategoriler + ürün atamaları. */
data class InventoryReview(
    val categories: List<CategoryDef>,
    val itemCategories: Map<String, String>,
    val shelfLife: List<ShelfLife> = emptyList()
)

/**
 * Gemini REST istemcisi. Model listesi API'den otomatik çekilir,
 * seçilen modelle metin/görsel analizi ve toplu envanter incelemesi yapılır.
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
        currentShelfLife: List<ShelfLife> = emptyList()
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
            append("MEVCUT ÜRÜN BAZLI RAF ÖMÜRLERİ (anahtar kelime | gün, üstünde tarih olmayan taze/paketsiz ürünler için):\n")
            append(if (shelfLines.isBlank()) "(henüz yok)" else shelfLines)
            append("\n\n")
            append("Görevin:\n")
            append("1) Her ürünü en doğru kategoriye ata.\n")
            append("2) Ürünler mevcut kategorilere iyi oturmuyorsa YENİ kategori ekleyebilirsin; ")
            append("yeni kategoriye o gıda türüne uygun kırmızı ve sarı gün eşiği ver ")
            append("(ör. çabuk bozulan taze ürünler kısa, konserve/kuru gıda uzun).\n")
            append("3) Mevcut bir kategorinin gün eşiği ürün türüne göre yanlışsa düzeltebilirsin.\n")
            append("4) Envanterdeki ürün isimlerinden, üstünde son kullanma tarihi genelde OLMAYAN taze/paketsiz ")
            append("ürünler (taze meyve, sebze, ekmek vb.) için uygun bir raf ömrü (gün) öner; ")
            append("mevcut bir eşleşme yanlışsa düzelt.\n\n")
            append("SADECE şu JSON'u döndür, başka metin yazma:\n")
            append("""{"categories":[{"id":"KISA_BUYUK_HARF_ID","label":"Ad","emoji":"🍎","redDays":3,"yellowDays":7,"keywords":["kelime1","kelime2"]}],""")
            append(""""items":[{"id":"URUN_ID","category":"KATEGORI_ID"}],""")
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
            root.optJSONArray("items")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
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
            InventoryReview(cats, map, shelfLife)
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
            category = category
        )
    }
}
