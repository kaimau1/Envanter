package com.envanter.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Entity(tableName = "items")
data class FoodItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val category: String = CategoryStore.DEFAULT_ID,
    val quantity: Double = 1.0,
    val unit: String = "adet",
    /** ISO-8601 (yyyy-MM-dd). Boş ise tarih bilinmiyor demektir. */
    val expiryDate: String = "",
    /**
     * Tarih otomatik tahmin edildiyse kaç günlük raf ömrü kullanıldığı; 0 ise
     * tarihi kullanıcı verdi (elle, sesle veya fotoğraftan) ve otomatik güncellenmez.
     * Gün sayısını saklamak, tahmin sonradan düzeltilse bile ürünün eklendiği
     * günü koruyarak yeniden hesap yapmayı sağlar.
     */
    val expiryAutoDays: Int = 0,
    val note: String = "",
    /**
     * Paketi açıldıysa açıldığı gün (ISO-8601); boş ise ürün kapalı.
     * Paketli üründe asıl kritik tarih budur: kapalıyken aylarca duran salça
     * açıldıktan sonra haftalar içinde bozulur.
     */
    val openedDate: String = "",
    /**
     * Açıldıktan sonra kaç gün içinde tüketilmeli (Gemini ya da yerel tahmin).
     * 0 ise henüz bilinmiyor; bu durumda paketin kendi tarihi geçerli kalır.
     */
    val openedDays: Int = 0,
    /** Ürünün ait olduğu ev; boş ise varsayılan ev ([HomeStore.DEFAULT_ID]). */
    val homeId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
) {
    /** Ürünün ait olduğu (dinamik) kategori tanımı. */
    val categoryEnum: CategoryDef get() = CategoryStore.byId(category)

    /** Paketin üzerindeki (ya da tahmin edilen) son kullanma tarihi. */
    val expiry: LocalDate? get() = runCatching { LocalDate.parse(expiryDate) }.getOrNull()

    /**
     * Tarihi kullanıcı mı verdi? Sesle söylenen ya da fotoğraftan okunan tarih de
     * kullanıcının verisidir (kutuya sistemin yazmış olması durumu değiştirmez).
     * Böyle tarihler otomatik güncellemeden muaftır.
     */
    val expiryFromUser: Boolean get() = expiryDate.isNotBlank() && expiryAutoDays == 0

    /** Paketi açıldı mı? */
    val opened: Boolean get() = openedDate.isNotBlank()

    val openedOn: LocalDate? get() = runCatching { LocalDate.parse(openedDate) }.getOrNull()

    /** Açıldıktan sonraki tüketim tarihi; açık değilse ya da gün bilinmiyorsa null. */
    val openedExpiry: LocalDate?
        get() = if (openedDays > 0) openedOn?.plusDays(openedDays.toLong()) else null

    /**
     * Gerçekten geçerli olan tarih: ürün açıldıysa "açıldıktan sonra X gün"
     * kuralı paketin tarihinden erkense onu kullanırız. Renk uyarıları,
     * bildirimler ve sıralama hep bu tarihe bakar.
     */
    val effectiveExpiry: LocalDate?
        get() {
            val opened = openedExpiry ?: return expiry
            val printed = expiry ?: return opened
            return if (opened.isBefore(printed)) opened else printed
        }

    /** Açılma tarihi paketin kendi tarihini öne çekti mi? */
    val shortenedByOpening: Boolean
        get() = openedExpiry != null && (expiry == null || openedExpiry!!.isBefore(expiry))

    /** Kalan gün; tarih yoksa null. Negatif = geçmiş. */
    val daysLeft: Long? get() = effectiveExpiry?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }

    val urgency: Urgency
        get() {
            val d = daysLeft ?: return Urgency.NORMAL
            val c = categoryEnum
            return when {
                d < 0 -> Urgency.EXPIRED
                d <= c.redDays -> Urgency.RED
                d <= c.yellowDays -> Urgency.YELLOW
                else -> Urgency.NORMAL
            }
        }

    /**
     * "Açıldı" işareti bu ürün için anlamlı mı? Paketli/kapaklı ürünlerde
     * (kutu, şişe, paket ya da konserve/süt/sos gibi kategorilerde) hızlı
     * işaretleme tuşu listede gösterilir; domates-salatalık için gösterilmez.
     */
    val packagedLikely: Boolean
        get() = unit in PACKAGED_UNITS || category in PACKAGED_CATEGORIES

    companion object {
        private val PACKAGED_UNITS = setOf("paket", "kutu", "şişe", "L", "ml")
        private val PACKAGED_CATEGORIES = setOf(
            "SUT_URUNLERI", "KONSERVE", "SOS_BAHARAT", "ATISTIRMALIK",
            "ICECEK", "KAHVALTILIK", "DONDURULMUS", "BAKLIYAT_KURU"
        )
    }
}

enum class Urgency { EXPIRED, RED, YELLOW, NORMAL }

val UNITS = listOf("adet", "kg", "g", "L", "ml", "paket", "kutu", "şişe")
