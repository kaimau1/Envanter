package com.envanter.app.gemini

import android.content.Context
import com.envanter.app.data.Repository
import com.envanter.app.data.Settings
import kotlinx.coroutines.flow.first

/**
 * Tüm envanteri TEK bir Gemini isteğiyle (batch) inceleyip yanlış kategorileri
 * düzeltir. Ürünler tek tek gönderilmez — token tasarrufu için hepsi bir liste
 * olarak tek çağrıda işlenir. Dönen değer: değiştirilen ürün sayısı.
 */
object CategoryFixer {
    class NoApiKey : Exception("Önce Ayarlar'dan Gemini API anahtarını ekle.")

    suspend fun run(context: Context): Result<Int> {
        val key = Settings.geminiKey(context).first()
        if (key.isBlank()) return Result.failure(NoApiKey())
        val model = Settings.geminiModel(context).first()
        val current = Repository.items()
        if (current.isEmpty()) return Result.success(0)
        return GeminiClient.fixCategories(key, model, current.map { it.id to it.name })
            .map { map ->
                var changed = 0
                current.forEach { item ->
                    val newCat = map[item.id]
                    if (newCat != null && newCat.name != item.category) {
                        Repository.save(item.copy(category = newCat.name))
                        changed++
                    }
                }
                changed
            }
    }
}
