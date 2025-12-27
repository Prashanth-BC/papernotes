package com.example.notes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableNoteCard(
    onSwipeToFavorite: () -> Unit,
    onSwipeToDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swipe right - Favorite
                    onSwipeToFavorite()
                    false  // Don't dismiss, just trigger action
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swipe left - Delete
                    onSwipeToDelete()
                    true  // Dismiss after delete
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * 0.25f }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)  // Gold for favorite
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)  // Red for delete
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                },
                label = "background color"
            )

            val scale by animateFloatAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.75f else 1f,
                label = "icon scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    else -> Alignment.Center
                }
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Favorite",
                            modifier = Modifier.scale(scale),
                            tint = Color.White
                        )
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.scale(scale),
                            tint = Color.White
                        )
                    }
                    else -> {}
                }
            }
        },
        content = { content() }
    )
}
