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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Tek veri kaynağı: Room. Firebase yapılandırılmışsa değişiklikler
 * Firestore'a da yazılır ve uzaktan gelenler Room'a işlenir.
 *
 * Ürünler eve göre ayrılır: [observeItems] ve [items] yalnızca o an seçili evin
 * ürünlerini verir; tüm evleri birden gerektiren yerler (bildirim) [allItems] kullanır.
 */
object Repository {
    lateinit var appContext: Context
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
        // Kategorileri yükle; tablo boşsa varsayılanları tohumla.
        scope.launch {
            if (categoryDao.count() == 0) categoryDao.upsertAll(CategoryStore.DEFAULTS)
            CategoryStore.update(categoryDao.getAll())
        }
        scope.launch {
            categoryDao.observeAll().collect { list ->
                if (list.isNotEmpty()) {
                    CategoryStore.update(list)
                    refreshWidgets()
                }
            }
        }
        // Evler: tablo boşsa varsayılan evi tohumla, sonra seçili evi geri yükle.
        scope.launch {
            if (homeDao.count() == 0) homeDao.upsert(HomeStore.DEFAULT)
            HomeStore.update(homeDao.getAll())
            val saved = Settings.activeHome(appContext).first()
            if (saved.isNotBlank()) HomeStore.setActive(saved)
        }
        scope.launch {
            homeDao.observeAll().collect { list ->
                if (list.isNotEmpty()) {
                    HomeStore.update(list)
                    refreshWidgets()
                }
            }
        }
        scope.launch {
            // Yalnız eksik varsayılanları ekler: hem ilk kurulumu tohumlar, hem de
            // sonradan eklenen varsayılanlar mevcut kurulumlara gelir. Gemini'nin
            // düzelttiği kayıtların üzerine yazmaz.
            val existing = shelfLifeDao.getAll().map { it.keyword }.toSet()
            val missing = ShelfLifeStore.DEFAULTS.filter { it.keyword !in existing }
            if (missing.isNotEmpty()) shelfLifeDao.upsertAll(missing)
            ShelfLifeStore.update(shelfLifeDao.getAll())
        }
        scope.launch {
            shelfLifeDao.observeAll().collect { list -> if (list.isNotEmpty()) ShelfLifeStore.update(list) }
        }
        FirebaseSync.start(appContext)
    }

    private val dao get() = AppDb.get(appContext).foodDao()
    private val categoryDao get() = AppDb.get(appContext).categoryDao()
    private val shelfLifeDao get() = AppDb.get(appContext).shelfLifeDao()
    private val homeDao get() = AppDb.get(appContext).homeDao()

    /** Gemini "kategorileri düzelt" ile yeni/düzeltilmiş ürün bazlı raf ömürlerini kaydeder. */
    suspend fun saveShelfLife(entries: List<ShelfLife>) {
        if (entries.isEmpty()) return
        // Gemini yalnız "açıldıktan sonra" ya da yalnız normal gün verdiyse,
        // eksik kalan alan eski kayıttan korunur.
        val existing = shelfLifeDao.getAll().associateBy { it.keyword }
        val merged = entries.map { new ->
            val old = existing[new.keyword] ?: return@map new
            new.copy(
                days = if (new.days > 0) new.days else old.days,
                openedDays = if (new.openedDays > 0) new.openedDays else old.openedDays
            )
        }
        shelfLifeDao.upsertAll(merged)
        ShelfLifeStore.update(shelfLifeDao.getAll())
    }

    suspend fun categories(): List<CategoryDef> = categoryDao.getAll()

    /** Kategori ekler/günceller (Gemini veya elle). */
    suspend fun saveCategory(def: CategoryDef) {
        categoryDao.upsert(def)
        CategoryStore.update(categoryDao.getAll())
        refreshWidgets()
    }

    suspend fun saveCategories(defs: List<CategoryDef>) {
        if (defs.isEmpty()) return
        categoryDao.upsertAll(defs)
        CategoryStore.update(categoryDao.getAll())
        refreshWidgets()
    }

    // ---- Evler ----

    suspend fun homes(): List<Home> = homeDao.getAll()

    /** Seçili evi değiştirir ve kalıcı olarak saklar. */
    fun setActiveHome(id: String) {
        HomeStore.setActive(id)
        scope.launch {
            Settings.setActiveHome(appContext, id)
            refreshWidgets()
        }
    }

    /** Yeni ev ekler ve doğrudan o eve geçer; eklenen evin id'sini döndürür. */
    suspend fun addHome(name: String, emoji: String): String {
        val order = (homeDao.getAll().maxOfOrNull { it.sortOrder } ?: 0) + 1
        val home = Home(name = name.trim().ifBlank { "Yeni ev" }, emoji = emoji, sortOrder = order)
        homeDao.upsert(home)
        HomeStore.update(homeDao.getAll())
        FirebaseSync.pushHome(home)
        setActiveHome(home.id)
        return home.id
    }

    suspend fun saveHome(home: Home) {
        val stamped = home.copy(updatedAt = System.currentTimeMillis())
        homeDao.upsert(stamped)
        HomeStore.update(homeDao.getAll())
        FirebaseSync.pushHome(stamped)
        refreshWidgets()
    }

    /**
     * Evi siler. Son ev silinemez. Evdeki ürünler kaybolmasın diye kalan
     * ilk eve taşınır; taşınan ürün sayısını döndürür.
     */
    suspend fun deleteHome(home: Home): Int {
        val remaining = homeDao.getAll().filter { it.id != home.id }
        if (remaining.isEmpty()) return -1
        val target = remaining.first()
        val moved = dao.getAll().count { HomeStore.homeOf(it) == home.id }
        // Varsayılan ev siliniyorsa evsiz (eski) kayıtlar da taşınmalı.
        homeDao.moveItems(
            source = home.id,
            target = target.id,
            alsoSource = if (home.id == HomeStore.DEFAULT_ID) "" else home.id
        )
        val removed = home.copy(deleted = true, updatedAt = System.currentTimeMillis())
        homeDao.upsert(removed)
        HomeStore.update(homeDao.getAll())
        FirebaseSync.pushHome(removed)
        // Taşınan ürünlerin yeni evi buluta da yansısın.
        dao.getAllIncludingDeleted().filter { HomeStore.homeOf(it) == target.id }.forEach { FirebaseSync.push(it) }
        setActiveHome(target.id)
        return moved
    }

    /** Uzaktan gelen evi, yerel kayıt daha yeniyse ezmeden işler. */
    suspend fun applyRemoteHome(remote: Home) {
        val local = homeDao.get(remote.id)
        if (local == null || remote.updatedAt > local.updatedAt) {
            homeDao.upsert(remote)
            HomeStore.update(homeDao.getAll())
        }
    }

    suspend fun allHomesIncludingDeleted(): List<Home> = homeDao.getAllIncludingDeleted()

    // ---- Ürünler ----

    /** Seçili evin ürünleri; ev değişince akış kendiliğinden tazelenir. */
    fun observeItems(): Flow<List<FoodItem>> =
        combine(dao.observeAll(), HomeStore.activeId) { items, home ->
            items.filter { HomeStore.homeOf(it) == home }
        }

    suspend fun items(): List<FoodItem> {
        val home = HomeStore.activeId.value
        return dao.getAll().filter { HomeStore.homeOf(it) == home }
    }

    /** Tüm evlerdeki ürünler (bildirim taraması gibi ev-üstü işler için). */
    suspend fun allItems(): List<FoodItem> = dao.getAll()

    suspend fun get(id: String): FoodItem? = dao.get(id)

    fun save(item: FoodItem) {
        // Evi belirtilmemiş kayıtlar (taslaklar, widget'tan gelenler) açık eve yazılır.
        val stamped = item.copy(
            homeId = item.homeId.ifBlank { HomeStore.activeId.value },
            updatedAt = System.currentTimeMillis()
        )
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

    /**
     * Ürünü "açıldı" olarak işaretler. Açıldıktan sonraki gün sayısı önce yerel
     * tablodan tahmin edilir (anında sonuç); Gemini anahtarı varsa çağıran taraf
     * [applyOpenedDays] ile bunu arka planda düzeltir.
     */
    fun markOpened(item: FoodItem, days: Int? = null): FoodItem {
        val guess = days ?: ShelfLifeStore.guessOpened(item.name, item.category) ?: 0
        val updated = item.copy(
            openedDate = LocalDate.now().toString(),
            openedDays = guess
        )
        save(updated)
        return updated
    }

    fun clearOpened(item: FoodItem) = save(item.copy(openedDate = "", openedDays = 0))

    /** Gemini'nin verdiği "açıldıktan sonra X gün" bilgisini kayda işler. */
    suspend fun applyOpenedDays(id: String, days: Int) {
        if (days <= 0) return
        val item = dao.get(id) ?: return
        if (!item.opened || item.openedDays == days) return
        save(item.copy(openedDays = days))
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

    /** Ad yazılırken çıkan öneriler; daha önce eklediğin (sildiklerin dahil) ürünler. */
    suspend fun suggestNames(prefix: String): List<FoodItem> {
        val q = prefix.trim()
        if (q.length < 2) return emptyList()
        // LIKE joker karakterleri kaçırılmazsa "%" yazan kullanıcı tüm listeyi çeker.
        val safe = q.replace("!", "!!").replace("%", "!%").replace("_", "!_")
        return dao.suggestByName("%$safe%")
    }

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
