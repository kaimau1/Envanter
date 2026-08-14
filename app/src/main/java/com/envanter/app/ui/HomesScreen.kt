package com.envanter.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.envanter.app.data.FoodItem
import com.envanter.app.data.Home
import com.envanter.app.data.HomeStore
import com.envanter.app.data.Repository
import kotlinx.coroutines.launch

/**
 * Evler: her evin kendi envanteri var. Buradan yeni ev eklenir, adı/simgesi
 * değiştirilir, silinir ve hangi evin açık olduğu seçilir. Seçilen ev tüm
 * uygulamada (ana sayfa, envanter, asistan, bildirimler, widget) geçerlidir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomesScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val homes by HomeStore.flow.collectAsState()
    val activeId by HomeStore.activeId.collectAsState()

    // Ev başına ürün sayısı: hangi evde ne kadar var, tek bakışta görünsün.
    var allItems by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    LaunchedEffect(homes, activeId) { allItems = Repository.allItems() }

    var newName by remember { mutableStateOf("") }
    var newEmoji by remember { mutableStateOf(HomeStore.EMOJIS.first()) }
    var editing by remember { mutableStateOf<Home?>(null) }
    var deleting by remember { mutableStateOf<Home?>(null) }
    var status by remember { mutableStateOf("") }

    fun countOf(home: Home) = allItems.count { HomeStore.homeOf(it) == home.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Evlerim") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "geri") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Her evin kendi envanteri, kendi sayaçları ve kendi uyarıları vardır. " +
                    "Üstteki eve dokunarak o evin sayfasına geçersin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            homes.forEach { home ->
                val selected = home.id == activeId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            Repository.setActiveHome(home.id)
                            status = "${home.title} açıldı"
                        },
                    colors = if (selected) CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) else CardDefaults.cardColors()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(home.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${countOf(home)} ürün" + if (selected) " • açık ev" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Filled.Check, "açık ev",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { editing = home }) {
                            Icon(Icons.Filled.Edit, "adını değiştir", modifier = Modifier.size(20.dp))
                        }
                        if (homes.size > 1) {
                            IconButton(onClick = { deleting = home }) {
                                Icon(Icons.Filled.Delete, "evi sil", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            if (status.isNotBlank()) {
                Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Yeni ev ekle", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Ev adı (ör. Yazlık, Ofis, Anneannem)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HomeStore.EMOJIS.forEach { emoji ->
                            FilterChip(
                                selected = emoji == newEmoji,
                                onClick = { newEmoji = emoji },
                                label = { Text(emoji, fontSize = 16.sp) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            val name = newName.trim()
                            if (name.isBlank()) {
                                status = "Önce bir ad yaz."
                                return@Button
                            }
                            scope.launch {
                                Repository.addHome(name, newEmoji)
                                allItems = Repository.allItems()
                                status = "$newEmoji $name eklendi ve açıldı"
                                newName = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ev ekle ve bu eve geç") }
                }
            }

            Box(Modifier.padding(bottom = 24.dp))
        }
    }

    editing?.let { home ->
        var name by remember(home.id) { mutableStateOf(home.name) }
        var emoji by remember(home.id) { mutableStateOf(home.emoji) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Evi düzenle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Ev adı") },
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HomeStore.EMOJIS.forEach { e ->
                            FilterChip(
                                selected = e == emoji,
                                onClick = { emoji = e },
                                label = { Text(e, fontSize = 16.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        Repository.saveHome(home.copy(name = name.trim().ifBlank { home.name }, emoji = emoji))
                        status = "Ev güncellendi"
                    }
                    editing = null
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("İptal") } }
        )
    }

    deleting?.let { home ->
        val count = countOf(home)
        val target = homes.firstOrNull { it.id != home.id }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("${home.title} silinsin mi?") },
            text = {
                Text(
                    if (count > 0)
                        "Bu evdeki $count ürün silinmez, ${target?.title ?: "diğer ev"} envanterine taşınır."
                    else "Bu evde ürün yok."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val moved = Repository.deleteHome(home)
                        allItems = Repository.allItems()
                        status = when {
                            moved < 0 -> "Son ev silinemez."
                            moved > 0 -> "Ev silindi, $moved ürün ${target?.title ?: ""} evine taşındı."
                            else -> "Ev silindi."
                        }
                    }
                    deleting = null
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } }
        )
    }
}
