package com.envanter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Otomatik son kullanma tarihi mantığının kritik kuralları.
 * Bunlar sessizce bozulduğunda kullanıcı ya tarihsiz ürün görür ya da
 * kendi girdiği tarihin değiştiğini görür; ikisi de fark edilmesi zor hatalardır.
 */
class ShelfLifeTest {

    /** Regresyon: liste boş başlarsa ekleme ekranı ilk saniyelerde tahmin yapamıyordu. */
    @Test
    fun `store varsayilanlarla dolu baslar`() {
        assertTrue(ShelfLifeStore.all.isNotEmpty())
        assertEquals(7, ShelfLifeStore.guess("domates"))
    }

    @Test
    fun `buyuk harf ve turkce karakter tolere edilir`() {
        assertEquals(7, ShelfLifeStore.guess("Domates"))
        assertEquals(21, ShelfLifeStore.guess("ELMA"))
    }

    @Test
    fun `bilinmeyen urun icin tahmin yapilmaz`() {
        assertNull(ShelfLifeStore.guess("hindistan cevizi unu"))
        assertNull(ShelfLifeStore.guess(""))
    }

    /** "Gemini ile düzelt"e üst üste basınca tarih ileri kaymamalı. */
    @Test
    fun `ayni gun sayisiyla yeniden hesap tarihi degistirmez`() {
        assertEquals("2026-07-31", ShelfLifeStore.reproject("2026-07-31", 7, 7))
    }

    /** Tahmin düzeltilirse eklendiği gün korunarak kaydırılır (bugünden değil). */
    @Test
    fun `gun sayisi degisince eklendigi gun korunur`() {
        // 24 Temmuz'da eklenmiş, 7 günlük tahminle 31 Temmuz olmuş ürün;
        // tahmin 10 güne çıkınca 24 + 10 = 3 Ağustos olmalı.
        assertEquals("2026-08-03", ShelfLifeStore.reproject("2026-07-31", 7, 10))
    }

    /** Kullanıcının verdiği tarih (sesle söylenen dahil) otomatik güncellemeye kapalı. */
    @Test
    fun `kullanici tarihi korunur otomatik tarih guncellenebilir`() {
        val sesleSoylenen = FoodItem(name = "domates", expiryDate = "2026-09-01", expiryAutoDays = 0)
        assertTrue(sesleSoylenen.expiryFromUser)

        val otomatikAtanan = FoodItem(name = "domates", expiryDate = "2026-07-31", expiryAutoDays = 7)
        assertFalse(otomatikAtanan.expiryFromUser)

        val tarihsiz = FoodItem(name = "domates", expiryDate = "")
        assertFalse(tarihsiz.expiryFromUser)
    }
}
