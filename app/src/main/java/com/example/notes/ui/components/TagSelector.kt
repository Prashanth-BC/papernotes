package com.example.notes.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.notes.data.TagEntity
import com.example.notes.data.TagRepository
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement

/**
 * A bottom sheet component for selecting and managing tags for a note.
 *
 * @param noteId The ID of the note being tagged
 * @param selectedTags List of currently selected tags for this note
 * @param tagRepository Repository for tag operations
 * @param onDismiss Callback when the sheet should be dismissed
 * @param modifier Modifier for the sheet content
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSelectorSheet(
    noteId: Long,
    selectedTags: List<TagEntity>,
    tagRepository: TagRepository,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Manage Tags",
                style = MaterialTheme.typography.titleLarge
            )

            IconButton(onClick = { showCreateDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create new tag"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search tags...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Currently selected tags
        if (selectedTags.isNotEmpty()) {
            Text(
                text = "Selected Tags",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedTags.forEach { tag ->
                    TagChip(
                        tag = tag,
                        onRemove = {
                            tagRepository.removeTagFromNote(noteId, tag.id)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Available tags
        Text(
            text = "Available Tags",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Tag list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allTags) { tag ->
                val isSelected = selectedTags.any { it.id == tag.id }

                SelectableTagChip(
                    tag = tag,
                    isSelected = isSelected,
                    onToggle = {
                        if (isSelected) {
                            tagRepository.removeTagFromNote(noteId, tag.id)
                        } else {
                            tagRepository.addTagToNote(noteId, tag.id)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
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
    }

    // Create tag dialog
    if (showCreateDialog) {
        TagCreationDialog(
            tagRepository = tagRepository,
            onDismiss = { showCreateDialog = false },
            onTagCreated = { newTag ->
                // Automatically add the new tag to this note
                tagRepository.addTagToNote(noteId, newTag.id)
                showCreateDialog = false
            }
        )
    }
}

/**
 * Compact tag selector for inline use (e.g., in note editor).
 *
 * @param noteId The ID of the note being tagged
 * @param selectedTags List of currently selected tags
 * @param onShowFullSelector Callback to show the full tag selector sheet
 * @param modifier Modifier for the component
 */
@Composable
fun CompactTagSelector(
    noteId: Long,
    selectedTags: List<TagEntity>,
    onShowFullSelector: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Tags",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(onClick = onShowFullSelector) {
                Text("Manage")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedTags.isEmpty()) {
            OutlinedButton(
                onClick = onShowFullSelector,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Tags")
            }
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedTags.forEach { tag ->
                    TagChip(
                        tag = tag,
                        onClick = onShowFullSelector
                    )
                }

                // Add more button
                IconButton(
                    onClick = onShowFullSelector,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add more tags",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
