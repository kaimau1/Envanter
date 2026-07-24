package com.envanter.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.envanter.app.MainActivity
import com.envanter.app.data.AppDb
import com.envanter.app.data.CategoryStore
import com.envanter.app.data.FoodItem
import com.envanter.app.data.Urgency

/**
 * Ana ekran widget'ı: tarihi en yakın ürünler + hızlı ekleme.
 * Kırmızı/sarı renk kodu uygulamadakiyle aynıdır.
 */
class ExpiringWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Renk/emoji doğruluğu için dinamik kategorileri senkron tazele.
        runCatching {
            val cats = AppDb.get(context).categoryDao().getAll()
            if (cats.isNotEmpty()) CategoryStore.update(cats)
        }
        val items = runCatching {
            AppDb.get(context).foodDao().getAll()
                .filter { it.daysLeft != null }
                .sortedBy { it.daysLeft }
                .take(5)
        }.getOrDefault(emptyList())

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(Color(0xFFFFFFFF))
                        .cornerRadius(16.dp)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⏰ Yaklaşan Tarihler",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ColorProvider(Color(0xFF2E7D32))
                            ),
                            modifier = GlanceModifier.defaultWeight()
                                .clickable(actionStartActivity<MainActivity>())
                        )
                        Text(
                            " ＋ Ekle ",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ColorProvider(Color(0xFFFFFFFF))
                            ),
                            modifier = GlanceModifier
                                .background(Color(0xFF2E7D32))
                                .cornerRadius(10.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable(actionStartActivity<MainActivity>())
                        )
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    if (items.isEmpty()) {
                        Text(
                            "Tarihli ürün yok. Eklemek için dokun.",
                            style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color(0xFF666666))),
                            modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
                        )
                    } else {
                        items.forEach { item -> WidgetRow(item) }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetRow(item: FoodItem) {
        val d = item.daysLeft ?: 0L
        val (dot, textColor) = when (item.urgency) {
            Urgency.EXPIRED, Urgency.RED -> "🔴" to Color(0xFFD32F2F)
            Urgency.YELLOW -> "🟡" to Color(0xFFB28704)
            Urgency.NORMAL -> "⚪" to Color(0xFF333333)
        }
        val label = when {
            d < 0 -> "${-d}g geçti"
            d == 0L -> "bugün!"
            else -> "$d gün"
        }
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(dot, style = TextStyle(fontSize = 10.sp))
            Spacer(GlanceModifier.width(6.dp))
            Text(
                item.name,
                style = TextStyle(fontSize = 13.sp, color = ColorProvider(Color(0xFF222222))),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                label,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(textColor)
                )
            )
        }
    }
}

class ExpiringWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpiringWidget()
}
