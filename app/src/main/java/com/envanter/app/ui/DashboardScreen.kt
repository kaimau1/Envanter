package com.envanter.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.envanter.app.data.Repository
import com.envanter.app.data.SortOption
import com.envanter.app.data.Urgency

/**
 * Sade ana sayfa: özet sayaçlar, hızlı ekleme kısayolları,
 * sıralanabilir "tarihi yaklaşanlar" listesi.
 */
@Composable
fun DashboardScreen(nav: NavHostController) {
    val items by Repository.observeItems().collectAsState(initial = emptyList())
    val expired = items.filter { it.urgency == Urgency.EXPIRED }
    val red = items.filter { it.urgency == Urgency.RED }
    val yellow = items.filter { it.urgency == Urgency.YELLOW }

    var sort by remember { mutableStateOf(SortOption.EXPIRY_ASC) }
    // İsim/son eklenen sıralamada tüm ürünler; tarih sıralamalarında tarihli olanlar.
    val base = when (sort) {
        SortOption.NAME_ASC, SortOption.RECENT, SortOption.CATEGORY -> items
        else -> items.filter { it.daysLeft != null }
    }
    val shown = sort.sort(base).take(12)

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
                    nav.navigate("inventory?urgency=EXPIRED")
                }
                StatCard(Modifier.weight(1f), red.size, "Çok yakın", UrgencyColors.red) {
                    nav.navigate("inventory?urgency=RED")
                }
                StatCard(Modifier.weight(1f), yellow.size, "Yaklaşıyor", UrgencyColors.yellow) {
                    nav.navigate("inventory?urgency=YELLOW")
                }
                StatCard(Modifier.weight(1f), items.size, "Toplam", Color(0xFF2E7D32)) {
                    nav.navigate("inventory?urgency=")
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
                QuickAddButton(Modifier.weight(1f), Icons.Filled.Edit, "Elle Ekle") { nav.navigate("edit") }
                QuickAddButton(Modifier.weight(1f), Icons.Filled.PhotoCamera, "Fotoğraf") { nav.navigate("edit?mode=photo") }
                QuickAddButton(Modifier.weight(1f), Icons.Filled.KeyboardVoice, "Sesle") { nav.navigate("edit?mode=voice") }
            }
        }
        if (items.isNotEmpty()) {
            item {
                GeminiFixButton(modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⏰ Ürünlerim",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                SortDropdown(sort) { sort = it }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    "Henüz ürün yok. Yukarıdan eklemeye başla!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        items(shown, key = { it.id }) { item ->
            ItemRow(item, onClick = { nav.navigate("edit/${item.id}") })
        }
    }
}

@Composable
private fun QuickAddButton(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(22.dp))
            Text(label, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
fun SortDropdown(current: SortOption, onSelect: (SortOption) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Filled.Sort, null, modifier = Modifier.size(18.dp))
            Text(" ${current.label}", fontSize = 13.sp, maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOption.entries.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    onClick = { onSelect(opt); open = false }
                )
            }
        }
    }
}
