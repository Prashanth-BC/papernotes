package com.example.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.notes.data.TagEntity
import com.example.notes.data.TagRepository
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement

/**
 * Dialog for creating a new tag with name and color selection.
 *
 * @param tagRepository Repository for tag operations
 * @param onDismiss Callback when the dialog should be dismissed
 * @param onTagCreated Callback when a tag is successfully created
 */
@Composable
fun TagCreationDialog(
    tagRepository: TagRepository,
    onDismiss: () -> Unit,
    onTagCreated: (TagEntity) -> Unit
) {
    var tagName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(tagRepository.getSuggestedColor()) }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Create New Tag",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tag name input
                OutlinedTextField(
                    value = tagName,
                    onValueChange = {
                        tagName = it
                        showError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tag Name") },
                    placeholder = { Text("e.g., Work, Personal, Ideas") },
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("Tag name cannot be empty") }
                    } else null,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Color picker
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(modifier = Modifier.height(12.dp))

                TagColorPicker(
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Preview
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (tagName.isNotEmpty()) {
                    TagChip(
                        tag = TagEntity(
                            name = tagName,
                            color = selectedColor
                        )
                    )
                } else {
                    Text(
                        text = "Enter a tag name to see preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (tagName.isBlank()) {
                                showError = true
                            } else {
                                val tagId = tagRepository.getOrCreateTag(
                                    name = tagName.trim(),
                                    color = selectedColor
                                )
                                val newTag = tagRepository.getTagById(tagId)
                                if (newTag != null) {
                                    onTagCreated(newTag)
                                }
                            }
                        }
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

/**
 * Color picker component for tag colors.
 *
 * @param selectedColor Currently selected color
 * @param onColorSelected Callback when a color is selected
 * @param modifier Modifier for the component
 */
@Composable
fun TagColorPicker(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = remember {
        listOf(
            "#E57373" to "Red",
            "#F06292" to "Pink",
            "#BA68C8" to "Purple",
            "#9575CD" to "Deep Purple",
            "#7986CB" to "Indigo",
            "#64B5F6" to "Blue",
            "#4DD0E1" to "Cyan",
            "#4DB6AC" to "Teal",
            "#81C784" to "Green",
            "#AED581" to "Light Green",
            "#FFD54F" to "Amber",
            "#FFB74D" to "Orange",
            "#FF8A65" to "Deep Orange",
            "#A1887F" to "Brown",
            "#90A4AE" to "Blue Gray",
            "#BDBDBD" to "Gray"
        )
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        colors.forEach { (colorHex, colorName) ->
            val color = android.graphics.Color.parseColor(colorHex)
            val isSelected = color == selectedColor

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = Color(color),
                        shape = CircleShape
                    )
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                        } else {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                        }
                    )
                    .clickable { onColorSelected(color) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.9f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(color)
                            )
                        }
                    }
                }
            }
        }
    }
}
