package com.envanter.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import com.envanter.app.data.HomeStore
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
    val homes by HomeStore.flow.collectAsState()
    val activeHomeId by HomeStore.activeId.collectAsState()
    val activeHome = homes.firstOrNull { it.id == activeHomeId } ?: HomeStore.DEFAULT
    val expired = items.filter { it.urgency == Urgency.EXPIRED }
    val red = items.filter { it.urgency == Urgency.RED }
    val yellow = items.filter { it.urgency == Urgency.YELLOW }

    var sort by remember { mutableStateOf(SortOption.EXPIRY_ASC) }
    // Tarihi olmayan ürünler de listelenir (tarih sıralamalarında en sona düşerler),
    // aksi halde "son kullanma tarihi girilmemiş" ürünler ana sayfada hiç görünmüyordu.
    val shown = sort.sort(items)
    val datelessCount = items.count { it.daysLeft == null }

    Box(Modifier.fillMaxSize()) {
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
                "${activeHome.title} • ${items.size} ürün",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }
        // Her evin kendi envanteri var: buradan seçilen ev tüm uygulamada geçerli olur.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                homes.forEach { home ->
                    FilterChip(
                        selected = home.id == activeHome.id,
                        onClick = { Repository.setActiveHome(home.id) },
                        label = { Text(home.title, fontSize = 13.sp, maxLines = 1) }
                    )
                }
                AssistChip(
                    onClick = { nav.navigate("homes") },
                    label = { Text("＋ Ev", fontSize = 13.sp) }
                )
            }
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
        // Tek satır, dört kaynak. Kaç ürün olduğunu sistem kendisi ayırt eder:
        // tek ürün çıkarsa doğrudan ekleme formu, birden fazla çıkarsa kontrol listesi açılır.
        // Basılı tutmak fotoğraf/videoyu galeriden seçmeye yarar.
        item {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SourceTile(Modifier.weight(1f), Icons.Filled.Edit, "Elle",
                        onClick = { nav.navigate("edit") })
                    SourceTile(Modifier.weight(1f), Icons.Filled.PhotoCamera, "Fotoğraf",
                        onClick = { nav.navigate("edit?mode=photo") },
                        onLongClick = { nav.navigate("batch?mode=gallery") })
                    SourceTile(Modifier.weight(1f), Icons.Filled.Videocam, "Video",
                        onClick = { nav.navigate("batch?mode=video") },
                        onLongClick = { nav.navigate("batch?mode=videoGallery") })
                    SourceTile(Modifier.weight(1f), Icons.Filled.KeyboardVoice, "Sesle",
                        onClick = { nav.navigate("edit?mode=voice") })
                }
                Text(
                    "Birden fazla ürün otomatik ayrıştırılır • galeriden seçmek için 📷 veya 🎥 tuşunu basılı tut",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
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
                Column(Modifier.weight(1f)) {
                    Text(
                        "⏰ Ürünlerim (${items.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (datelessCount > 0) {
                        Text(
                            "$datelessCount ürünün son kullanma tarihi yok — dokunup ekleyebilirsin",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
        // Mikrofon tuşunun altında kalan son satırı görünür tutmak için boşluk.
        item { Box(Modifier.padding(bottom = 96.dp)) {} }
    }

        // En pratik yol: basılı tut, konuş, bırak. Onay ekranı analizden sonra açılır.
        VoiceHoldFab(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp),
            onProductsReady = { nav.navigate("batch") }
        )
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
