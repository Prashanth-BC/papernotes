package com.example.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notes.data.NotebookEntity
import com.example.notes.data.NotebookRepository

/**
 * Simple inline notebook picker.
 * Shows current notebook and allows changing via a bottom sheet.
 *
 * @param currentNotebookId Currently selected notebook ID (null = Scratchpad)
 * @param onNotebookSelected Callback when a notebook is selected
 */
@Composable
fun NotebookPicker(
    currentNotebookId: Long?,
    onNotebookSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val notebookRepository = remember { NotebookRepository() }
    val currentNotebook = remember(currentNotebookId) {
        if (currentNotebookId != null) {
            notebookRepository.getNotebookById(currentNotebookId)
        } else {
            notebookRepository.ensureDefaultNotebook()
        }
    }

    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = "Notebook",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Current selection
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPicker = true },
            colors = CardDefaults.cardColors(
                containerColor = if (currentNotebook != null)
                    Color(currentNotebook.color).copy(alpha = 0.1f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (currentNotebook != null)
                                Color(currentNotebook.color).copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentNotebook?.icon ?: "✏️",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name
                Text(
                    text = currentNotebook?.name ?: "Scratchpad",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )

                // Change indicator
                Text(
                    text = "Change",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // Notebook picker sheet
    if (showPicker) {
        NotebookPickerSheet(
            currentNotebookId = currentNotebookId,
            onNotebookSelected = { notebookId ->
                onNotebookSelected(notebookId)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * Bottom sheet for selecting a notebook.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookPickerSheet(
    currentNotebookId: Long?,
    onNotebookSelected: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val notebookRepository = remember { NotebookRepository() }
    val rootNotebooks = remember { notebookRepository.getRootNotebooks() }
    var expandedNotebooks by remember { mutableStateOf(setOf<Long>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Select Notebook",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(rootNotebooks) { notebook ->
                    NotebookPickerItem(
                        notebook = notebook,
                        isSelected = currentNotebookId == notebook.id,
                        isExpanded = expandedNotebooks.contains(notebook.id),
                        onToggleExpand = {
                            expandedNotebooks = if (expandedNotebooks.contains(notebook.id)) {
                                expandedNotebooks - notebook.id
                            } else {
                                expandedNotebooks + notebook.id
                            }
                        },
                        onClick = { onNotebookSelected(notebook.id) }
                    )

                    // Show sections if expanded
                    if (expandedNotebooks.contains(notebook.id)) {
                        val sections = notebookRepository.getSections(notebook.id)
                        sections.forEach { section ->
                            NotebookPickerItem(
                                notebook = section,
                                isSelected = currentNotebookId == section.id,
                                isExpanded = false,
                                onToggleExpand = {},
                                onClick = { onNotebookSelected(section.id) },
                                modifier = Modifier.padding(start = 32.dp),
                                isSection = true
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NotebookPickerItem(
    notebook: NotebookEntity,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSection: Boolean = false
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected)
            Color(notebook.color).copy(alpha = 0.15f)
        else
            Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Text(
                text = notebook.icon ?: if (isSection) "📄" else "📔",
                style = if (isSection)
                    MaterialTheme.typography.titleSmall
                else
                    MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Name
            Text(
                text = notebook.name,
                style = if (isSection)
                    MaterialTheme.typography.bodyMedium
                else
                    MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            // Expand button (only for root notebooks with sections)
            if (!isSection && notebook.isRootLevel) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Selected indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
