package com.example.notes.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notes.data.TagRepository

/**
 * Dialog for creating quick text notes without scanning.
 * Allows entering title and text content directly.
 *
 * @param currentNotebookId Currently selected notebook ID
 * @param onDismiss Callback when dialog is dismissed
 * @param onSave Callback when note is saved with (title, content, color, notebookId, tagIds)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTextNoteDialog(
    currentNotebookId: Long?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, color: Int?, notebookId: Long?, tagIds: List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf<Int?>(null) }
    var selectedNotebookId by remember { mutableStateOf(currentNotebookId) }
    var selectedTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showTagSelector by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Quick Note",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Content input
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    maxLines = 8
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Notebook picker
                NotebookPicker(
                    currentNotebookId = selectedNotebookId,
                    onNotebookSelected = { selectedNotebookId = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tags
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tags",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showTagSelector = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add tags",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Tags")
                        }
                    }

                    // Display selected tags
                    if (selectedTagIds.isNotEmpty()) {
                        val tagRepository = remember { TagRepository() }
                        val tags = remember(selectedTagIds) {
                            selectedTagIds.mapNotNull { tagRepository.getTagById(it) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEach { tag ->
                                TagChip(
                                    tag = tag,
                                    onRemove = { selectedTagIds = selectedTagIds - tag.id }
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No tags selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Color picker
                CompactColorPicker(
                    currentColor = selectedColor,
                    onColorSelected = { selectedColor = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank() || content.isNotBlank()) {
                                onSave(
                                    title.ifBlank { "Untitled Note" },
                                    content,
                                    selectedColor,
                                    selectedNotebookId,
                                    selectedTagIds.toList()
                                )
                                onDismiss()
                            }
                        },
                        enabled = title.isNotBlank() || content.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    // Tag selector sheet
    if (showTagSelector) {
        SimpleTagSelector(
            selectedTagIds = selectedTagIds,
            onTagsSelected = { selectedTagIds = it },
            onDismiss = { showTagSelector = false }
        )
    }
}
