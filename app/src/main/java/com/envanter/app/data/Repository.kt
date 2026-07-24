package com.envanter.app.data

import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import com.envanter.app.widget.ExpiringWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Tek veri kaynağı: Room. Firebase yapılandırılmışsa değişiklikler
 * Firestore'a da yazılır ve uzaktan gelenler Room'a işlenir.
 */
object Repository {
    lateinit var appContext: Context
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
        FirebaseSync.start(appContext)
    }

    private val dao get() = AppDb.get(appContext).foodDao()

    fun observeItems(): Flow<List<FoodItem>> = dao.observeAll()

    suspend fun items(): List<FoodItem> = dao.getAll()

    suspend fun get(id: String): FoodItem? = dao.get(id)

    fun save(item: FoodItem) {
        val stamped = item.copy(updatedAt = System.currentTimeMillis())
        scope.launch {
            dao.upsert(stamped)
            FirebaseSync.push(stamped)
            refreshWidgets()
        }
    }

    fun delete(item: FoodItem) = save(item.copy(deleted = true))

    fun changeQuantity(item: FoodItem, delta: Double) {
        val q = (item.quantity + delta)
        if (q <= 0) delete(item) else save(item.copy(quantity = q))
    }

    /** Uzaktan gelen kaydı, yerel kayıt daha yeniyse ezmeden işler. */
    suspend fun applyRemote(remote: FoodItem) {
        val local = dao.get(remote.id)
        if (local == null || remote.updatedAt > local.updatedAt) {
            dao.upsert(remote)
            refreshWidgets()
        }
    }

    suspend fun allIncludingDeleted(): List<FoodItem> = dao.getAllIncludingDeleted()

    fun refreshWidgets() {
        runCatching {
            val intent = Intent(appContext, ExpiringWidgetReceiver::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = AppWidgetManager.getInstance(appContext)
                    .getAppWidgetIds(ComponentName(appContext, ExpiringWidgetReceiver::class.java))
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            appContext.sendBroadcast(intent)
        }
    }
}
