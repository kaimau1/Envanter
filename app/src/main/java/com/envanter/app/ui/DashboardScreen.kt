package com.envanter.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.envanter.app.data.Repository
import com.envanter.app.data.Urgency

/**
 * Sade ana sayfa: özet sayaçlar, hızlı ekleme kısayolları
 * ve tarihi en yakın ürünler.
 */
@Composable
fun DashboardScreen(nav: NavHostController) {
    val items by Repository.observeItems().collectAsState(initial = emptyList())
    val expired = items.filter { it.urgency == Urgency.EXPIRED }
    val red = items.filter { it.urgency == Urgency.RED }
    val yellow = items.filter { it.urgency == Urgency.YELLOW }
    val upcoming = items
        .filter { it.daysLeft != null }
        .sortedBy { it.daysLeft }
        .take(8)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                "Merhaba 👋",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp)
            )
            Text(
                "Mutfağında ${items.size} ürün var",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(Modifier.weight(1f), expired.size, "Süresi geçti", UrgencyColors.red) {
                    nav.navigate("inventory")
                }
                StatCard(Modifier.weight(1f), red.size, "Çok yakın", UrgencyColors.red) {
                    nav.navigate("inventory")
                }
                StatCard(Modifier.weight(1f), yellow.size, "Yaklaşıyor", UrgencyColors.yellow) {
                    nav.navigate("inventory")
                }
                StatCard(Modifier.weight(1f), items.size, "Toplam", Color(0xFF2E7D32)) {
                    nav.navigate("inventory")
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { nav.navigate("edit") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Edit, null)
                    Text(" Elle Ekle")
                }
                OutlinedButton(onClick = { nav.navigate("edit?mode=photo") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoCamera, null)
                    Text(" Fotoğraf")
                }
                OutlinedButton(onClick = { nav.navigate("edit?mode=voice") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.KeyboardVoice, null)
                    Text(" Sesle")
                }
            }
        }
        item {
            Text(
                "⏰ Tarihi Yaklaşanlar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
        }
        if (upcoming.isEmpty()) {
            item {
                Text(
                    "Henüz tarihli ürün yok. Sağ alttan eklemeye başla!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        items(upcoming, key = { it.id }) { item ->
            ItemRow(item, onClick = { nav.navigate("edit/${item.id}") })
        }
    }
}
