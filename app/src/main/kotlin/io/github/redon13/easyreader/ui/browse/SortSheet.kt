package io.github.redon13.easyreader.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.redon13.easyreader.data.browse.SortField
import io.github.redon13.easyreader.data.browse.SortOrder
import io.github.redon13.easyreader.ui.theme.EasyColors

/** The sort panel that slides up from the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(vm: BrowseViewModel, onDismiss: () -> Unit) {
    val start = vm.sort.value
    var field by remember { mutableStateOf(start.field) }
    var descending by remember { mutableStateOf(start.descending) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = EasyColors.Sheet,
        contentColor = EasyColors.Text,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text("Sort by", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            SortField.entries.forEach { option ->
                val chosen = option == field
                Column(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = chosen, role = Role.RadioButton) {
                            if (!chosen) {
                                field = option
                                // Dates read best newest first; names A to Z.
                                descending = option.isDate
                            }
                        }
                        .padding(vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = chosen,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = EasyColors.Accent, unselectedColor = EasyColors.TextMuted),
                        )
                        Spacer(Modifier.padding(start = 12.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (chosen) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 36.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            listOf(true, false).forEach { desc ->
                                // Show "Newest first" / "A to Z" first.
                                val d = if (option.isDate) desc else !desc
                                FilterChip(
                                    selected = descending == d,
                                    onClick = { descending = d },
                                    label = { Text(SortOrder.directionLabel(option, d)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = EasyColors.SheetChip,
                                        labelColor = EasyColors.Text,
                                        selectedContainerColor = EasyColors.Accent,
                                        selectedLabelColor = EasyColors.OnAccent,
                                    ),
                                    border = null,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text("Cancel", color = EasyColors.Text) }
                Button(
                    onClick = {
                        vm.setSort(SortOrder(field, descending))
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EasyColors.Accent, contentColor = EasyColors.OnAccent),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text("Apply", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
