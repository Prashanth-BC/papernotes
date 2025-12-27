package com.example.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Predefined note colors (Google Keep style).
 * Each color is shown with 15% opacity on note cards.
 */
object NoteColors {
    val Red = Color(0xFFE57373)
    val Pink = Color(0xFFF06292)
    val Purple = Color(0xFFBA68C8)
    val DeepPurple = Color(0xFF9575CD)
    val Indigo = Color(0xFF7986CB)
    val Blue = Color(0xFF64B5F6)
    val Cyan = Color(0xFF4DD0E1)
    val Teal = Color(0xFF4DB6AC)
    val Green = Color(0xFF81C784)
    val LightGreen = Color(0xFFAED581)
    val Amber = Color(0xFFFFD54F)
    val Orange = Color(0xFFFFB74D)
    val DeepOrange = Color(0xFFFF8A65)
    val Brown = Color(0xFFA1887F)
    val BlueGray = Color(0xFF90A4AE)
    val Gray = Color(0xFFBDBDBD)

    val all = listOf(
        Red, Pink, Purple, DeepPurple, Indigo, Blue, Cyan, Teal,
        Green, LightGreen, Amber, Orange, DeepOrange, Brown, BlueGray, Gray
    )

    val names = listOf(
        "Red", "Pink", "Purple", "Deep Purple", "Indigo", "Blue", "Cyan", "Teal",
        "Green", "Light Green", "Amber", "Orange", "Deep Orange", "Brown", "Blue Gray", "Gray"
    )
}

/**
 * Color picker sheet for selecting note colors.
 *
 * @param currentColor Currently selected color (argb Int), null for no color
 * @param onColorSelected Callback when a color is selected
 * @param onDismiss Callback when sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteColorPickerSheet(
    currentColor: Int?,
    onColorSelected: (Int?) -> Unit,
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
                text = "Note Color",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // No color option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onColorSelected(null) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 2.dp,
                            color = if (currentColor == null)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentColor == null) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "No Color (Default)",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Color grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(NoteColors.all) { color ->
                    val colorArgb = color.toArgb()
                    val isSelected = currentColor == colorArgb

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(colorArgb) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Compact inline color picker (horizontal row of colors).
 * Useful for including in dialogs without a separate sheet.
 */
@Composable
fun CompactColorPicker(
    currentColor: Int?,
    onColorSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Color",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // First row: No color + first 3 colors
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // No color option
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 2.dp,
                        color = if (currentColor == null)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(null) },
                contentAlignment = Alignment.Center
            ) {
                if (currentColor == null) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // First 7 colors
            NoteColors.all.take(7).forEach { color ->
                val colorArgb = color.toArgb()
                val isSelected = currentColor == colorArgb

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(colorArgb) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Second row: Remaining colors
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            NoteColors.all.drop(7).forEach { color ->
                val colorArgb = color.toArgb()
                val isSelected = currentColor == colorArgb

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(colorArgb) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
