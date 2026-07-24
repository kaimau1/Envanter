package com.envanter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.envanter.app.data.FoodItem
import com.envanter.app.data.Repository
import com.envanter.app.data.Urgency
import com.envanter.app.util.TextParse

/** Miktarı kısa yazar: 1.0 -> "1", 0.5 -> "0.5" */
fun fmtQty(q: Double): String =
    if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()

fun expiryLabel(item: FoodItem): String {
    val d = item.daysLeft ?: return "tarih yok"
    val date = item.expiry?.let { TextParse.formatDate(it) } ?: ""
    return when {
        d < 0 -> "$date • ${-d} gün geçti!"
        d == 0L -> "$date • BUGÜN son gün"
        d == 1L -> "$date • yarın son gün"
        else -> "$date • $d gün kaldı"
    }
}

@Composable
fun ItemRow(item: FoodItem, onClick: () -> Unit, showQuantityButtons: Boolean = true) {
    val dark = isSystemInDarkTheme()
    val bg = UrgencyColors.background(item.urgency, dark)
    val accent = UrgencyColors.accent(item.urgency)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = if (bg != Color.Unspecified) CardDefaults.cardColors(containerColor = bg)
        else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(item.categoryEnum.emoji, fontSize = 26.sp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    item.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    expiryLabel(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (accent != null) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (showQuantityButtons) {
                IconButton(onClick = { Repository.changeQuantity(item, -1.0) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Remove, "azalt")
                }
            }
            Text(
                "${fmtQty(item.quantity)} ${item.unit}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            if (showQuantityButtons) {
                IconButton(onClick = { Repository.changeQuantity(item, 1.0) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Add, "artır")
                }
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    count: Int,
    label: String,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$count", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
