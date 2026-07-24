package com.envanter.app.gemini

import android.content.Context
import com.envanter.app.data.CategoryStore
import com.envanter.app.data.Repository
import com.envanter.app.data.Settings
import com.envanter.app.data.ShelfLifeStore
import kotlinx.coroutines.flow.first

/** Toplu düzeltme sonucu özeti. */
data class FixSummary(
    val itemsChanged: Int,
    val categoriesAdded: Int,
    val categoriesEdited: Int,
    val shelfLifeUpdated: Int = 0
) {
    val total get() = itemsChanged + categoriesAdded + categoriesEdited + shelfLifeUpdated
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
            key, model, currentCats, items.map { it.id to it.name }, ShelfLifeStore.all
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

                // 2) Ürün atamaları (yalnız geçerli kategoriye)
                val validIds = (currentCats.map { it.id } + review.categories.map { it.id }).toSet()
                var changed = 0
                items.forEach { item ->
                    val newCat = review.itemCategories[item.id]
                    if (newCat != null && newCat in validIds && newCat != item.category) {
                        Repository.save(item.copy(category = newCat))
                        changed++
                    }
                }
                if (review.shelfLife.isNotEmpty()) Repository.saveShelfLife(review.shelfLife)

                FixSummary(changed, added, edited, review.shelfLife.size)
            }
    }
}
