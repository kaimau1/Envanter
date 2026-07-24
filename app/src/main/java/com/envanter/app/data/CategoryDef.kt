package com.envanter.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Çalışma zamanında düzenlenebilir/eklenebilir gıda kategorisi.
 * Eskiden sabit enum'du; artık Room'da saklanır, böylece Gemini yeni kategoriler
 * ekleyebilir, mevcutların gün eşiklerini (kırmızı/sarı) değiştirebilir.
 *
 * kalan gün <= redDays  -> kırmızı (acil)
 * kalan gün <= yellowDays -> sarı (yaklaşıyor)
 */
@Entity(tableName = "categories")
data class CategoryDef(
    @PrimaryKey val id: String,
    val label: String,
    val emoji: String,
    val redDays: Int,
    val yellowDays: Int,
    /** Virgülle ayrılmış anahtar kelimeler (Room için düz metin). */
    val keywords: String = "",
    val sortOrder: Int = 0
) {
    val keywordList: List<String>
        get() = keywords.split(",").map { it.trim() }.filter { it.isNotBlank() }
}

/**
 * Kategorilerin bellek-içi (senkron erişilebilir) kayıt defteri. Room'daki
 * kategoriler değiştikçe [update] ile tazelenir; [flow] ile Compose'a yansır.
 * FoodItem.urgency gibi senkron hesaplar buradan okur.
 */
object CategoryStore {
    const val DEFAULT_ID = "DIGER"
    private val TR = Locale("tr", "TR")

    val DEFAULTS: List<CategoryDef> = listOf(
        CategoryDef("SUT_URUNLERI", "Süt Ürünleri", "🥛", 2, 5,
            "süt,yoğurt,yogurt,peynir,kaşar,kaşkaval,beyaz peynir,tulum peyniri,labne,ayran,kefir,krema,kaymak,tereyağı,tereyağ,lor,çökelek,muhallebi,sütlaç,kremşanti", 0),
        CategoryDef("ET_TAVUK_BALIK", "Et / Tavuk / Balık", "🥩", 1, 3,
            "et,kırmızı et,dana,kuzu,biftek,antrikot,bonfile,kıyma,tavuk,piliç,but,kanat,göğüs,hindi,balık,somon,levrek,çipura,hamsi,uskumru,sucuk,sosis,salam,jambon,pastırma,köfte,nugget,şnitzel,kavurma,ciğer,işkembe,midye,karides", 1),
        CategoryDef("MEYVE_SEBZE", "Meyve / Sebze", "🥦", 2, 5,
            "elma,armut,muz,portakal,mandalina,limon,üzüm,çilek,karpuz,kavun,kayısı,şeftali,erik,kiraz,vişne,nar,incir,avokado,kivi,ananas,domates,biber,salatalık,hıyar,marul,roka,maydanoz,dereotu,ıspanak,patates,soğan,sarımsak,havuç,kabak,patlıcan,brokoli,karnabahar,lahana,pırasa,kereviz,turp,mantar,bezelye,taze fasulye,meyve,sebze,yeşillik", 2),
        CategoryDef("EKMEK_UNLU", "Ekmek / Unlu Mamül", "🍞", 1, 3,
            "ekmek,somun,baget,poğaça,açma,simit,börek,kek,pasta,kurabiye,lavaş,bazlama,yufka,tortilla,pide,kruvasan,sandviç ekmeği,hamburger ekmeği,galeta,grissini,donut", 3),
        CategoryDef("DONDURULMUS", "Dondurulmuş", "🧊", 14, 60,
            "dondurulmuş,donuk,dondurma,donmuş,buzluk,milföy,yufka börek,patates kızartması,parmak patates,donmuş pizza,donmuş sebze,donmuş meyve,buz", 4),
        CategoryDef("KONSERVE", "Konserve / Kavanoz", "🥫", 30, 180,
            "konserve,kavanoz,turşu,salça,reçel,marmelat,bal,komposto,ton balığı,ton,mısır konservesi,bezelye konservesi,barbunya konservesi,közlenmiş,közlenmiş biber,zeytin ezmesi,domates konservesi", 5),
        CategoryDef("BAKLIYAT_KURU", "Bakliyat / Kuru Gıda", "🌾", 30, 120,
            "makarna,spagetti,erişte,şehriye,pirinç,bulgur,kuskus,irmik,mercimek,kırmızı mercimek,yeşil mercimek,nohut,fasulye,kuru fasulye,barbunya,börülce,bakla,un,nişasta,şeker,toz şeker,kesme şeker,tuz,kuruyemiş,fındık,fıstık,yer fıstığı,ceviz,badem,leblebi,çekirdek,kuru üzüm,kuru kayısı,kuru incir,hurma,yulaf,mısır unu", 6),
        CategoryDef("ATISTIRMALIK", "Atıştırmalık / Çikolata", "🍫", 14, 30,
            "çikolata,cikolata,çukulata,bar çikolata,bisküvi,biskuvi,gofret,cips,çips,kraker,çubuk kraker,şekerleme,jelibon,lokum,helva,bar,mısır cipsi,patlamış mısır,kuru pasta,wafer,sakız,draje", 7),
        CategoryDef("ICECEK", "İçecek", "🧃", 14, 60,
            "su,maden suyu,soda,kola,gazoz,meyve suyu,meyusu,nektar,çay,siyah çay,yeşil çay,bitki çayı,kahve,filtre kahve,nescafe,türk kahvesi,ayran içecek,limonata,şalgam,enerji içeceği,içecek,smoothie,ice tea,buzlu çay", 8),
        CategoryDef("KAHVALTILIK", "Kahvaltılık / Yumurta", "🥚", 5, 14,
            "yumurta,zeytin,siyah zeytin,yeşil zeytin,sürülebilir,kakaolu krema,fındık kreması,fıstık ezmesi,tahin,pekmez,tahin pekmez,reçel kahvaltı,gevrek,mısır gevreği,granola,müsli,yulaf ezmesi,bal kahvaltı", 9),
        CategoryDef("SOS_BAHARAT", "Sos / Baharat", "🧂", 30, 90,
            "sos,ketçap,mayonez,hardal,barbekü,acı sos,soya sosu,baharat,pul biber,kırmızı biber,karabiber,kimyon,kekik,nane,sumak,zerdeçal,tarçın,vanilya,kabartma tozu,maya,sirke,elma sirkesi,zeytinyağı,ayçiçek yağı,ayçiçek,mısırözü yağı,sıvı yağ,yağ,salçalık", 10),
        CategoryDef(DEFAULT_ID, "Diğer", "📦", 7, 30, "", 99)
    )

