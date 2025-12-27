package com.example.notes.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notes.data.NotebookEntity
import com.example.notes.data.NotebookRepository

/**
 * Navigation drawer with hierarchical notebook support.
 *
 * @param selectedNotebookId Currently selected notebook ID (null for all notes)
 * @param notebookRepository Repository for notebook operations
 * @param onNotebookSelected Callback when a notebook is selected
 * @param onShowFavorites Callback to show only favorite notes
 * @param onShowAll Callback to show all notes
 * @param onAddNotebook Callback to create a new root notebook
 * @param onSettingsClick Callback for settings
 * @param modifier Modifier for the drawer
 */
@Composable
fun NotebookNavigationDrawer(
    selectedNotebookId: Long?,
    notebookRepository: NotebookRepository,
    onNotebookSelected: (Long?) -> Unit,
    onShowFavorites: () -> Unit,
    onShowAll: () -> Unit,
    onAddNotebook: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Get root notebooks and track expanded state
    val rootNotebooks = remember { notebookRepository.getRootNotebooks() }
    var expandedNotebooks by remember { mutableStateOf(setOf<Long>()) }

    ModalDrawerSheet(
        modifier = modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = "Fusion Notes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Paper to Digital",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // All Notes
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Menu, contentDescription = null) },
                label = { Text("All Notes") },
                selected = selectedNotebookId == null,
                onClick = onShowAll,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // Favorites
            NavigationDrawerItem(
                icon = {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                },
                label = { Text("Favorites") },
                selected = false,
                onClick = onShowFavorites,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Notebooks Section
            Text(
                text = "NOTEBOOKS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            // Root Notebooks (with sections)
            rootNotebooks.forEach { notebook ->
                NotebookDrawerItem(
                    notebook = notebook,
                    isSelected = selectedNotebookId == notebook.id,
                    isExpanded = expandedNotebooks.contains(notebook.id),
                    onToggleExpand = {
                        expandedNotebooks = if (expandedNotebooks.contains(notebook.id)) {
                            expandedNotebooks - notebook.id
                        } else {
                            expandedNotebooks + notebook.id
                        }
                    },
                    onClick = { onNotebookSelected(notebook.id) },
                    noteCount = notebookRepository.getNoteCount(notebook.id).toInt()
                )

                // Show sections if expanded
                AnimatedVisibility(
                    visible = expandedNotebooks.contains(notebook.id),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        val sections = notebookRepository.getSections(notebook.id)
                        sections.forEach { section ->
                            NotebookDrawerItem(
                                notebook = section,
                                isSelected = selectedNotebookId == section.id,
                                isExpanded = false,
                                onToggleExpand = {},
                                onClick = { onNotebookSelected(section.id) },
                                noteCount = notebookRepository.getNoteCount(section.id).toInt(),
                                isSection = true
                            )
                        }
                    }
                }
            }

            // Add Notebook
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                label = { Text("Add Notebook") },
                selected = false,
                onClick = onAddNotebook,
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Settings
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Settings") },
                selected = false,
                onClick = onSettingsClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * Individual notebook item in the drawer.
 */
@Composable
private fun NotebookDrawerItem(
    notebook: NotebookEntity,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    noteCount: Int,
    isSection: Boolean = false,
    modifier: Modifier = Modifier
) {
    NavigationDrawerItem(
        icon = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indentation for sections
                if (isSection) {
                    Spacer(modifier = Modifier.width(16.dp))
                }

                // Color indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = Color(notebook.color),
                            shape = CircleShape
                        )
                )

                // Icon (emoji or fallback)
                val iconText = notebook.icon
                if (!iconText.isNullOrEmpty()) {
                    Text(
                        text = iconText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Icon(
                        imageVector = if (isSection) {
                            Icons.Default.FolderOpen
                        } else {
                            Icons.Default.MenuBook
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = notebook.name,
                    modifier = Modifier.weight(1f)
                )

                if (noteCount > 0) {
                    Text(
                        text = noteCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        selected = isSelected,
        onClick = onClick,
        modifier = modifier.padding(horizontal = 12.dp),
        badge = if (!isSection && !isExpanded) {
            {
                IconButton(
                    onClick = { onToggleExpand() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Expand",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else if (!isSection && isExpanded) {
            {
                IconButton(
                    onClick = { onToggleExpand() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else null
    )
}
