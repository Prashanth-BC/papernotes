package com.example.notes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class NoteSortOption(val label: String) {
    DATE_NEWEST("Date (Newest First)"),
    DATE_OLDEST("Date (Oldest First)"),
    TITLE_AZ("Title (A-Z)"),
    TITLE_ZA("Title (Z-A)"),
    PINNED_FIRST("Pinned First")
}

enum class NoteFilterOption(val label: String) {
    ALL("All Notes"),
    FAVORITES("Favorites Only"),
    PINNED("Pinned Only"),
    HAS_TEXT("Has OCR Text"),
    NO_TEXT("Images Only"),
    TASKS_ONLY("Tasks Only"),
    NOTES_ONLY("Notes Only"),
    TASKS_INCOMPLETE("Incomplete Tasks"),
    TASKS_COMPLETE("Completed Tasks")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortFilterBottomSheet(
    currentSort: NoteSortOption,
    currentFilters: Set<NoteFilterOption>,
    onSortChanged: (NoteSortOption) -> Unit,
    onFilterToggled: (NoteFilterOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = "Sort & Filter",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            HorizontalDivider()

            // Sort Section
            Text(
                text = "SORT BY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            NoteSortOption.values().forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortChanged(option) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentSort == option,
                        onClick = { onSortChanged(option) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // Filter Section
            Text(
                text = "FILTER",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            NoteFilterOption.values().forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFilterToggled(option) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = currentFilters.contains(option),
                        onCheckedChange = { onFilterToggled(option) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Icon(
                            imageVector = when (option) {
                                NoteFilterOption.ALL -> Icons.Default.Menu
                                NoteFilterOption.FAVORITES -> Icons.Default.Star
                                NoteFilterOption.PINNED -> Icons.Default.PushPin
                                NoteFilterOption.HAS_TEXT -> Icons.Default.TextFields
                                NoteFilterOption.NO_TEXT -> Icons.Default.Image
                                NoteFilterOption.TASKS_ONLY -> Icons.Default.CheckCircle
                                NoteFilterOption.NOTES_ONLY -> Icons.Default.Note
                                NoteFilterOption.TASKS_INCOMPLETE -> Icons.Default.RadioButtonUnchecked
                                NoteFilterOption.TASKS_COMPLETE -> Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
