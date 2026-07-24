package com.envanter.app.data

import com.envanter.app.util.ParsedProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * Videodan / çoklu fotoğraftan / çok ürünlü konuşmadan çıkarılan, henüz envantere
 * kaydedilmemiş ürün taslağı. Kullanıcı listeyi gözden geçirip düzeltebilsin diye
 * kaydetmeden önce burada bekletilir.
 */
data class Draft(
    val key: Long,
    val name: String,
    val category: String = CategoryStore.DEFAULT_ID,
    val quantity: Double = 1.0,
    val unit: String = "adet",
    /** ISO-8601 (yyyy-MM-dd); boş ise tarih yok. */
    val expiryDate: String = "",
    /** >0 ise tarih tahmin edildi (FoodItem.expiryAutoDays ile aynı anlam). */
    val expiryAutoDays: Int = 0,
    /** Hangi kaynaktan geldi: "video", "fotoğraf", "ses"… */
    val source: String = "",
    val selected: Boolean = true
) {
    fun toItem(): FoodItem = FoodItem(
        name = name.trim().ifBlank { "İsimsiz ürün" },
        category = category,
        quantity = quantity,
        unit = unit,
        expiryDate = expiryDate,
        expiryAutoDays = expiryAutoDays
    )
}

/**
 * Toplu ekleme ekranının bellek-içi taslak listesi. Ekran gezinmeleri arasında
 * korunur; kullanıcı "Ekle" ya da "Temizle" deyince boşalır.
 */
object DraftStore {
    private val seq = AtomicLong(0)
    private val _flow = MutableStateFlow<List<Draft>>(emptyList())
    val flow: StateFlow<List<Draft>> = _flow

    val all: List<Draft> get() = _flow.value

    /**
     * Ayrıştırılmış ürünleri taslağa çevirip listeye ekler. Aynı ada+birime sahip
     * taslaklar birleşir (video/çoklu fotoğrafta aynı ürünün tekrar görünmesi normaldir).
     * Eklenen yeni taslak sayısını döndürür.
     */
    fun addParsed(products: List<ParsedProduct>, source: String): Int {
        val incoming = products.mapNotNull { toDraft(it, source) }
        if (incoming.isEmpty()) return 0
        val merged = _flow.value.toMutableList()
        var added = 0
        for (d in incoming) {
            val idx = merged.indexOfFirst {
                CategoryStore.normalize(it.name) == CategoryStore.normalize(d.name) && it.unit == d.unit
            }
            if (idx >= 0) {
                val old = merged[idx]
                merged[idx] = old.copy(
                    quantity = old.quantity + d.quantity,
                    // Tarihi olan kayıt, tahminî tarihli kaydı ezer.
                    expiryDate = if (old.expiryAutoDays > 0 && d.expiryAutoDays == 0 && d.expiryDate.isNotBlank())
                        d.expiryDate else old.expiryDate,
                    expiryAutoDays = if (old.expiryAutoDays > 0 && d.expiryAutoDays == 0 && d.expiryDate.isNotBlank())
                        0 else old.expiryAutoDays
                )
            } else {
                merged += d
                added++
            }
        }
        _flow.value = merged
        return added
    }

    private fun toDraft(p: ParsedProduct, source: String): Draft? {
        val name = p.name?.trim().orEmpty()
        if (name.isBlank()) return null
        val category = p.category?.takeIf { it.id != CategoryStore.DEFAULT_ID }?.id
            ?: CategoryStore.guess(name).id
        // Tarih önceliği: okunan tarih > Gemini'nin gün tahmini > yerel raf ömrü tablosu.
        val days = p.days ?: 0
        val (date, autoDays) = when {
            p.expiry != null -> p.expiry.toString() to 0
            days > 0 -> LocalDate.now().plusDays(days.toLong()).toString() to days
            else -> ShelfLifeStore.guess(name)
                ?.let { LocalDate.now().plusDays(it.toLong()).toString() to it }
                ?: ("" to 0)
        }
        return Draft(
            key = seq.incrementAndGet(),
            name = name,
            category = category,
            quantity = p.quantity?.takeIf { it > 0 } ?: 1.0,
            unit = normalizeUnit(p.unit),
            expiryDate = date,
            expiryAutoDays = autoDays,
            source = source
        )
    }

    /** Gemini "litre", "gram", "Adet" gibi serbest birimler dönebiliyor; listeye oturt. */
    fun normalizeUnit(raw: String?): String {
        val u = raw?.trim()?.lowercase(java.util.Locale("tr", "TR")).orEmpty()
        if (u.isBlank()) return "adet"
        UNITS.firstOrNull { it.lowercase(java.util.Locale("tr", "TR")) == u }?.let { return it }
        return when (u) {
            "tane", "ad", "piece", "pcs" -> "adet"
            "kilo", "kilogram" -> "kg"
            "gram", "gr" -> "g"
            "litre", "lt", "l" -> "L"
            "mililitre", "cc" -> "ml"
            "poşet", "poset", "torba" -> "paket"
            "teneke", "konserve" -> "kutu"
            "sise" -> "şişe"
            else -> "adet"
        }
    }

    fun addBlank(): Long {
        val key = seq.incrementAndGet()
        _flow.value = _flow.value + Draft(key = key, name = "", source = "elle")
        return key
    }

    fun update(key: Long, transform: (Draft) -> Draft) {
        _flow.value = _flow.value.map { if (it.key == key) transform(it) else it }
    }

    fun remove(key: Long) {
        _flow.value = _flow.value.filterNot { it.key == key }
    }

    fun setAllSelected(selected: Boolean) {
        _flow.value = _flow.value.map { it.copy(selected = selected) }
    }

    fun clear() {
        _flow.value = emptyList()
    }

    /** Seçili taslakları envantere yazar ve listeyi boşaltır; kaydedilen sayıyı döndürür. */
    fun commitSelected(): Int {
        val selected = _flow.value.filter { it.selected && it.name.isNotBlank() }
        selected.forEach { Repository.save(it.toItem()) }
        _flow.value = emptyList()
        return selected.size
    }
}
