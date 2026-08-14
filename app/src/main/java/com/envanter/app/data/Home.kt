package com.envanter.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Bir "ev" (ya da yazlık, ofis, anneannenin evi…). Her evin kendi envanteri vardır:
 * ürünler [FoodItem.homeId] ile bir eve bağlanır ve uygulamanın tamamı (ana sayfa,
 * envanter, asistan, widget) o an seçili evin ürünlerini gösterir.
 */
@Entity(tableName = "homes")
data class Home(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val emoji: String = "🏠",
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
) {
    val title: String get() = "$emoji ${name.ifBlank { "İsimsiz ev" }}"
}

/**
 * Evlerin bellek-içi kayıt defteri ve o an açık olan ev. Room'daki evler
 * değiştikçe [update] ile tazelenir; seçili ev [Settings] içinde saklanır,
 * yani uygulama kapanıp açılınca en son bakılan ev yine açılır.
 */
object HomeStore {
    /** İlk kurulumdaki (ve migration'da eski ürünlere atanan) varsayılan ev. */
    const val DEFAULT_ID = "ev"

    val DEFAULT = Home(id = DEFAULT_ID, name = "Evim", emoji = "🏠", sortOrder = 0)

    /** Ev eklerken sunulan simgeler. */
    val EMOJIS = listOf("🏠", "🏡", "🏢", "🏖️", "🏔️", "🛖", "🏬", "🧳", "👵", "🧑‍🍳", "🚐", "🏭")

    private val _homes = MutableStateFlow(listOf(DEFAULT))
    val flow: StateFlow<List<Home>> = _homes
    val all: List<Home> get() = _homes.value

    private val _activeId = MutableStateFlow(DEFAULT_ID)
    val activeId: StateFlow<String> = _activeId

    val active: Home
        get() = all.firstOrNull { it.id == _activeId.value } ?: all.firstOrNull() ?: DEFAULT

    fun byId(id: String?): Home? = id?.let { key -> all.firstOrNull { it.id == key } }

    fun update(list: List<Home>) {
        val visible = list.filterNot { it.deleted }.sortedBy { it.sortOrder }
        if (visible.isEmpty()) return
        _homes.value = visible
        // Seçili ev silindiyse ilk eve düş.
        if (visible.none { it.id == _activeId.value }) _activeId.value = visible.first().id
    }

    /** Yalnız bellekteki seçimi değiştirir; kalıcılık [Repository.setActiveHome] üzerinden. */
    fun setActive(id: String) {
        if (all.any { it.id == id }) _activeId.value = id
    }

    /**
     * Ürünün ait olduğu ev. Eski kayıtlarda (ve evsiz oluşturulan taslaklarda)
     * alan boş olabilir; bunlar varsayılan eve sayılır.
     */
    fun homeOf(item: FoodItem): String = item.homeId.ifBlank { DEFAULT_ID }
}
