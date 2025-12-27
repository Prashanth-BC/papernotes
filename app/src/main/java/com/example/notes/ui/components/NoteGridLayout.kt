package com.example.notes.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.notes.data.NoteEntity

/**
 * Grid layout style options
 */
enum class GridLayoutStyle {
    UNIFORM,    // Regular grid with fixed height (Kindle style)
    STAGGERED   // Masonry/staggered grid (Google Keep style)
}

/**
 * Adaptive grid layout that switches between uniform and staggered based on preference.
 * Inspired by Google Keep's masonry layout and Amazon Kindle's uniform grid.
 *
 * @param notes List of notes to display
 * @param layoutStyle Style of grid layout
 * @param modifier Modifier for the grid
 * @param content Composable for each note item
 */
@Composable
fun AdaptiveNoteGrid(
    notes: List<NoteEntity>,
    layoutStyle: GridLayoutStyle = GridLayoutStyle.STAGGERED,
    modifier: Modifier = Modifier,
    content: @Composable (NoteEntity) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    // Adaptive column count based on screen width
    val gridColumns = when {
        screenWidthDp < 600 -> 2   // Phone portrait: 2 columns
        screenWidthDp < 840 -> 3   // Phone landscape/small tablet: 3 columns
        else -> 4                   // Large tablet: 4 columns
    }

    when (layoutStyle) {
        GridLayoutStyle.STAGGERED -> {
            // Google Keep style masonry layout
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(gridColumns),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp  // Extra padding for bottom nav
                ),
                modifier = modifier
            ) {
                items(
                    items = notes,
                    key = { it.id }
                ) { note ->
                    content(note)
                }
            }
        }
        GridLayoutStyle.UNIFORM -> {
            // Traditional uniform grid (current implementation)
            // Note: This would use LazyVerticalGrid for fixed heights
            // Keeping staggered for now as it's more flexible
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(gridColumns),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 16.dp,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp
                ),
                modifier = modifier
            ) {
                items(
                    items = notes,
                    key = { it.id }
                ) { note ->
                    content(note)
                }
            }
        }
    }
}
