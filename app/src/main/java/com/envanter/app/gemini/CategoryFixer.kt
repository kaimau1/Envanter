package com.envanter.app.gemini

import android.content.Context
import com.envanter.app.data.CategoryStore
import com.envanter.app.data.Repository
import com.envanter.app.data.Settings
import com.envanter.app.data.ShelfLifeStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Toplu düzeltme sonucu özeti. */
data class FixSummary(
    val itemsChanged: Int,
    val categoriesAdded: Int,
    val categoriesEdited: Int,
    val shelfLifeUpdated: Int = 0,
    val datesFilled: Int = 0,
    /** "Açıldı" işaretli ürünlerden kaçına açıldıktan sonraki süre yazıldı. */
    val openedFilled: Int = 0
) {
    val total get() =
        itemsChanged + categoriesAdded + categoriesEdited + shelfLifeUpdated + datesFilled + openedFilled
}

/**
 * Tüm envanteri TEK bir Gemini isteğiyle (batch) inceletir; ürünler tek tek
 * gönderilmez (token tasarrufu). Gemini:
 *  - yanlış kategorileri düzeltir,
 *  - gerekirse kendi gün eşikleriyle yeni kategori ekler,
 *  - mevcut kategorilerin eşiklerini düzenleyebilir.
 */
object CategoryFixer {
    class NoApiKey : Exception("Önce Ayarlar'dan Gemini API anahtarını ekle.")

    suspend fun run(context: Context): Result<FixSummary> {
        val key = Settings.geminiKey(context).first()
        if (key.isBlank()) return Result.failure(NoApiKey())
        val model = Settings.geminiModel(context).first()
        val items = Repository.items()
        if (items.isEmpty()) return Result.success(FixSummary(0, 0, 0))
        val currentCats = Repository.categories().ifEmpty { CategoryStore.all }

        return GeminiClient.reviewInventory(
            key, model, currentCats, items.map { it.id to it.name }, ShelfLifeStore.all,
            datelessNames = items.filter { it.expiryDate.isBlank() }.map { it.name },
            openedItems = items.filter { it.opened }.map { it.id to it.name }
        ).map { review ->
                // 1) Kategori ekleme/düzenleme
                val existingById = currentCats.associateBy { it.id }
                var added = 0
                var edited = 0
                review.categories.forEach { def ->
                    val old = existingById[def.id]
                    when {
                        old == null -> added++
                        old.redDays != def.redDays || old.yellowDays != def.yellowDays ||
                            old.label != def.label || old.emoji != def.emoji -> edited++
                    }
                }
                if (review.categories.isNotEmpty()) Repository.saveCategories(review.categories)

                // 2) Raf ömrü listesi (tarih tahminleri buradan okunacağı için önce kaydedilir)
                if (review.shelfLife.isNotEmpty()) Repository.saveShelfLife(review.shelfLife)

                // 3) Ürün başına kategori + tarih; ikisi tek kayıtta birleştirilir.
                val validIds = (currentCats.map { it.id } + review.categories.map { it.id }).toSet()
                var changed = 0
                var datesFilled = 0
                var openedFilled = 0
                items.forEach { item ->
                    var updated = item

                    val newCat = review.itemCategories[item.id]
                    if (newCat != null && newCat in validIds && newCat != item.category) {
                        updated = updated.copy(category = newCat)
                        changed++
                    }

                    // Yalnız BOŞ ya da daha önce otomatik atanmış tarihlere dokunulur;
                    // kullanıcının elle/sesle/fotoğrafla verdiği tarih korunur.
                    if (!item.expiryFromUser) {
                        // Önce Gemini'nin bu ürün için verdiği gün; yoksa yerel tahmin listesi.
                        val days = review.itemDays[item.id] ?: ShelfLifeStore.guess(item.name)
                        if (days != null) {
                            val newDate = if (item.expiryDate.isBlank()) {
                                LocalDate.now().plusDays(days.toLong()).toString()
                            } else {
                                // Eklendiği günü koru: tekrar tekrar çalıştırınca tarih kaymaz.
                                ShelfLifeStore.reproject(item.expiryDate, item.expiryAutoDays, days)
                            }
                            if (newDate != null && newDate != item.expiryDate) {
                                updated = updated.copy(expiryDate = newDate, expiryAutoDays = days)
                                datesFilled++
                            }
                        }
                    }

                    // Açılmış ürünlerde asıl kritik süre "açıldıktan sonra kaç gün".
                    if (item.opened) {
                        val openedDays = review.itemOpenedDays[item.id]
                            ?: ShelfLifeStore.guessOpened(item.name, item.category)
                        if (openedDays != null && openedDays > 0 && openedDays != item.openedDays) {
                            updated = updated.copy(openedDays = openedDays)
                            openedFilled++
                        }
                    }

                    if (updated != item) Repository.save(updated)
                }

                FixSummary(changed, added, edited, review.shelfLife.size, datesFilled, openedFilled)
            }
    }
}
