package com.example.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.notes.data.TagEntity

/**
 * A compact chip component for displaying a tag.
 *
 * @param tag The tag entity to display
 * @param onRemove Optional callback when the remove (X) button is clicked
 * @param onClick Optional callback when the chip itself is clicked
 * @param modifier Modifier for the chip
 */
@Composable
fun TagChip(
    tag: TagEntity,
    onRemove: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val backgroundColor = Color(tag.color).copy(alpha = 0.2f)
    val contentColor = Color(tag.color)

    Row(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(16.dp)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(
                start = 12.dp,
                top = 6.dp,
                end = if (onRemove != null) 4.dp else 12.dp,
                bottom = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove tag",
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * A selectable tag chip variant for tag selection UI.
 *
 * @param tag The tag entity to display
 * @param isSelected Whether this tag is currently selected
 * @param onToggle Callback when the chip is clicked to toggle selection
 * @param modifier Modifier for the chip
 */
@Composable
fun SelectableTagChip(
    tag: TagEntity,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        Color(tag.color).copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = if (isSelected) {
        Color(tag.color)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onToggle,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = tag.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (tag.usageCount > 0) {
                Text(
                    text = tag.usageCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}
