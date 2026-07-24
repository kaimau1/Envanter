package com.envanter.app.util

import java.util.Locale

/**
 * Yazım hatalarına toleranslı arama. Türkçe karakterler normalize edilir
 * (ş->s, ı->i ...), alt dizgi eşleşmesi + Levenshtein mesafesi birlikte kullanılır.
 */
object Fuzzy {
    private val TR = Locale("tr", "TR")

    fun normalize(s: String): String = s.lowercase(TR)
        .replace('ç', 'c').replace('ğ', 'g').replace('ı', 'i')
        .replace('i', 'i').replace('ö', 'o').replace('ş', 's').replace('ü', 'u')
        .trim()

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
            prev.indices.forEach { prev[it] = cur[it] }
        }
        return cur[b.length]
    }

    /** 0.0 (alakasız) .. 1.0 (tam eşleşme) arası skor. */
    fun score(query: String, target: String): Double {
        val q = normalize(query)
        val t = normalize(target)
        if (q.isEmpty()) return 1.0
        if (t.contains(q)) return 1.0
        // Kelime bazlı: her sorgu kelimesi için hedef kelimelerdeki en iyi eşleşme
        val qWords = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        val tWords = t.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (qWords.isEmpty() || tWords.isEmpty()) return 0.0
        var total = 0.0
        for (qw in qWords) {
            var best = 0.0
            for (tw in tWords) {
                val s = when {
                    tw.contains(qw) || qw.contains(tw) -> 0.9
                    else -> {
                        val d = levenshtein(qw, tw)
                        val m = maxOf(qw.length, tw.length)
                        1.0 - d.toDouble() / m
                    }
                }
                if (s > best) best = s
            }
            total += best
        }
        return total / qWords.size
    }

    /** Eşik: kısa sorgularda daha hoşgörülü olma gereği yok; 0.6 pratik bir denge. */
    fun matches(query: String, target: String): Boolean = score(query, target) >= 0.6
}
