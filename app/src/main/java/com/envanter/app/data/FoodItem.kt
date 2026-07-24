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
    val category: String = Category.DIGER.name,
    val quantity: Double = 1.0,
    val unit: String = "adet",
    /** ISO-8601 (yyyy-MM-dd). Boş ise tarih bilinmiyor demektir. */
    val expiryDate: String = "",
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
) {
    val categoryEnum: Category get() = Category.fromName(category)

    val expiry: LocalDate? get() = runCatching { LocalDate.parse(expiryDate) }.getOrNull()

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
