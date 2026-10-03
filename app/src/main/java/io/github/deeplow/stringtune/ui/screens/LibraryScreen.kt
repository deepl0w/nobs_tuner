package io.github.deeplow.stringtune.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.deeplow.stringtune.model.InstrumentFamily
import io.github.deeplow.stringtune.model.Tuning
import io.github.deeplow.stringtune.ui.LibraryState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (Tuning) -> Unit,
    onDelete: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Tuning?>(null) }

    val normalisedQuery = query.trim().lowercase()
    fun matches(tuning: Tuning): Boolean =
        normalisedQuery.isEmpty() ||
            tuning.name.lowercase().contains(normalisedQuery) ||
            tuning.family.displayName.lowercase().contains(normalisedQuery) ||
            tuning.detailedSummary(state.useFlats).lowercase().contains(normalisedQuery) ||
            tuning.summary(state.useFlats).lowercase().contains(normalisedQuery)

    val favorites = state.favorites.filter(::matches)
    val custom = state.customTunings.filter(::matches)
    val presets = state.presetsByFamily
        .mapValues { (_, list) -> list.filter(::matches) }
        .filterValues { it.isNotEmpty() }

    val nothingFound = favorites.isEmpty() && custom.isEmpty() && presets.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tunings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tuner")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = "Create a custom tuning")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search tunings") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                if (nothingFound) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "No tunings match “$query”",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onCreate) { Text("Create a custom tuning") }
                        }
                    }
                }

                if (favorites.isNotEmpty()) {
                    sectionHeader("Favourites")
                    items(favorites, key = { "fav_${it.id}" }) { tuning ->
                        TuningRow(
                            tuning = tuning,
                            state = state,
                            onSelect = onSelect,
                            onToggleFavorite = onToggleFavorite,
                            onEdit = onEdit,
                            onDuplicate = onDuplicate,
                            onRequestDelete = { pendingDelete = it },
                        )
                    }
                }

                if (custom.isNotEmpty()) {
                    sectionHeader("My tunings")
                    items(custom, key = { "custom_${it.id}" }) { tuning ->
                        TuningRow(
                            tuning = tuning,
                            state = state,
                            onSelect = onSelect,
                            onToggleFavorite = onToggleFavorite,
                            onEdit = onEdit,
                            onDuplicate = onDuplicate,
                            onRequestDelete = { pendingDelete = it },
                        )
                    }
                }

                InstrumentFamily.entries.forEach { family ->
                    val list = presets[family].orEmpty()
                    if (list.isEmpty()) return@forEach
                    sectionHeader(family.displayName)
                    items(list, key = { "preset_${it.id}" }) { tuning ->
                        TuningRow(
                            tuning = tuning,
                            state = state,
                            onSelect = onSelect,
                            onToggleFavorite = onToggleFavorite,
                            onEdit = onEdit,
                            onDuplicate = onDuplicate,
                            onRequestDelete = { pendingDelete = it },
                        )
                    }
                }
            }
          }
        }
    }

    pendingDelete?.let { tuning ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete “${tuning.name}”?") },
            text = { Text("This custom tuning will be removed from your device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(tuning.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

private fun LazyListScope.sectionHeader(title: String) {
    item(key = "header_$title") {
        Column {
            HorizontalDivider()
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun TuningRow(
    tuning: Tuning,
    state: LibraryState,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (Tuning) -> Unit,
    onRequestDelete: (Tuning) -> Unit,
) {
    val isSelected = tuning.id == state.selectedId
    val isFavorite = tuning.id in state.favoriteIds
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(
                text = tuning.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = {
            Text(
                text = tuning.detailedSummary(state.useFlats),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = if (isSelected) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Currently selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                IconButton(onClick = { onToggleFavorite(tuning.id) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isFavorite) {
                            "Remove ${tuning.name} from favourites"
                        } else {
                            "Add ${tuning.name} to favourites"
                        },
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Column {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${tuning.name}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (tuning.isCustom) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onEdit(tuning.id)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (tuning.isCustom) "Duplicate" else "Copy to my tunings") },
                            leadingIcon = {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onDuplicate(tuning)
                            },
                        )
                        if (tuning.isCustom) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onRequestDelete(tuning)
                                },
                            )
                        }
                    }
                }
            }
        },
        colors = if (isSelected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier.clickable { onSelect(tuning.id) },
    )
}
