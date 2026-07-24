package com.envanter.app.data

import java.util.Locale

/**
 * Gıda kategorileri. Her kategorinin kendi "kırmızı" ve "sarı" eşiği vardır:
 * kalan gün <= redDays  -> kırmızı (acil tüket)
 * kalan gün <= yellowDays -> sarı (yaklaşıyor)
 * aksi halde normal.
 * Uzun ömürlü gıdalarda (konserve vb.) eşikler geniş, kısa ömürlülerde dardır.
 *
 * Kategori tahmini (guess) skor tabanlıdır: ürün adı Türkçe karakterlerden
 * arındırılıp kelimelere ayrılır; her anahtar kelime için tam kelime,
 * kelime başı (Türkçe ekleri yakalamak için: pirinç→pirinci, süt→sütü),
 * alt dizgi ve yazım hatası (Levenshtein) eşleşmesi puanlanır. En yüksek
 * puanlı kategori seçilir; hiçbiri eşik geçmezse DIGER kalır.
 */
enum class Category(
    val label: String,
    val emoji: String,
    val redDays: Int,
    val yellowDays: Int,
    val keywords: List<String>
) {
    SUT_URUNLERI(
        "Süt Ürünleri", "🥛", 2, 5,
        listOf(
            "süt", "yoğurt", "yogurt", "peynir", "kaşar", "kaşkaval", "beyaz peynir",
            "tulum peyniri", "labne", "ayran", "kefir", "krema", "kaymak", "tereyağı",
            "tereyağ", "lor", "çökelek", "muhallebi", "sütlaç", "kremşanti"
        )
    ),
    ET_TAVUK_BALIK(
        "Et / Tavuk / Balık", "🥩", 1, 3,
        listOf(
            "et", "kırmızı et", "dana", "kuzu", "biftek", "antrikot", "bonfile", "kıyma",
            "tavuk", "piliç", "but", "kanat", "göğüs", "hindi", "balık", "somon", "levrek",
            "çipura", "hamsi", "uskumru", "sucuk", "sosis", "salam", "jambon", "pastırma",
            "köfte", "nugget", "şnitzel", "kavurma", "ciğer", "işkembe", "midye", "karides"
        )
    ),
    MEYVE_SEBZE(
        "Meyve / Sebze", "🥦", 2, 5,
        listOf(
            "elma", "armut", "muz", "portakal", "mandalina", "limon", "üzüm", "çilek",
            "karpuz", "kavun", "kayısı", "şeftali", "erik", "kiraz", "vişne", "nar", "incir",
            "avokado", "kivi", "ananas", "domates", "biber", "salatalık", "hıyar", "marul",
            "roka", "maydanoz", "dereotu", "ıspanak", "patates", "soğan", "sarımsak", "havuç",
            "kabak", "patlıcan", "brokoli", "karnabahar", "lahana", "pırasa", "kereviz",
            "turp", "mantar", "bezelye", "taze fasulye", "meyve", "sebze", "yeşillik"
        )
    ),
    EKMEK_UNLU(
        "Ekmek / Unlu Mamül", "🍞", 1, 3,
        listOf(
            "ekmek", "somun", "baget", "poğaça", "açma", "simit", "börek", "kek", "pasta",
            "kurabiye", "lavaş", "bazlama", "yufka", "tortilla", "pide", "kruvasan",
            "sandviç ekmeği", "hamburger ekmeği", "galeta", "grissini", "donut"
        )
    ),
    DONDURULMUS(
        "Dondurulmuş", "🧊", 14, 60,
        listOf(
            "dondurulmuş", "donuk", "dondurma", "donmuş", "buzluk", "milföy", "yufka börek",
            "patates kızartması", "parmak patates", "donmuş pizza", "donmuş sebze",
            "donmuş meyve", "buz"
        )
    ),
    KONSERVE(
        "Konserve / Kavanoz", "🥫", 30, 180,
        listOf(
            "konserve", "kavanoz", "turşu", "salça", "reçel", "marmelat", "bal", "komposto",
            "ton balığı", "ton", "mısır konservesi", "bezelye konservesi", "barbunya konservesi",
            "közlenmiş", "közlenmiş biber", "zeytin ezmesi", "domates konservesi"
        )
    ),
    BAKLIYAT_KURU(
        "Bakliyat / Kuru Gıda", "🌾", 30, 120,
        listOf(
            "makarna", "spagetti", "erişte", "şehriye", "pirinç", "bulgur", "kuskus", "irmik",
            "mercimek", "kırmızı mercimek", "yeşil mercimek", "nohut", "fasulye", "kuru fasulye",
            "barbunya", "börülce", "bakla", "un", "nişasta", "şeker", "toz şeker", "kesme şeker",
            "tuz", "kuruyemiş", "fındık", "fıstık", "yer fıstığı", "ceviz", "badem", "leblebi",
            "çekirdek", "kuru üzüm", "kuru kayısı", "kuru incir", "hurma", "yulaf", "mısır unu"
        )
    ),
    ATISTIRMALIK(
        "Atıştırmalık / Çikolata", "🍫", 14, 30,
        listOf(
            "çikolata", "cikolata", "çukulata", "bar çikolata", "bisküvi", "biskuvi", "gofret",
            "cips", "çips", "kraker", "çubuk kraker", "şekerleme", "jelibon", "lokum", "helva",
            "bar", "mısır cipsi", "patlamış mısır", "kuru pasta", "wafer", "sakız", "draje"
        )
    ),
    ICECEK(
        "İçecek", "🧃", 14, 60,
        listOf(
            "su", "maden suyu", "soda", "kola", "gazoz", "meyve suyu", "meyusu", "nektar",
            "çay", "siyah çay", "yeşil çay", "bitki çayı", "kahve", "filtre kahve", "nescafe",
            "türk kahvesi", "ayran içecek", "limonata", "şalgam", "enerji içeceği", "içecek",
            "smoothie", "ice tea", "buzlu çay"
        )
    ),
    KAHVALTILIK(
        "Kahvaltılık / Yumurta", "🥚", 5, 14,
        listOf(
            "yumurta", "zeytin", "siyah zeytin", "yeşil zeytin", "sürülebilir", "kakaolu krema",
            "fındık kreması", "fıstık ezmesi", "tahin", "pekmez", "tahin pekmez", "reçel kahvaltı",
            "gevrek", "mısır gevreği", "granola", "müsli", "yulaf ezmesi", "bal kahvaltı", "kaymak"
        )
    ),
    SOS_BAHARAT(
        "Sos / Baharat", "🧂", 30, 90,
        listOf(
            "sos", "ketçap", "mayonez", "hardal", "barbekü", "acı sos", "soya sosu", "baharat",
            "pul biber", "kırmızı biber", "karabiber", "kimyon", "kekik", "nane", "kırmızı toz",
            "sumak", "zerdeçal", "tarçın", "vanilya", "kabartma tozu", "maya", "sirke", "elma sirkesi",
            "zeytinyağı", "ayçiçek yağı", "ayçiçek", "mısırözü yağı", "sıvı yağ", "yağ", "salçalık"
        )
    ),
    DIGER("Diğer", "📦", 7, 30, emptyList());

    /** Anahtar kelimelerin Türkçe-normalize edilmiş hali (bir kez hesaplanır). */
    val normKeywords: List<String> by lazy { keywords.map { normalize(it) } }

    companion object {
        private val TR = Locale("tr", "TR")

        /** Türkçe karakterleri sadeleştirip küçük harfe indirger: "Pirinç" -> "pirinc". */
        fun normalize(s: String): String = s.lowercase(TR)
            .replace('ç', 'c').replace('ğ', 'g').replace('ı', 'i')
            .replace('ö', 'o').replace('ş', 's').replace('ü', 'u')
            .replace('â', 'a').replace('î', 'i').replace('û', 'u')
            .trim()

        private fun tokens(s: String): List<String> =
            s.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }

        private fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            val prev = IntArray(b.length + 1) { it }
            val cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                }
                System.arraycopy(cur, 0, prev, 0, cur.size)
            }
            return cur[b.length]
        }

        /**
         * Tek bir anahtar kelimenin, ürün adına (normalize + kelimeler) göre puanı.
         * Uzun ve tam kelime eşleşmeleri, kısa/parçalı eşleşmelerden yüksek puan alır.
         */
        private fun keywordScore(nameNorm: String, nameTokens: List<String>, kwNorm: String): Double {
            if (kwNorm.isBlank()) return 0.0
            val kwTokens = kwNorm.split(' ').filter { it.isNotBlank() }

            // Çok kelimeli anahtar (ör. "ton balığı"): tüm parçalar geçiyorsa güçlü eşleşme.
            if (kwTokens.size > 1) {
                val allPresent = kwTokens.all { part -> nameNorm.contains(part) }
                return if (allPresent) 3.0 + kwNorm.length * 0.1 else 0.0
            }

            var best = 0.0
            for (t in nameTokens) {
                val s = when {
                    t == kwNorm -> 3.0 + kwNorm.length * 0.1
                    // Türkçe ek toleransı: "sütü", "pirinci", "ekmeği" gibi
                    kwNorm.length >= 4 && t.startsWith(kwNorm) -> 2.4 + kwNorm.length * 0.1
                    kwNorm.length == 3 && t.startsWith(kwNorm) -> 1.7
                    // yazım hatası toleransı (yeterince uzun kelimelerde)
                    kwNorm.length >= 5 && t.length >= 4 && levenshtein(t, kwNorm) <= 1 -> 1.6
                    kwNorm.length >= 6 && t.length >= 5 && levenshtein(t, kwNorm) == 2 -> 1.1
                    else -> 0.0
                }
                if (s > best) best = s
            }
            // İsim boşluksuz yazıldıysa (nadir) alt dizgi ile son bir şans.
            if (best == 0.0 && kwNorm.length >= 5 && nameNorm.contains(kwNorm)) best = 1.4
            return best
        }

        /** Ürün adından en olası kategoriyi skor tabanlı seçer. */
        fun guess(name: String): Category {
            if (name.isBlank()) return DIGER
            val nameNorm = normalize(name)
            val nameTokens = tokens(nameNorm)
            if (nameTokens.isEmpty()) return DIGER

            var best: Category = DIGER
            var bestScore = 0.0
            for (cat in entries) {
                if (cat == DIGER) continue
                var catScore = 0.0
                for (kw in cat.normKeywords) {
                    val s = keywordScore(nameNorm, nameTokens, kw)
                    if (s > catScore) catScore = s
                }
                if (catScore > bestScore) {
                    bestScore = catScore
                    best = cat
                }
            }
            return if (bestScore >= 1.5) best else DIGER
        }

        /** Enum adı ya da tam etiketle birebir eşleşme; bulunamazsa null. */
        fun fromNameOrNull(s: String?): Category? {
            if (s.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(s, true) || it.label.equals(s, true) }
        }

        /** Geriye dönük uyum: eşleşme yoksa DIGER. */
        fun fromName(s: String?): Category = fromNameOrNull(s) ?: DIGER

        /**
         * Gemini/serbest metinden gelen kategori ifadesini eşler. Enum adı, etiket,
         * yaygın Türkçe/İngilizce eş anlamlılar ve son çare olarak anahtar-kelime
         * tahmini denenir. Hiçbiri tutmazsa null (çağıran taraf ada göre tahmine düşer).
         */
        fun fromGemini(s: String?): Category? {
            if (s.isNullOrBlank()) return null
            fromNameOrNull(s)?.let { return it }
            val n = normalize(s)
            val synonyms = mapOf(
                SUT_URUNLERI to listOf("sut", "sut urunleri", "dairy", "milk", "peynir"),
                ET_TAVUK_BALIK to listOf("et", "et urunleri", "kasap", "meat", "poultry", "fish", "tavuk", "balik", "sarkuteri"),
                MEYVE_SEBZE to listOf("meyve", "sebze", "manav", "produce", "fruit", "vegetable", "yesillik", "taze"),
                EKMEK_UNLU to listOf("ekmek", "unlu", "unlu mamul", "firin", "bakery", "bread", "pastane"),
                DONDURULMUS to listOf("dondurulmus", "donmus", "frozen", "buzluk"),
                KONSERVE to listOf("konserve", "kavanoz", "canned", "jarred", "salca", "recel"),
                BAKLIYAT_KURU to listOf("bakliyat", "kuru gida", "kuru", "tahil", "dry", "grains", "legumes", "pantry", "makarna", "pirinc"),
                ATISTIRMALIK to listOf("atistirmalik", "cikolata", "snack", "chocolate", "sekerleme", "tatli"),
                ICECEK to listOf("icecek", "drink", "beverage", "su", "meyve suyu"),
                KAHVALTILIK to listOf("kahvaltilik", "kahvalti", "breakfast", "yumurta"),
                SOS_BAHARAT to listOf("sos", "baharat", "sauce", "spice", "condiment", "yag", "sirke")
            )
            synonyms.forEach { (cat, list) ->
                if (list.any { it == n || n.contains(it) || it.contains(n) }) return cat
            }
            return null
        }
    }
}
