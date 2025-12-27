package com.example.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notes.data.NotebookEntity

/**
 * Dialog for creating or editing a notebook.
 *
 * @param notebook Existing notebook to edit, or null to create new
 * @param parentNotebook Parent notebook if creating a section
 * @param onDismiss Callback when dialog is dismissed
 * @param onSave Callback when notebook is saved (notebook, isNew)
 * @param onDelete Callback when notebook is deleted (only shown for existing notebooks)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookDialog(
    notebook: NotebookEntity? = null,
    parentNotebook: NotebookEntity? = null,
    onDismiss: () -> Unit,
    onSave: (NotebookEntity, Boolean) -> Unit,
    onDelete: ((NotebookEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isNew = notebook == null
    val isSection = parentNotebook != null || notebook?.isSection == true

    var name by remember { mutableStateOf(notebook?.name ?: "") }
    var selectedColor by remember { mutableStateOf(notebook?.color ?: NotebookColors.all[0].toArgb()) }
    var selectedIcon by remember { mutableStateOf(notebook?.icon ?: if (isSection) "📄" else "📔") }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            isNew && isSection -> "New Section"
                            isNew -> "New Notebook"
                            isSection -> "Edit Section"
                            else -> "Edit Notebook"
                        },
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

                // Parent notebook indicator
                if (parentNotebook != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(parentNotebook.color).copy(alpha = 0.1f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = parentNotebook.icon ?: "📔",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Section in: ${parentNotebook.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isSection) "Section name" else "Notebook name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Text(
                            text = selectedIcon,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Icon picker
                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val icons = if (isSection) NotebookIcons.sectionIcons else NotebookIcons.notebookIcons
                    items(icons) { icon ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selectedIcon == icon)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (selectedIcon == icon) 2.dp else 0.dp,
                                    color = if (selectedIcon == icon)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = icon,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Color picker
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(NotebookColors.all) { color ->
                        val colorArgb = color.toArgb()
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColor == colorArgb) 3.dp else 1.dp,
                                    color = if (selectedColor == colorArgb)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorArgb },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == colorArgb) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Delete button (only for existing notebooks)
                    if (!isNew && onDelete != null && notebook?.isDefault != true) {
                        TextButton(
                            onClick = { showDeleteConfirmation = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val notebookToSave = (notebook ?: NotebookEntity()).copy(
                                    name = name.trim(),
                                    color = selectedColor,
                                    icon = selectedIcon,
                                    parentNotebookId = parentNotebook?.id ?: notebook?.parentNotebookId
                                )
                                onSave(notebookToSave, isNew)
                                onDismiss()
                            },
                            enabled = name.trim().isNotBlank()
                        ) {
                            Text(if (isNew) "Create" else "Save")
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation && notebook != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete ${if (isSection) "Section" else "Notebook"}?") },
            text = {
                Text(
                    "Are you sure you want to delete \"${notebook.name}\"? " +
                    "Notes in this ${if (isSection) "section" else "notebook"} will be moved to " +
                    "${if (isSection) "the parent notebook" else "Scratchpad"}."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(notebook)
                        showDeleteConfirmation = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Predefined colors for notebooks.
 */
object NotebookColors {
    val Orange = Color(0xFFFFB74D)
    val Blue = Color(0xFF64B5F6)
    val Green = Color(0xFF81C784)
    val Red = Color(0xFFE57373)
    val Purple = Color(0xFFBA68C8)
    val Cyan = Color(0xFF4DD0E1)
    val Yellow = Color(0xFFFFF176)
    val Pink = Color(0xFFF06292)
    val Brown = Color(0xFFA1887F)
    val Gray = Color(0xFF90A4AE)
    val Teal = Color(0xFF4DB6AC)
    val Indigo = Color(0xFF7986CB)

    val all = listOf(
        Orange, Blue, Green, Red, Purple, Cyan,
        Yellow, Pink, Brown, Gray, Teal, Indigo
    )
}

/**
 * Predefined icons for notebooks and sections.
 */
object NotebookIcons {
    val notebookIcons = listOf(
        "📔", "📕", "📗", "📘", "📙", "📓",
        "📚", "🗂️", "📁", "🗃️", "📦", "🎯"
    )

    val sectionIcons = listOf(
        "📄", "📃", "📋", "📝", "🗒️", "📑",
        "🔖", "📌", "📍", "🏷️", "📎", "✏️"
    )
}
