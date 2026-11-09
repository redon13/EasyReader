package io.github.redon13.easyreader.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.redon13.easyreader.data.browse.Browse
import io.github.redon13.easyreader.data.browse.FilterCategory
import io.github.redon13.easyreader.ui.theme.EasyColors

/** The "Filters & shelves" panel that slides up from the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersSheet(vm: BrowseViewModel, onDismiss: () -> Unit, initialTab: Int = 0) {
    val query by vm.query.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf<FilterCategory?>(null) }
    var tab by remember { mutableStateOf(initialTab) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = EasyColors.Sheet,
        contentColor = EasyColors.Text,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(Modifier.padding(bottom = 16.dp)) {
            PrimaryTabRow(
                selectedTabIndex = tab,
                containerColor = EasyColors.Sheet,
                contentColor = EasyColors.Accent,
                divider = {},
            ) {
                listOf("Filters", "My shelves", "Smart shelves").forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(label, color = if (tab == i) EasyColors.Accent else EasyColors.TextMuted) },
                    )
                }
            }
            Spacer(Modifier.size(8.dp))

            when (tab) {
                1 -> MyShelvesTab(vm, onDismiss)
                2 -> SmartShelvesTab(vm, onDismiss)
                else -> {
                    val current = category
                    if (current == null) {
                        CategoryList(vm, query.filters, onOpen = { category = it })
                    } else {
                        ValueList(
                            vm = vm,
                            category = current,
                            selected = query.filters[current],
                            onBack = { category = null },
                            onPick = { value ->
                                vm.setFilter(current, if (value == query.filters[current]) null else value)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryList(vm: BrowseViewModel, active: Map<FilterCategory, String>, onOpen: (FilterCategory) -> Unit) {
    Column {
        FilterCategory.entries.filter { it.inFiltersTab }.forEach { category ->
            val count = remember(category, active) { vm.categoryValues(category).size }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(category) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).background(EasyColors.SheetChip, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(category.label.first().toString(), style = MaterialTheme.typography.titleMedium, color = EasyColors.Accent)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(category.label, style = MaterialTheme.typography.bodyLarge)
                    active[category]?.let {
                        Text(display(category, it), style = MaterialTheme.typography.bodySmall, color = EasyColors.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                CountBadge(count)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = EasyColors.TextMuted)
            }
        }
        if (active.isNotEmpty()) {
            TextButton(
                onClick = vm::clearSearchAndFilters,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) { Text("Clear all filters", color = EasyColors.Accent) }
        }
    }
}

@Composable
private fun ValueList(
    vm: BrowseViewModel,
    category: FilterCategory,
    selected: String?,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
) {
    val values = remember(category) { vm.categoryValues(category) }
    var find by remember { mutableStateOf("") }
    val shown = remember(values, find) {
        val f = Browse.normalise(find.trim())
        if (f.isEmpty()) values else values.filter { Browse.normalise(display(category, it.value)).contains(f) }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(category.label, style = MaterialTheme.typography.titleLarge)
        }
        if (values.size > 10) {
            TextField(
                value = find,
                onValueChange = { find = it },
                placeholder = { Text("Find ${category.label.lowercase()}", color = EasyColors.TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = EasyColors.TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = EasyColors.SheetChip,
                    unfocusedContainerColor = EasyColors.SheetChip,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = EasyColors.Accent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (values.isEmpty()) {
            Text(
                "Nothing here for the books you're looking at.",
                style = MaterialTheme.typography.bodyMedium,
                color = EasyColors.TextMuted,
                modifier = Modifier.padding(20.dp),
            )
        }
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(shown, key = { it.value }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(item.value) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        display(category, item.value),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (item.value == selected) EasyColors.Accent else EasyColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.value == selected) Icon(Icons.Default.Check, "Selected", tint = EasyColors.Accent)
                    CountBadge(item.count)
                }
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    Box(
        Modifier
            .background(EasyColors.SheetChip, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = EasyColors.Text)
    }
}

private fun display(category: FilterCategory, value: String) =
    if (category == FilterCategory.LANGUAGES) Browse.languageName(value) else value
