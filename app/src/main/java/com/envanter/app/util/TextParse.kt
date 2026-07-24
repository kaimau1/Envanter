package com.envanter.app.util

import com.envanter.app.data.CategoryDef
import com.envanter.app.data.CategoryStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedProduct(
    val name: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val expiry: LocalDate? = null,
    val category: CategoryDef? = null
)

/**
 * OCR çıktısından ve sesle söylenen cümleden ürün bilgisi çıkarır.
 * Örn: "3 adet süt son kullanma tarihi 12 ağustos 2026"
 * Örn OCR: "SKT: 05.11.2026"
 */
object TextParse {
    private val TR = Locale("tr", "TR")

    private val months = mapOf(
        "ocak" to 1, "şubat" to 2, "subat" to 2, "mart" to 3, "nisan" to 4,
        "mayıs" to 5, "mayis" to 5, "haziran" to 6, "temmuz" to 7,
        "ağustos" to 8, "agustos" to 8, "eylül" to 9, "eylul" to 9,
        "ekim" to 10, "kasım" to 11, "kasim" to 11, "aralık" to 12, "aralik" to 12
    )

    private val numericDate = Regex("""(\d{1,2})[./\-](\d{1,2})[./\-](\d{2,4})""")
    private val monthYearOnly = Regex("""(?<![\d./\-])(\d{1,2})[./\-](\d{4})(?![\d./\-])""")
    private val verbalDate = Regex("""(\d{1,2})\s+(ocak|şubat|subat|mart|nisan|mayıs|mayis|haziran|temmuz|ağustos|agustos|eylül|eylul|ekim|kasım|kasim|aralık|aralik)(?:\s+(\d{4}))?""", RegexOption.IGNORE_CASE)
    private val qtyRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(adet|tane|kilo|kg|gram|gr|g|litre|lt|l|ml|mililitre|paket|kutu|şişe|sise)""", RegexOption.IGNORE_CASE)

    fun parseDates(text: String): List<LocalDate> {
        val found = mutableListOf<LocalDate>()
        val t = text.lowercase(TR)
        for (m in numericDate.findAll(t)) {
            val (d, mo, y) = m.destructured
            val year = y.toInt().let { if (it < 100) 2000 + it else it }
            runCatching { found += LocalDate.of(year, mo.toInt(), d.toInt()) }
        }
        for (m in verbalDate.findAll(t)) {
            val day = m.groupValues[1].toInt()
            val mo = months[m.groupValues[2].lowercase(TR)] ?: continue
            val year = m.groupValues[3].ifBlank { LocalDate.now().year.toString() }.toInt()
            runCatching {
                var date = LocalDate.of(year, mo, day)
                if (m.groupValues[3].isBlank() && date.isBefore(LocalDate.now())) date = date.plusYears(1)
                found += date
            }
        }
        if (found.isEmpty()) {
            for (m in monthYearOnly.findAll(t)) {
                val mo = m.groupValues[1].toInt()
                val y = m.groupValues[2].toInt()
                if (mo in 1..12 && y in 2000..2100) {
                    runCatching { found += LocalDate.of(y, mo, 1).plusMonths(1).minusDays(1) }
                }
            }
        }
        return found
    }

    /** OCR metninde SKT/TETT satırına en yakın, yoksa en ileri tarihi seç. */
    fun bestExpiry(text: String): LocalDate? {
        val dates = parseDates(text)
        if (dates.isEmpty()) return null
        val future = dates.filter { !it.isBefore(LocalDate.now().minusMonths(1)) }
        return (future.ifEmpty { dates }).max()
    }

    fun parseSpeech(text: String): ParsedProduct {
        val t = text.trim()
        val expiry = bestExpiry(t)
        var qty: Double? = null
        var unit: String? = null
        qtyRegex.find(t)?.let { m ->
            qty = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            unit = when (m.groupValues[2].lowercase(TR)) {
                "tane", "adet" -> "adet"
                "kilo", "kg" -> "kg"
                "gram", "gr", "g" -> "g"
                "litre", "lt", "l" -> "L"
                "ml", "mililitre" -> "ml"
                "şişe", "sise" -> "şişe"
                else -> m.groupValues[2].lowercase(TR)
            }
        }
        // İsim: tarih/miktar/anahtar kelimeleri temizle
        var name = t.lowercase(TR)
        name = name.replace(qtyRegex, " ")
        name = name.replace(numericDate, " ")
        name = name.replace(verbalDate, " ")
        name = name.replace(Regex("""son kullanma tarihi|son kullanma|kullanma tarihi|tarihi|skt|tett|ekle|envantere|envanter"""), " ")
        name = name.replace(Regex("\\s+"), " ").trim()
        val cleanName = name.replaceFirstChar { it.titlecase(TR) }.ifBlank { null }
        return ParsedProduct(
            name = cleanName,
            quantity = qty,
            unit = unit,
            expiry = expiry,
            category = cleanName?.let { CategoryStore.guess(it) }
        )
    }

    /** OCR metninden: tarih + en makul ürün adı satırı. */
    fun parseOcr(text: String): ParsedProduct {
        val expiry = bestExpiry(text)
        val nameLine = text.lines()
            .map { it.trim() }
            .filter { it.length in 3..40 }
            .filter { line -> line.count { it.isLetter() } >= line.length / 2 }
            .filterNot { it.lowercase(TR).contains(Regex("skt|tett|son kullanma|üretim|seri|lot|barkod")) }
            .maxByOrNull { it.count { c -> c.isLetter() } }
        val name = nameLine?.lowercase(TR)?.replaceFirstChar { it.titlecase(TR) }
        return ParsedProduct(
            name = name,
            expiry = expiry,
            category = name?.let { CategoryStore.guess(it) }
        )
    }

    fun formatDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}
