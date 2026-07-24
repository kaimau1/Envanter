package com.envanter.app.data

/** Liste sıralama seçenekleri (ana sayfa ve envanter). */
enum class SortOption(val label: String) {
    EXPIRY_ASC("Tarihi en yakın"),
    EXPIRY_DESC("Tarihi en uzak"),
    NAME_ASC("İsim (A→Z)"),
    RECENT("Son eklenen"),
    CATEGORY("Kategoriye göre");

    fun sort(items: List<FoodItem>): List<FoodItem> = when (this) {
        EXPIRY_ASC -> items.sortedWith(compareBy(nullsLast()) { it.daysLeft })
        EXPIRY_DESC -> items.sortedWith(compareByDescending<FoodItem> { it.daysLeft != null }
            .thenByDescending { it.daysLeft ?: Long.MIN_VALUE })
        NAME_ASC -> items.sortedBy { it.name.lowercase() }
        RECENT -> items.sortedByDescending { it.updatedAt }
        CATEGORY -> items.sortedWith(compareBy({ it.categoryEnum.sortOrder }, { it.daysLeft ?: Long.MAX_VALUE }))
    }
}