    @Volatile private var byId: Map<String, CategoryDef> = DEFAULTS.associateBy { it.id }
    private val _flow = MutableStateFlow(DEFAULTS)
    val flow: StateFlow<List<CategoryDef>> = _flow

    val all: List<CategoryDef> get() = _flow.value
    val DIGER: CategoryDef get() = byId[DEFAULT_ID] ?: DEFAULTS.last()

    fun byId(id: String?): CategoryDef = id?.let { byId[it] } ?: DIGER

    fun update(list: List<CategoryDef>) {
        if (list.isEmpty()) return
        val sorted = list.sortedBy { it.sortOrder }
        byId = sorted.associateBy { it.id }
        _flow.value = sorted
    }

    // ---- İsimden kategori tahmini (skor tabanlı) ----

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

    private fun keywordScore(nameNorm: String, nameTokens: List<String>, kwNorm: String): Double {
        if (kwNorm.isBlank()) return 0.0
        val kwTokens = kwNorm.split(' ').filter { it.isNotBlank() }
        if (kwTokens.size > 1) {
            val allPresent = kwTokens.all { part -> nameNorm.contains(part) }
            return if (allPresent) 3.0 + kwNorm.length * 0.1 else 0.0
        }
        var best = 0.0
        for (t in nameTokens) {
            val s = when {
                t == kwNorm -> 3.0 + kwNorm.length * 0.1
                kwNorm.length >= 4 && t.startsWith(kwNorm) -> 2.4 + kwNorm.length * 0.1
                kwNorm.length == 3 && t.startsWith(kwNorm) -> 1.7
                kwNorm.length >= 5 && t.length >= 4 && levenshtein(t, kwNorm) <= 1 -> 1.6
                kwNorm.length >= 6 && t.length >= 5 && levenshtein(t, kwNorm) == 2 -> 1.1
                else -> 0.0
            }
            if (s > best) best = s
        }
        if (best == 0.0 && kwNorm.length >= 5 && nameNorm.contains(kwNorm)) best = 1.4
        return best
    }

    fun guess(name: String): CategoryDef {
        if (name.isBlank()) return DIGER
        val nameNorm = normalize(name)
        val nameTokens = tokens(nameNorm)
        if (nameTokens.isEmpty()) return DIGER
        var best: CategoryDef = DIGER
        var bestScore = 0.0
        for (cat in all) {
            if (cat.id == DEFAULT_ID) continue
            var catScore = 0.0
            for (kw in cat.keywordList) {
                val s = keywordScore(nameNorm, nameTokens, normalize(kw))
                if (s > catScore) catScore = s
            }
            if (catScore > bestScore) {
                bestScore = catScore
                best = cat
            }
        }
        return if (bestScore >= 1.5) best else DIGER
    }

    fun byNameOrNull(s: String?): CategoryDef? {
        if (s.isNullOrBlank()) return null
        return all.firstOrNull { it.id.equals(s, true) || it.label.equals(s, true) }
    }

    /** Gemini/serbest metinden kategori eşleme; bulunamazsa null. */
    fun fromGemini(s: String?): CategoryDef? {
        if (s.isNullOrBlank()) return null
        byNameOrNull(s)?.let { return it }
        val n = normalize(s)
        // İçerik olarak id/label eşleşmesi
        all.firstOrNull { normalize(it.label).contains(n) || n.contains(normalize(it.label)) }?.let { return it }
        return null
    }
}
