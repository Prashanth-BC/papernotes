package com.example.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
 * Bottom sheet for managing notebooks and sections.
 * Displays all notebooks in a hierarchical list with options to create, edit, and delete.
 *
 * @param onDismiss Callback when sheet is dismissed
 * @param onNotebookSelected Callback when a notebook is selected (optional)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookManagementSheet(
    onDismiss: () -> Unit,
    onNotebookSelected: ((NotebookEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val notebookRepository = remember { NotebookRepository() }
    var rootNotebooks by remember { mutableStateOf(notebookRepository.getRootNotebooks()) }
    var showNotebookDialog by remember { mutableStateOf(false) }
    var notebookToEdit by remember { mutableStateOf<NotebookEntity?>(null) }
    var parentForNewSection by remember { mutableStateOf<NotebookEntity?>(null) }
    var expandedNotebooks by remember { mutableStateOf(setOf<Long>()) }

    fun refreshNotebooks() {
        rootNotebooks = notebookRepository.getRootNotebooks()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            // Header with create button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notebooks",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = {
                        notebookToEdit = null
                        parentForNewSection = null
                        showNotebookDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create notebook",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Notebooks list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(rootNotebooks) { notebook ->
                    NotebookListItem(
                        notebook = notebook,
                        noteCount = notebookRepository.getNoteCount(notebook.id),
                        isExpanded = expandedNotebooks.contains(notebook.id),
                        onToggleExpand = {
                            expandedNotebooks = if (expandedNotebooks.contains(notebook.id)) {
                                expandedNotebooks - notebook.id
                            } else {
                                expandedNotebooks + notebook.id
                            }
                        },
                        onEdit = {
                            notebookToEdit = notebook
                            parentForNewSection = null
                            showNotebookDialog = true
                        },
                        onAddSection = {
                            notebookToEdit = null
                            parentForNewSection = notebook
                            showNotebookDialog = true
                        },
                        onClick = {
                            onNotebookSelected?.invoke(notebook)
                            onDismiss()
                        }
                    )

                    // Show sections if expanded
                    if (expandedNotebooks.contains(notebook.id)) {
                        val sections = notebookRepository.getSections(notebook.id)
                        sections.forEach { section ->
                            SectionListItem(
                                section = section,
                                noteCount = notebookRepository.getNoteCount(section.id),
                                onEdit = {
                                    notebookToEdit = section
                                    parentForNewSection = null
                                    showNotebookDialog = true
                                },
                                onClick = {
                                    onNotebookSelected?.invoke(section)
                                    onDismiss()
                                },
                                modifier = Modifier.padding(start = 32.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Notebook creation/editing dialog
    if (showNotebookDialog) {
        NotebookDialog(
            notebook = notebookToEdit,
            parentNotebook = parentForNewSection,
            onDismiss = { showNotebookDialog = false },
            onSave = { notebook, isNew ->
                notebookRepository.saveNotebook(notebook)
                refreshNotebooks()
            },
            onDelete = if (notebookToEdit != null) {
                { notebook ->
                    notebookRepository.deleteNotebook(notebook.id, deleteNotes = false)
                    refreshNotebooks()
                }
            } else null
        )
    }
}

/**
 * List item for a root-level notebook.
 */
@Composable
private fun NotebookListItem(
    notebook: NotebookEntity,
    noteCount: Long,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onAddSection: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color(notebook.color).copy(alpha = 0.1f)
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(notebook.color).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = notebook.icon ?: "📔",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name and count
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notebook.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (notebook.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Default",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Text(
                    text = "$noteCount note${if (noteCount != 1L) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expand/collapse button
            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            // Menu button
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options"
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            onEdit()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, "Edit")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add Section") },
                        onClick = {
                            onAddSection()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Add, "Add section")
                        }
                    )
                }
            }
        }
    }
}

/**
 * List item for a section (child notebook).
 */
@Composable
private fun SectionListItem(
    section: NotebookEntity,
    noteCount: Long,
    onEdit: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color(section.color).copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Text(
                text = section.icon ?: "📄",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Name and count
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = "$noteCount note${if (noteCount != 1L) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Menu button
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            onEdit()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, "Edit")
                        }
                    )
                }
            }
        }
    }
}
