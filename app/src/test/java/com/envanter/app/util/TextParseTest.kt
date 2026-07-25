package com.envanter.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TextParseTest {

    @Test
    fun `goreli tarih sayiyla cozulur`() {
        val from = LocalDate.of(2026, 1, 15)
        assertEquals(LocalDate.of(2027, 1, 15), TextParse.parseRelativeDate("1 yıl sonra tarihi doluyor", from))
        assertEquals(LocalDate.of(2026, 1, 25), TextParse.parseRelativeDate("10 gün sonra", from))
        assertEquals(LocalDate.of(2026, 7, 15), TextParse.parseRelativeDate("6 ay içinde", from))
    }

    @Test
    fun `goreli tarih sayi sozcugu ile cozulur`() {
        val from = LocalDate.of(2026, 1, 15)
        assertEquals(LocalDate.of(2026, 1, 29), TextParse.parseRelativeDate("iki hafta sonra", from))
        assertEquals(LocalDate.of(2027, 1, 15), TextParse.parseRelativeDate("bir sene sonra", from))
    }

    @Test
    fun `yalin sonra tarih sayilmaz`() {
        assertNull(TextParse.parseRelativeDate("daha sonra domates"))
    }

    @Test
    fun `daha sonra ayraci urunleri boler ama 1 yil sonra bolmez`() {
        val parts = TextParse.splitProducts(
            "1 kilo süt aldım pastörize daha sonra 1 kilo domates bir paket beyaz peynir 1 yıl sonra tarihi doluyor"
        )
        assertEquals(2, parts.size)
        assertTrue(parts[0].contains("süt"))
        assertTrue(parts[1].contains("domates"))
        // "1 yıl sonra" bölünmediği için ikinci parçada bütün olarak duruyor.
        assertTrue(parts[1].contains("1 yıl sonra"))
    }

    @Test
    fun `virgul ve ve ile sayilan urunler ayri ayri cikar`() {
        val products = TextParse.parseSpeechMany("2 litre süt, 3 adet yumurta ve bir paket ekmek")
        assertEquals(3, products.size)
        assertEquals(2.0, products[0].quantity!!, 0.001)
        assertEquals("L", products[0].unit)
        assertEquals(3.0, products[1].quantity!!, 0.001)
        assertEquals("adet", products[1].unit)
        // Sayı sözcüğü de miktara çevrilir.
        assertEquals(1.0, products[2].quantity!!, 0.001)
        assertEquals("paket", products[2].unit)
    }

    @Test
    fun `dolgu sozcukleri urun adindan temizlenir`() {
        val p = TextParse.parseSpeech("1 kilo süt aldım")
        assertEquals("Süt", p.name)
        assertEquals(1.0, p.quantity!!, 0.001)
        assertEquals("kg", p.unit)
    }

    @Test
    fun `tek urunde de liste doner`() {
        val products = TextParse.parseSpeechMany("bir paket beyaz peynir 1 yıl sonra tarihi doluyor")
        assertEquals(1, products.size)
        assertNotNull(products[0].expiry)
        assertEquals(LocalDate.now().plusYears(1), products[0].expiry)
    }
}
