package com.envanter.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.envanter.app.data.FoodItem
import com.envanter.app.data.Repository
import com.envanter.app.data.Urgency
import com.envanter.app.gemini.CategoryFixer
import com.envanter.app.gemini.OpenedAdvisor
import com.envanter.app.util.TextParse
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * "Gemini ile kategorileri düzelt" butonu (ana sayfa ve envanterde ortak).
 * Tüm envanteri tek istekte inceler; yanlış kategorileri toplu düzeltir.
 * Kategori eşiklerine bağlı olduğu için renk uyarıları da otomatik güncellenir.
 */
@Composable
fun GeminiFixButton(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    Column(modifier) {
        OutlinedButton(
            onClick = {
                scope.launch {
                    busy = true
                    status = "Gemini tüm envanteri inceliyor…"
                    CategoryFixer.run(context)
                        .onSuccess { s ->
                            status = if (s.total == 0) "Her şey doğru görünüyor, değişiklik gerekmedi ✓"
                            else buildString {
                                append("Güncellendi ✓ ")
                                val parts = mutableListOf<String>()
                                if (s.itemsChanged > 0) parts += "${s.itemsChanged} ürün yeniden kategorilendi"
                                if (s.categoriesAdded > 0) parts += "${s.categoriesAdded} yeni kategori eklendi"
                                if (s.categoriesEdited > 0) parts += "${s.categoriesEdited} kategori eşiği düzeltildi"
                                if (s.shelfLifeUpdated > 0) parts += "${s.shelfLifeUpdated} ürün raf ömrü güncellendi"
                                if (s.datesFilled > 0) parts += "${s.datesFilled} ürüne tarih atandı"
                                if (s.openedFilled > 0) parts += "${s.openedFilled} açılmış ürünün süresi güncellendi"
                                append(parts.joinToString(", "))
                            }
                        }
                        .onFailure { status = it.message ?: "Bir hata oluştu." }
                    busy = false
                }
            },
            enabled = enabled && !busy
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.AutoFixHigh, null, modifier = Modifier.size(18.dp))
            }
            Text(" Gemini ile kategorileri düzelt", fontSize = 13.sp, maxLines = 1)
        }
        if (status.isNotBlank()) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * Kaynak/kısayol tuşu (ana sayfa ve toplu ekleme ekranında ortak).
 * [onLongClick] verilirse basılı tutmak ikinci eylemi (ör. galeriden seçme)
 * çalıştırır; böylece dört kaynak tek satıra sığar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SourceTile(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val tint = if (enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    OutlinedCard(
        modifier = modifier.combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(22.dp), tint = tint)
            Text(label, fontSize = 11.sp, maxLines = 1, color = tint)
        }
    }
}

/** Miktarı kısa yazar: 1.0 -> "1", 0.5 -> "0.5" */
fun fmtQty(q: Double): String =
    if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()

fun expiryLabel(item: FoodItem): String {
    val opened = if (item.opened) " • 📂 açıldı" else ""
    val d = item.daysLeft ?: return "tarih yok$opened"
    // Açıldıysa geçerli tarih paketinki değil, açılıştan sonraki gün sayısıdır.
    val date = item.effectiveExpiry?.let { TextParse.formatDate(it) } ?: ""
    // Tarihi sistem tahmin ettiyse belli olsun; kullanıcının verdiği tarih işaretlenmez.
    val auto = if (item.expiryAutoDays > 0 && !item.shortenedByOpening) " • ~tahmini" else ""
    return when {
        d < 0 -> "$date • ${-d} gün geçti!$auto$opened"
        d == 0L -> "$date • BUGÜN son gün$auto$opened"
        d == 1L -> "$date • yarın son gün$auto$opened"
        else -> "$date • $d gün kaldı$auto$opened"
    }
}

/** "12 Ağu'da açıldı • 5 gün içinde tüket (17 Ağu)" biçiminde özet. */
fun openedSummary(openedDate: String, openedDays: Int): String {
    val date = runCatching { LocalDate.parse(openedDate) }.getOrNull()
        ?: return "Açıldı olarak işaretli"
    val head = "${TextParse.formatDate(date)} tarihinde açıldı"
    if (openedDays <= 0) return "$head • süre bilinmiyor, gün sayısını yazabilirsin"
    val until = date.plusDays(openedDays.toLong())
    return "$head • $openedDays gün içinde tüket (${TextParse.formatDate(until)})"
}

@Composable
fun ItemRow(item: FoodItem, onClick: () -> Unit, showQuantityButtons: Boolean = true) {
    val context = LocalContext.current
    // Aktif temanın (sistem ya da elle seçilmiş) koyu olup olmadığını yüzeyden türet.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
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
                if (item.opened) {
                    Text(
                        openedSummary(item.openedDate, item.openedDays),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Paketli ürünlerde tek dokunuşla "açıldı" işareti: kalan süre o andan
            // itibaren açılış kuralına göre hesaplanır.
            if (showQuantityButtons && (item.opened || item.packagedLikely)) {
                IconButton(
                    onClick = { OpenedAdvisor.toggle(context, item) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        if (item.opened) Icons.Filled.LockOpen else Icons.Outlined.Lock,
                        if (item.opened) "açıldı işaretini kaldır" else "paketi açıldı olarak işaretle",
                        modifier = Modifier.size(18.dp),
                        tint = if (item.opened) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
