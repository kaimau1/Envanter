package com.envanter.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.envanter.app.data.Category
import com.envanter.app.data.Repository
import com.envanter.app.util.Fuzzy

/**
 * Envanter listesi: geniş (yazım hatası toleranslı) arama, kategori filtresi,
 * tarihe göre sıralı ve renk kodlu liste. +/- ile hızlı miktar değişimi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(nav: NavHostController) {
    val all by Repository.observeItems().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf<Category?>(null) }

    val filtered = all
        .filter { selectedCat == null || it.categoryEnum == selectedCat }
        .filter { query.isBlank() || Fuzzy.matches(query, it.name) }
        .sortedWith(compareBy(nullsLast()) { it.daysLeft })

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
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCat == null,
                        onClick = { selectedCat = null },
                        label = { Text("Tümü") }
                    )
                }
                items(Category.entries.toList()) { cat ->
                    FilterChip(
                        selected = selectedCat == cat,
                        onClick = { selectedCat = if (selectedCat == cat) null else cat },
                        label = { Text("${cat.emoji} ${cat.label}") }
                    )
                }
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
