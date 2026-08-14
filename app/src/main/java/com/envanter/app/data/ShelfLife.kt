package com.envanter.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * Ürün adından tahmini raf ömrü (gün). Etiketinde son kullanma tarihi olmayan
 * taze/paketsiz ürünler (domates, patlıcan gibi) eklenirken tarih otomatik önerilir.
 * Gemini "kategorileri düzelt" isteğiyle bu listeyi de düzenleyebilir.
 *
 * [openedDays] paketli ürünler içindir: kapalıyken aylarca duran salça/konserve
 * açıldıktan sonra günler içinde bozulur. 0 ise bu ürün için bilinmiyor.
 */
@Entity(tableName = "shelf_life")
data class ShelfLife(
    @PrimaryKey val keyword: String,
    val days: Int,
    val openedDays: Int = 0
)

object ShelfLifeStore {
    // ponytail: kaba tahminler, Gemini "kategorileri düzelt" ile ürün bazlı düzeltebilir.
    val DEFAULTS: List<ShelfLife> = listOf(
        ShelfLife("domates", 7), ShelfLife("salatalık", 7), ShelfLife("hıyar", 7),
        ShelfLife("biber", 7), ShelfLife("patlıcan", 5), ShelfLife("kabak", 5),
        ShelfLife("brokoli", 5), ShelfLife("karnabahar", 5), ShelfLife("marul", 5),
        ShelfLife("roka", 4), ShelfLife("ıspanak", 4), ShelfLife("maydanoz", 5),
        ShelfLife("dereotu", 5), ShelfLife("havuç", 21), ShelfLife("patates", 30),
        ShelfLife("soğan", 30), ShelfLife("sarımsak", 30), ShelfLife("kereviz", 14),
        ShelfLife("pırasa", 10), ShelfLife("lahana", 14), ShelfLife("turp", 14),
        ShelfLife("mantar", 5), ShelfLife("muz", 5), ShelfLife("elma", 21),
        ShelfLife("armut", 10), ShelfLife("portakal", 14), ShelfLife("mandalina", 10),
        ShelfLife("limon", 21), ShelfLife("üzüm", 7), ShelfLife("çilek", 3),
        ShelfLife("karpuz", 7), ShelfLife("kavun", 7), ShelfLife("kayısı", 5),
        ShelfLife("şeftali", 5), ShelfLife("erik", 5), ShelfLife("kiraz", 5),
        ShelfLife("vişne", 5), ShelfLife("nar", 21), ShelfLife("incir", 3),
        ShelfLife("avokado", 5), ShelfLife("kivi", 14), ShelfLife("ananas", 5),
        // Etiketinde tarih olsa bile açıldıktan sonra hızlı biten temel ürünler.
        ShelfLife("peynir", 10, 10), ShelfLife("kaşar", 14, 14), ShelfLife("ekmek", 3, 2),
        ShelfLife("yumurta", 21), ShelfLife("zeytin", 21, 30),
        // Paketi AÇILDIKTAN sonra geçerli olan süreler (kapalı hâlleri etiketinde yazar).
        ShelfLife("süt", 5, 3), ShelfLife("yoğurt", 10, 5), ShelfLife("ayran", 7, 2),
        ShelfLife("krema", 10, 3), ShelfLife("tereyağı", 60, 21), ShelfLife("labne", 14, 7),
        ShelfLife("salça", 365, 20), ShelfLife("konserve", 365, 3),
        ShelfLife("ton balığı", 365, 2), ShelfLife("turşu", 365, 30),
        ShelfLife("reçel", 365, 30),
        ShelfLife("ketçap", 365, 60), ShelfLife("mayonez", 180, 30),
        ShelfLife("hardal", 365, 90), ShelfLife("soya sosu", 730, 180),
        ShelfLife("meyve suyu", 180, 3), ShelfLife("süzme peynir", 14, 7),
        ShelfLife("sucuk", 60, 10), ShelfLife("salam", 30, 5), ShelfLife("sosis", 30, 4),
        ShelfLife("çikolata", 180, 30), ShelfLife("bisküvi", 180, 14),
        ShelfLife("cips", 120, 3), ShelfLife("kahve", 365, 60),
        ShelfLife("un", 365, 120), ShelfLife("pirinç", 365, 180),
        ShelfLife("makarna", 365, 180), ShelfLife("zeytinyağı", 540, 120)
    )

    // Varsayılanlarla başlar: Room'dan yükleme asenkron olduğu için boş başlarsa
    // uygulamanın ilk saniyelerinde açılan ekleme ekranı tahmin yapamıyordu.
    private val _flow = MutableStateFlow(DEFAULTS)
    val flow: StateFlow<List<ShelfLife>> = _flow
    val all: List<ShelfLife> get() = _flow.value

    fun update(list: List<ShelfLife>) {
        _flow.value = list
    }

    /**
     * Otomatik atanmış bir tarihi, ürünün eklendiği günü koruyarak yeni gün
     * sayısına taşır. Aynı gün sayısıyla çağrılırsa tarih değişmez; böylece
     * "Gemini ile düzelt"e her basışta tarih ileri kaymaz.
     */
    fun reproject(expiryDate: String, oldDays: Int, newDays: Int): String? {
        val d = runCatching { LocalDate.parse(expiryDate) }.getOrNull() ?: return null
        return d.minusDays(oldDays.toLong()).plusDays(newDays.toLong()).toString()
    }

    /** Ürün adına en yakın eşleşen kaydı döndürür; bulunamazsa null. */
    fun match(name: String): ShelfLife? {
        if (name.isBlank()) return null
        val nameNorm = CategoryStore.normalize(name)
        val nameTokens = CategoryStore.tokens(nameNorm)
        if (nameTokens.isEmpty()) return null
        var best: ShelfLife? = null
        var bestScore = 0.0
        for (entry in all) {
            val s = CategoryStore.keywordScore(nameNorm, nameTokens, CategoryStore.normalize(entry.keyword))
            if (s > bestScore) { bestScore = s; best = entry }
        }
        return if (bestScore >= 1.5) best else null
    }

    /** Ürün adına en yakın eşleşen raf ömrünü (gün) döndürür; tahminidir, bulunamazsa null. */
    fun guess(name: String): Int? = match(name)?.days?.takeIf { it > 0 }

    /**
     * Paketi açıldıktan sonra kaç gün içinde tüketilmeli? Tabloda kayıt yoksa
     * kategoriye göre kaba bir varsayılan verilir (bilinmiyorsa null).
     */
    fun guessOpened(name: String, categoryId: String? = null): Int? {
        match(name)?.openedDays?.takeIf { it > 0 }?.let { return it }
        return when (categoryId) {
            "SUT_URUNLERI" -> 5
            "ET_TAVUK_BALIK" -> 2
            "KONSERVE" -> 3
            "SOS_BAHARAT" -> 60
            "ICECEK" -> 4
            "KAHVALTILIK" -> 14
            "ATISTIRMALIK" -> 14
            "EKMEK_UNLU" -> 3
            "BAKLIYAT_KURU" -> 120
            "DONDURULMUS" -> 30
            else -> null
        }
    }
}
