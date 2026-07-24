package com.envanter.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Yapay zekâ yanıtlarındaki temel Markdown'ı (kalın, italik, kod, başlık, madde)
 * gerçek stillere çevirir; işaretlerin ham metinde görünmesini engeller.
 * Harici kütüphane kullanmaz.
 */
@Composable
fun MarkdownText(md: String, modifier: Modifier = Modifier) {
    val lines = md.trim().replace("\r\n", "\n").split("\n")
    Column(modifier) {
        lines.forEach { raw ->
            val line = raw.trim()
            when {
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                line.startsWith("### ") -> Text(
                    inline(line.removePrefix("### ")),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                line.startsWith("## ") -> Text(
                    inline(line.removePrefix("## ")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                line.startsWith("# ") -> Text(
                    inline(line.removePrefix("# ")),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") ->
                    Row {
                        Text("•  ", style = MaterialTheme.typography.bodyMedium)
                        Text(inline(line.drop(2).trim()), style = MaterialTheme.typography.bodyMedium)
                    }
                Regex("""^\d+\.\s""").containsMatchIn(line) -> {
                    val num = line.substringBefore(". ")
                    Row {
                        Text("$num.  ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(inline(line.substringAfter(". ").trim()), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> Text(inline(line), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Satır içi **kalın**, *italik*, `kod` işaretlerini stillere çevirir. */
private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        when {
            s.startsWith("**", i) -> {
                val end = s.indexOf("**", i + 2)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, end)) }
                    i = end + 2
                } else { append(s[i]); i++ }
            }
            s.startsWith("__", i) -> {
                val end = s.indexOf("__", i + 2)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, end)) }
                    i = end + 2
                } else { append(s[i]); i++ }
            }
            s[i] == '*' -> {
                val end = s.indexOf('*', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.substring(i + 1, end)) }
                    i = end + 1
                } else { append(s[i]); i++ }
            }
            s[i] == '`' -> {
                val end = s.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(s.substring(i + 1, end)) }
                    i = end + 1
                } else { append(s[i]); i++ }
            }
            else -> { append(s[i]); i++ }
        }
    }
}
