package com.example.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.notes.data.NoteEntity
import com.example.notes.data.NotebookRepository
import com.example.notes.data.TagRepository
import com.example.notes.ui.components.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full-screen note details and editing interface.
 *
 * @param note The note to display and edit
 * @param onDismiss Callback when the screen is dismissed
 * @param onSave Callback when changes are saved
 * @param onDelete Callback when note is deleted
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailsScreen(
    note: NoteEntity,
    onDismiss: () -> Unit,
    onSave: (NoteEntity) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editedNote by remember { mutableStateOf(note) }
    var showImageDialog by remember { mutableStateOf(false) }
    var showNotebookPicker by remember { mutableStateOf(false) }
    var showTagSelector by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    val notebookRepository = remember { NotebookRepository() }
    val tagRepository = remember { TagRepository() }

    val currentNotebook = remember(editedNote.notebookId) {
        editedNote.notebookId?.let { notebookRepository.getNotebookById(it) }
    }

    val tags = remember(editedNote.id) {
        tagRepository.getTagsForNote(editedNote.id)
    }

    val dateFormatter = remember {
        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
        ) {
            // Top bar with actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Pin toggle
                    IconButton(onClick = {
                        editedNote = editedNote.copy(
                            isPinned = !editedNote.isPinned,
                            pinnedAt = if (!editedNote.isPinned) System.currentTimeMillis() else null
                        )
                    }) {
                        Icon(
                            imageVector = if (editedNote.isPinned) Icons.Filled.PushPin else Icons.Default.PushPin,
                            contentDescription = if (editedNote.isPinned) "Unpin" else "Pin",
                            tint = if (editedNote.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Favorite toggle
                    IconButton(onClick = {
                        editedNote = editedNote.copy(isFavorite = !editedNote.isFavorite)
                    }) {
                        Icon(
                            imageVector = if (editedNote.isFavorite) Icons.Filled.Star else Icons.Default.StarBorder,
                            contentDescription = if (editedNote.isFavorite) "Unfavorite" else "Favorite",
                            tint = if (editedNote.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Share
                    IconButton(onClick = { /* TODO: Implement share */ }) {
                        Icon(Icons.Default.Share, "Share")
                    }

                    // Delete
                    IconButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(
                            Icons.Default.Delete,
                            "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Divider()

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                OutlinedTextField(
                    value = editedNote.title,
                    onValueChange = { editedNote = editedNote.copy(title = it) },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    singleLine = false,
                    maxLines = 3
                )

                // Timestamp
                Text(
                    text = dateFormatter.format(Date(editedNote.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Image preview
                if (editedNote.imagePath.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        onClick = { showImageDialog = true }
                    ) {
                        coil.compose.AsyncImage(
                            model = editedNote.imagePath,
                            contentDescription = "Note image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    Text(
                        text = "Tap to view full size",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Divider()

                // Notebook
                Column {
                    Text(
                        text = "Notebook",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showNotebookPicker = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentNotebook?.icon ?: "✏️",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = currentNotebook?.name ?: "Scratchpad",
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "Change notebook",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

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
                                Icons.Default.Add,
                                contentDescription = "Edit tags",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Tags")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (tags.isNotEmpty()) {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEach { tag ->
                                TagChip(
                                    tag = tag,
                                    onRemove = {
                                        tagRepository.removeTagFromNote(editedNote.id, tag.id)
                                    }
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No tags",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Color
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Color",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showColorPicker = !showColorPicker }) {
                            Text(if (showColorPicker) "Hide" else "Change")
                        }
                    }
                    if (showColorPicker) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CompactColorPicker(
                            currentColor = editedNote.color,
                            onColorSelected = { editedNote = editedNote.copy(color = it) }
                        )
                    } else if (editedNote.color != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = Color(editedNote.color!!),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                    }
                }

                Divider()

                // ML Kit OCR Text
                Column {
                    Text(
                        text = "ML Kit OCR Text",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedNote.mlKitText ?: "",
                        onValueChange = { editedNote = editedNote.copy(mlKitText = it) },
                        label = { Text("Edit ML Kit text") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        placeholder = { Text("No text detected") },
                        maxLines = 10
                    )
                }

                // ColorBased OCR Text
                Column {
                    Text(
                        text = "ColorBased OCR Text",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedNote.colorBasedText ?: "",
                        onValueChange = { editedNote = editedNote.copy(colorBasedText = it) },
                        label = { Text("Edit ColorBased text") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        placeholder = { Text("No text detected") },
                        maxLines = 10
                    )
                }

                // Debug info (collapsible)
                var showDebugInfo by remember { mutableStateOf(false) }
                Column {
                    TextButton(onClick = { showDebugInfo = !showDebugInfo }) {
                        Text(if (showDebugInfo) "Hide Debug Info" else "Show Debug Info")
                    }
                    if (showDebugInfo) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("ID: ${editedNote.id}", style = MaterialTheme.typography.bodySmall)
                                Text("Image Embedding: ${if (editedNote.embedding != null) "Yes (${editedNote.embedding!!.size}-dim)" else "No"}", style = MaterialTheme.typography.bodySmall)
                                Text("CLIP Embedding: ${if (editedNote.clipEmbedding != null) "Yes (${editedNote.clipEmbedding!!.size}-dim)" else "No"}", style = MaterialTheme.typography.bodySmall)
                                Text("TrOCR Embedding: ${if (editedNote.trocrEmbedding != null) "Yes (${editedNote.trocrEmbedding!!.size}-dim)" else "No"}", style = MaterialTheme.typography.bodySmall)
                                Text("ML Kit Text Embedding: ${if (editedNote.mlKitTextEmbedding != null) "Yes (${editedNote.mlKitTextEmbedding!!.size}-dim)" else "No"}", style = MaterialTheme.typography.bodySmall)
                                Text("ColorBased Text Embedding: ${if (editedNote.colorBasedTextEmbedding != null) "Yes (${editedNote.colorBasedTextEmbedding!!.size}-dim)" else "No"}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Bottom spacing
                Spacer(modifier = Modifier.height(80.dp))
            }

            // Bottom action bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onSave(editedNote)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }

    // Full-screen image dialog
    if (showImageDialog) {
        AlertDialog(
            onDismissRequest = { showImageDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ZoomableImage(
                        model = editedNote.imagePath,
                        contentDescription = "Note image (zoomable)"
                    )
                    IconButton(
                        onClick = { showImageDialog = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            "Close",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    // Notebook picker
    if (showNotebookPicker) {
        NotebookPickerSheet(
            currentNotebookId = editedNote.notebookId,
            onNotebookSelected = { notebookId ->
                editedNote = editedNote.copy(notebookId = notebookId)
                showNotebookPicker = false
            },
            onDismiss = { showNotebookPicker = false }
        )
    }

    // Tag selector
    if (showTagSelector) {
        TagSelectorSheet(
            noteId = editedNote.id,
            selectedTags = tags,
            tagRepository = tagRepository,
            onDismiss = { showTagSelector = false }
        )
    }

    // Delete confirmation
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            icon = { Icon(Icons.Default.Delete, "Delete") },
            title = { Text("Delete Note?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirmation = false
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
 * Notebook picker bottom sheet for note editing.
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
                text = "Move to Notebook",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(rootNotebooks.size) { index ->
                    val notebook = rootNotebooks[index]
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
    notebook: com.example.notes.data.NotebookEntity,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSection: Boolean = false
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        onClick = onClick,
        color = if (isSelected)
            Color(notebook.color).copy(alpha = 0.15f)
        else
            Color.Transparent,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = notebook.icon ?: if (isSection) "📄" else "📔",
                style = if (isSection)
                    MaterialTheme.typography.titleSmall
                else
                    MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = notebook.name,
                style = if (isSection)
                    MaterialTheme.typography.bodyMedium
                else
                    MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

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
