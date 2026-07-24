package com.envanter.app.data

/**
 * Gıda kategorileri. Her kategorinin kendi "kırmızı" ve "sarı" eşiği vardır:
 * kalan gün <= redDays  -> kırmızı (acil tüket)
 * kalan gün <= yellowDays -> sarı (yaklaşıyor)
 * aksi halde normal.
 * Uzun ömürlü gıdalarda (konserve vb.) eşikler geniş, kısa ömürlülerde dardır.
 */
enum class Category(
    val label: String,
    val emoji: String,
    val redDays: Int,
    val yellowDays: Int,
    val keywords: List<String>
) {
    SUT_URUNLERI("Süt Ürünleri", "🥛", 2, 5,
        listOf("süt", "yoğurt", "yogurt", "peynir", "kaşar", "ayran", "kefir", "krema", "tereyağ", "lor")),
    ET_TAVUK_BALIK("Et / Tavuk / Balık", "🥩", 1, 3,
        listOf("et", "kıyma", "tavuk", "balık", "hindi", "sucuk", "sosis", "salam", "pastırma", "köfte")),
    MEYVE_SEBZE("Meyve / Sebze", "🥦", 2, 5,
        listOf("elma", "muz", "domates", "biber", "salatalık", "marul", "portakal", "limon", "patates", "soğan", "havuç", "meyve", "sebze")),
    EKMEK_UNLU("Ekmek / Unlu Mamül", "🍞", 1, 3,
        listOf("ekmek", "poğaça", "simit", "börek", "kek", "pasta", "lavaş", "bazlama", "tortilla")),
    DONDURULMUS("Dondurulmuş", "🧊", 14, 60,
        listOf("dondurulmuş", "donuk", "dondurma", "pizza", "milföy")),
    KONSERVE("Konserve / Kavanoz", "🥫", 30, 180,
        listOf("konserve", "kavanoz", "turşu", "salça", "reçel", "bal", "komposto", "ton balığı", "mısır konserve")),
    BAKLIYAT_KURU("Bakliyat / Kuru Gıda", "🌾", 30, 120,
        listOf("makarna", "pirinç", "bulgur", "mercimek", "nohut", "fasulye", "un", "şeker", "tuz", "kuruyemiş", "fındık", "ceviz", "badem")),
    ATISTIRMALIK("Atıştırmalık / Çikolata", "🍫", 14, 30,
        listOf("çikolata", "cikolata", "bisküvi", "gofret", "cips", "kraker", "şekerleme", "lokum", "helva", "bar")),
    ICECEK("İçecek", "🧃", 14, 60,
        listOf("su", "kola", "gazoz", "meyve suyu", "çay", "kahve", "soda", "içecek", "limonata")),
    KAHVALTILIK("Kahvaltılık / Yumurta", "🥚", 5, 14,
        listOf("yumurta", "zeytin", "sürülebilir", "fındık kreması", "tahin", "pekmez", "gevrek", "granola", "müsli")),
    SOS_BAHARAT("Sos / Baharat", "🧂", 30, 90,
        listOf("sos", "ketçap", "mayonez", "hardal", "baharat", "pul biber", "karabiber", "kekik", "nane", "sirke", "zeytinyağı", "ayçiçek yağı", "yağ")),
    DIGER("Diğer", "📦", 7, 30, emptyList());

    companion object {
        /** Ürün adından kategori tahmini (basit anahtar kelime eşleşmesi). */
        fun guess(name: String): Category {
            val n = name.lowercase()
            return entries.firstOrNull { cat -> cat.keywords.any { n.contains(it) } } ?: DIGER
        }

        fun fromName(s: String?): Category =
            entries.firstOrNull { it.name.equals(s, true) || it.label.equals(s, true) } ?: DIGER
    }
}
