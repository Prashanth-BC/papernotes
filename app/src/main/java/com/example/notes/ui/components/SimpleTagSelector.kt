package com.example.notes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notes.data.TagRepository

/**
 * Simple tag selector for note creation (doesn't require an existing note).
 *
 * @param selectedTagIds Set of currently selected tag IDs
 * @param onTagsSelected Callback when tags are selected/deselected
 * @param onDismiss Callback when sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTagSelector(
    selectedTagIds: Set<Long>,
    onTagsSelected: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tagRepository = remember { TagRepository() }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Get all available tags
    val allTags = remember(searchQuery) {
        if (searchQuery.isEmpty()) {
            tagRepository.getPopularTags(limit = 50)
        } else {
            tagRepository.searchTags(searchQuery)
        }
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Tags",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create tag"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search tags") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Selected count
            if (selectedTagIds.isNotEmpty()) {
                Text(
                    text = "${selectedTagIds.size} tag${if (selectedTagIds.size != 1) "s" else ""} selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Tag list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(allTags) { tag ->
                    val isSelected = selectedTagIds.contains(tag.id)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newSelection = if (isSelected) {
                                    selectedTagIds - tag.id
                                } else {
                                    selectedTagIds + tag.id
                                }
                                onTagsSelected(newSelection)
                            },
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface,
                        tonalElevation = if (isSelected) 4.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TagChip(tag = tag, onRemove = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${tag.usageCount} use${if (tag.usageCount != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Empty state
                if (allTags.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (searchQuery.isEmpty())
                                    "No tags yet"
                                else
                                    "No tags found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { showCreateDialog = true }) {
                                Icon(Icons.Default.Add, "Create", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Create Tag")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Done button
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Tag creation dialog
    if (showCreateDialog) {
        TagCreationDialog(
            tagRepository = tagRepository,
            onDismiss = { showCreateDialog = false },
            onTagCreated = { tag ->
                // Tag is already saved by the dialog, just select it
                onTagsSelected(selectedTagIds + tag.id)
                showCreateDialog = false
            }
        )
    }
}
