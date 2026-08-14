package com.envanter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * "Paketi açıldı" işaretinin kuralları. Buradaki hataların hepsi sessizdir:
 * kullanıcı açtığı salçanın hâlâ 6 ay dayandığını sanır.
 */
class OpenedItemTest {

    private val today: LocalDate = LocalDate.now()

    @Test
    fun `acilmamis urunde paketin tarihi gecerlidir`() {
        val item = FoodItem(name = "salça", expiryDate = today.plusDays(200).toString())
        assertFalse(item.opened)
        assertEquals(today.plusDays(200), item.effectiveExpiry)
        assertFalse(item.shortenedByOpening)
    }

    @Test
    fun `acilinca kalan sure acilis tarihine gore kisalir`() {
        val item = FoodItem(
            name = "salça",
            category = "KONSERVE",
            expiryDate = today.plusDays(200).toString(),
            openedDate = today.toString(),
            openedDays = 20
        )
        assertTrue(item.opened)
        assertEquals(today.plusDays(20), item.effectiveExpiry)
        assertEquals(20L, item.daysLeft)
        assertTrue(item.shortenedByOpening)
    }

    /** Paketin tarihi zaten daha yakınsa açılış onu uzatmamalı. */
    @Test
    fun `acilis paketin tarihini uzatmaz`() {
        val item = FoodItem(
            name = "süt",
            expiryDate = today.plusDays(2).toString(),
            openedDate = today.toString(),
            openedDays = 30
        )
        assertEquals(today.plusDays(2), item.effectiveExpiry)
        assertFalse(item.shortenedByOpening)
    }

    /** Tarihi olmayan üründe açılış tek başına tarih üretir. */
    @Test
    fun `tarihsiz urunde acilis tarihi belirler`() {
        val item = FoodItem(name = "zeytin", openedDate = today.toString(), openedDays = 30)
        assertEquals(today.plusDays(30), item.effectiveExpiry)
        assertEquals(30L, item.daysLeft)
    }

    /** Gün sayısı bilinmiyorsa (0) hesap değişmez, ürün "tarih yok" kalır. */
    @Test
    fun `gun sayisi bilinmiyorsa tarih uydurulmaz`() {
        val item = FoodItem(name = "reçel", openedDate = today.toString(), openedDays = 0)
        assertTrue(item.opened)
        assertNull(item.effectiveExpiry)
        assertNull(item.daysLeft)
    }

    /** Açılmış süt hemen kırmızıya düşmeli (süt ürünleri eşiği 2 gün). */
    @Test
    fun `acilmis urun renk uyarisini tetikler`() {
        val kapali = FoodItem(
            name = "süt", category = "SUT_URUNLERI",
            expiryDate = today.plusDays(20).toString()
        )
        assertEquals(Urgency.NORMAL, kapali.urgency)

        val acik = kapali.copy(openedDate = today.toString(), openedDays = 2)
        assertEquals(Urgency.RED, acik.urgency)
    }

    @Test
    fun `acildiktan sonraki sure tablodan tahmin edilir`() {
        assertEquals(20, ShelfLifeStore.guessOpened("Tat salça 700g", "KONSERVE"))
        assertEquals(3, ShelfLifeStore.guessOpened("Pınar süt 1L", "SUT_URUNLERI"))
        // Tabloda yoksa kategori varsayılanı devreye girer.
        assertEquals(60, ShelfLifeStore.guessOpened("bilinmeyen çeşni", "SOS_BAHARAT"))
        // Ne tabloda ne kategoride bilgi varsa uydurulmaz.
        assertNull(ShelfLifeStore.guessOpened("bilinmeyen şey", CategoryStore.DEFAULT_ID))
    }

    /** Paketli olmayan ürünlerde listede "açıldı" tuşu çıkmaz. */
    @Test
    fun `paketli urun tahmini`() {
        assertTrue(FoodItem(name = "salça", unit = "kutu").packagedLikely)
        assertTrue(FoodItem(name = "süt", category = "SUT_URUNLERI").packagedLikely)
        assertFalse(FoodItem(name = "domates", category = "MEYVE_SEBZE", unit = "kg").packagedLikely)
    }
}
