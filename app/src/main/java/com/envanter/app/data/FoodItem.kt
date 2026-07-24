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
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
) {
    /** Ürünün ait olduğu (dinamik) kategori tanımı. */
    val categoryEnum: CategoryDef get() = CategoryStore.byId(category)

    val expiry: LocalDate? get() = runCatching { LocalDate.parse(expiryDate) }.getOrNull()

    /**
     * Tarihi kullanıcı mı verdi? Sesle söylenen ya da fotoğraftan okunan tarih de
     * kullanıcının verisidir (kutuya sistemin yazmış olması durumu değiştirmez).
     * Böyle tarihler otomatik güncellemeden muaftır.
     */
    val expiryFromUser: Boolean get() = expiryDate.isNotBlank() && expiryAutoDays == 0

    /** Kalan gün; tarih yoksa null. Negatif = geçmiş. */
    val daysLeft: Long? get() = expiry?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }

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
}

enum class Urgency { EXPIRED, RED, YELLOW, NORMAL }

val UNITS = listOf("adet", "kg", "g", "L", "ml", "paket", "kutu", "şişe")
