package com.envanter.app.gemini

import android.content.Context
import com.envanter.app.data.FoodItem
import com.envanter.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * "Paketi açıldı" işaretinin arkasındaki akıl.
 *
 * İşaretleme anında yerel tabloyla (ör. açılmış süt 3 gün) hemen sonuç verir,
 * ardından Gemini anahtarı varsa ürünün adına özel doğru süreyi arka planda
 * sorup kaydı günceller. Böylece kullanıcı beklemez ama sonuç isabetli olur.
 * İstek ekranın ömründen bağımsız bir kapsamda çalışır: kullanıcı listeden
 * çıksa bile cevap geldiğinde kayıt güncellenir.
 */
object OpenedAdvisor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun mark(context: Context, item: FoodItem) {
        val updated = Repository.markOpened(item)
        if (updated.name.isBlank()) return
        scope.launch {
            val days = runCatching {
                MediaAnalyzer.openedShelfLife(context, updated.name, updated.categoryEnum.label)
            }.getOrNull() ?: return@launch
            Repository.applyOpenedDays(updated.id, days)
        }
    }

    fun clear(item: FoodItem) = Repository.clearOpened(item)

    fun toggle(context: Context, item: FoodItem) {
        if (item.opened) clear(item) else mark(context, item)
    }
}
