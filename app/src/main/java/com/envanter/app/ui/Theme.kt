package com.envanter.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.envanter.app.data.Urgency

private val Green = Color(0xFF2E7D32)
private val GreenLight = Color(0xFF60AD5E)

private val LightColors = lightColorScheme(
    primary = Green,
    secondary = GreenLight,
    surfaceVariant = Color(0xFFF1F5EF)
)
private val DarkColors = darkColorScheme(
    primary = GreenLight,
    secondary = Green
)

@Composable
fun EnvanterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

/** Aciliyet renkleri: kırmızı = tarih çok yakın/geçmiş, sarı = yaklaşıyor. */
object UrgencyColors {
    val red = Color(0xFFD32F2F)
    val redBg = Color(0xFFFFEBEE)
    val yellow = Color(0xFFF9A825)
    val yellowBg = Color(0xFFFFF8E1)
    val expiredBg = Color(0xFFFFCDD2)

    fun background(u: Urgency, dark: Boolean): Color = when (u) {
        Urgency.EXPIRED -> if (dark) Color(0xFF5C1A1A) else expiredBg
        Urgency.RED -> if (dark) Color(0xFF4A1414) else redBg
        Urgency.YELLOW -> if (dark) Color(0xFF4A3B00) else yellowBg
        Urgency.NORMAL -> Color.Unspecified
    }

    fun accent(u: Urgency): Color? = when (u) {
        Urgency.EXPIRED, Urgency.RED -> red
        Urgency.YELLOW -> yellow
        Urgency.NORMAL -> null
    }
}
