package com.example.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Quick add bottom sheet for creating notes and tasks.
 * Inspired by Google Keep's quick creation workflow.
 *
 * @param onScanNote Callback to scan a new note with camera
 * @param onImportImage Callback to import an image from gallery
 * @param onQuickNote Callback to create a quick text note (future feature)
 * @param onQuickTask Callback to create a quick task list (future feature)
 * @param onDismiss Callback when sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    onScanNote: () -> Unit,
    onImportImage: () -> Unit,
    onQuickNote: () -> Unit,
    onQuickTask: () -> Unit,
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
                text = "Add New",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Action Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Scan Note (Primary Action)
                QuickAddOption(
                    icon = Icons.Default.CameraAlt,
                    label = "Scan Paper",
                    description = "Camera",
                    onClick = {
                        onScanNote()
                        onDismiss()
                    },
                    isPrimary = true,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Import Image
                QuickAddOption(
                    icon = Icons.Default.Image,
                    label = "Import",
                    description = "Gallery",
                    onClick = {
                        onImportImage()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Quick Note
                QuickAddOption(
                    icon = Icons.Default.Note,
                    label = "Quick Note",
                    description = "Text",
                    onClick = {
                        onQuickNote()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = true
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Quick Task (Future)
                QuickAddOption(
                    icon = Icons.Default.CheckCircle,
                    label = "Task List",
                    description = "Checklist",
                    onClick = {
                        onQuickTask()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = false
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Individual quick add option card.
 */
@Composable
private fun QuickAddOption(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    enabled: Boolean = true
) {
    val backgroundColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        isPrimary -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isPrimary -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )

            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f)
            )

            if (!enabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Coming Soon",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.5f)
                )
            }
        }
    }
}
