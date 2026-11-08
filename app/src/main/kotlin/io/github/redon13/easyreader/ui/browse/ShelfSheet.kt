package io.github.redon13.easyreader.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.redon13.easyreader.calibre.CalibreBook
import io.github.redon13.easyreader.calibre.shelves
import io.github.redon13.easyreader.data.browse.FilterCategory
import io.github.redon13.easyreader.ui.theme.EasyColors

/** A steady colour for each shelf, so it's easy to recognise. */
fun shelfColour(name: String): Color {
    val palette = listOf(
        0xFFF2A23A, 0xFF8FD19E, 0xFF7FB3E8, 0xFFE88F8F,
        0xFFC9A2E8, 0xFFE8D27F, 0xFF7FD6D0, 0xFFE8B48F,
    )
    return Color(palette[Math.floorMod(name.lowercase().hashCode(), palette.size)])
}

/** "Add to shelf" for one book: tick the shelves it should be on, or make a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToShelfSheet(vm: BrowseViewModel, book: CalibreBook, onDismiss: () -> Unit) {
    val shelves by vm.shelves.collectAsStateWithLifecycle()
    val chosen = remember { mutableStateListOf<String>().apply { addAll(book.shelves) } }
    var naming by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = EasyColors.Sheet,
        contentColor = EasyColors.Text,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text("Add to shelf", style = MaterialTheme.typography.headlineSmall)
            Text(book.title, style = MaterialTheme.typography.bodyMedium, color = EasyColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            // Shelves just made here (not saved yet) are shown too.
            val names = (shelves.map { it.value } + chosen).distinctBy { it.lowercase() }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(names, key = { it }) { name ->
                    val ticked = chosen.any { it.equals(name, ignoreCase = true) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (ticked) chosen.removeAll { it.equals(name, ignoreCase = true) } else chosen.add(name)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = ticked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = EasyColors.Accent, checkmarkColor = EasyColors.OnAccent, uncheckedColor = EasyColors.TextMuted),
                            modifier = Modifier.padding(12.dp),
                        )
                        Box(Modifier.size(14.dp).background(shelfColour(name), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            NewShelfButton { naming = true }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    vm.setShelves(book, chosen.toList())
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EasyColors.Accent, contentColor = EasyColors.OnAccent),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Done", style = MaterialTheme.typography.labelLarge) }
        }
    }

    if (naming) {
        ShelfNameDialog(title = "New shelf", initial = "", onDismiss = { naming = false }) { name ->
            if (chosen.none { it.equals(name, ignoreCase = true) }) chosen.add(name)
            naming = false
        }
    }
}

/** The "My shelves" tab: tap a shelf to see its books; press and hold to rename or delete it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyShelvesTab(vm: BrowseViewModel, onDismiss: () -> Unit) {
    val shelves by vm.shelves.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(horizontal = 20.dp)) {
        if (shelves.isEmpty()) {
            Text(
                "No shelves yet. Make one here, or use Add to shelf on a book's page.",
                style = MaterialTheme.typography.bodyMedium,
                color = EasyColors.TextMuted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(shelves, key = { it.value }) { shelf ->
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    vm.clearSmartShelf()
                                    vm.setFilter(FilterCategory.SHELVES, shelf.value)
                                    onDismiss()
                                },
                                onLongClick = { menuFor = shelf.value },
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(28.dp).background(shelfColour(shelf.value), RoundedCornerShape(8.dp)))
                        Spacer(Modifier.width(14.dp))
                        Text(shelf.value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        CountChip(shelf.count)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = EasyColors.TextMuted)
                    }
                    DropdownMenu(expanded = menuFor == shelf.value, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menuFor = null; renaming = shelf.value })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menuFor = null; deleting = shelf.value })
                    }
                }
            }
        }
        if (shelves.isNotEmpty()) {
            Text(
                "Press and hold a shelf to rename or delete it.",
                style = MaterialTheme.typography.bodySmall,
                color = EasyColors.TextMuted,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        NewShelfButton { naming = true }
    }

    if (naming) {
        ShelfNameDialog(title = "New shelf", initial = "", onDismiss = { naming = false }) { name ->
            vm.createShelf(name)
            naming = false
        }
    }
    renaming?.let { old ->
        ShelfNameDialog(title = "Rename shelf", initial = old, onDismiss = { renaming = null }) { name ->
            vm.renameShelf(old, name)
            renaming = null
        }
    }
    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = EasyColors.Surface,
            title = { Text("Delete \"$name\"?") },
            text = { Text("The shelf is removed from every book. The books themselves stay in your library.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteShelf(name); deleting = null }) { Text("Delete", color = EasyColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel", color = EasyColors.Text) }
            },
        )
    }
}

/** The "Smart shelves" tab: Calibre's virtual libraries and saved searches (view only). */
@Composable
fun SmartShelvesTab(vm: BrowseViewModel, onDismiss: () -> Unit) {
    val smart by vm.smartShelves.collectAsStateWithLifecycle()
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            "These come from Calibre's virtual libraries and saved searches. Set them up in Calibre on the PC.",
            style = MaterialTheme.typography.bodyMedium,
            color = EasyColors.TextMuted,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        if (smart.isEmpty()) {
            Text(
                "None yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = EasyColors.TextMuted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(smart, key = { it.name }) { shelf ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.showSmartShelf(shelf); onDismiss() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(shelf.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    CountChip(shelf.bookIds.size)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = EasyColors.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun NewShelfButton(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, EasyColors.Outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Add, null, tint = EasyColors.Accent)
        Spacer(Modifier.width(6.dp))
        Text("New shelf", color = EasyColors.Accent, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CountChip(count: Int) {
    Box(
        Modifier
            .padding(end = 4.dp)
            .background(EasyColors.SheetChip, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = EasyColors.Text)
    }
}

@Composable
private fun ShelfNameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EasyColors.Surface,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.replace(",", "") },
                label = { Text("Shelf name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EasyColors.Accent,
                    focusedLabelColor = EasyColors.Accent,
                    cursorColor = EasyColors.Accent,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Save", color = EasyColors.Accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = EasyColors.Text) } },
    )
}
