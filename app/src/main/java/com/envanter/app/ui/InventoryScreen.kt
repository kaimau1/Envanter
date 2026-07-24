package com.envanter.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.envanter.app.data.CategoryDef
import com.envanter.app.data.CategoryStore
import com.envanter.app.data.Repository
import com.envanter.app.data.SortOption
import com.envanter.app.data.Urgency
import com.envanter.app.util.Fuzzy

/**
 * Envanter listesi: geniş (yazım hatası toleranslı) arama, dropdown içinde
 * kategori ve sıralama seçimi, Gemini ile toplu kategori düzeltme,
 * renk kodlu liste ve +/- ile hızlı miktar değişimi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(nav: NavHostController, urgencyArg: String = "") {
    val all by Repository.observeItems().collectAsState(initial = emptyList())
    val categories by CategoryStore.flow.collectAsState()

    var query by remember { mutableStateOf("") }
    var selectedCatId by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(SortOption.EXPIRY_ASC) }
    var urgencyFilter by remember(urgencyArg) {
        mutableStateOf(runCatching { if (urgencyArg.isBlank()) null else Urgency.valueOf(urgencyArg) }.getOrNull())
    }
    val filtered = sort.sort(
        all
            .filter { selectedCatId == null || it.category == selectedCatId }
            .filter { urgencyFilter == null || it.urgency == urgencyFilter }
            .filter { query.isBlank() || Fuzzy.matches(query, it.name) }
    )

    val urgencyLabel = when (urgencyFilter) {
        Urgency.EXPIRED -> "Süresi geçenler"
        Urgency.RED -> "Tarihi çok yakın olanlar"
        Urgency.YELLOW -> "Tarihi yaklaşanlar"
        else -> null
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Ürün ara (yazım hatası sorun değil)") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, null) }
                    }
                },
                singleLine = true
            )

            // Kategori + sıralama dropdown'ları yan yana
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryDropdown(selectedCatId, categories, Modifier.weight(1f)) { selectedCatId = it }
                Box(Modifier.weight(1f)) { SortDropdown(sort) { sort = it } }
            }

            // Gemini ile toplu kategori düzeltme (ana sayfadaki ile aynı bileşen)
            GeminiFixButton(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                enabled = all.isNotEmpty()
            )

            if (urgencyLabel != null) {
                val accent = UrgencyColors.accent(urgencyFilter!!) ?: MaterialTheme.colorScheme.primary
                InputChip(
                    selected = true,
                    onClick = { urgencyFilter = null },
                    label = { Text("$urgencyLabel (${filtered.size})") },
                    trailingIcon = { Icon(Icons.Filled.Clear, "filtreyi kaldır") },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.16f),
                        selectedLabelColor = accent,
                        selectedTrailingIconColor = accent
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (all.isEmpty()) "Envanter boş.\n+ ile ilk ürününü ekle!"
                        else "Eşleşen ürün bulunamadı.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 6.dp, bottom = 80.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        ItemRow(item, onClick = { nav.navigate("edit/${item.id}") })
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { nav.navigate("edit") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, "ekle")
        }
    }
}

@Composable
private fun CategoryDropdown(
    selectedId: String?,
    categories: List<CategoryDef>,
    modifier: Modifier,
    onSelect: (String?) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val selected = selectedId?.let { id -> categories.firstOrNull { it.id == id } }
    val label = selected?.let { "${it.emoji} ${it.label}" } ?: "Tüm kategoriler"
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(label, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Tüm kategoriler", fontWeight = FontWeight.SemiBold) },
                onClick = { onSelect(null); open = false }
            )
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text("${cat.emoji} ${cat.label}") },
                    onClick = { onSelect(cat.id); open = false }
                )
            }
        }
    }
}
