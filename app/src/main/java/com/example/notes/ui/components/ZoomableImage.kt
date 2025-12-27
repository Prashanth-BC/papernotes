package com.example.notes.ui.components

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * An image component that supports pinch-to-zoom and pan gestures.
 *
 * @param model The image to display (path, URL, etc.)
 * @param contentDescription Accessibility description
 * @param modifier Modifier for the component
 */
@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Update scale
                    scale = (scale * zoom).coerceIn(1f, 5f)

                    // If zoomed in, allow panning
                    if (scale > 1f) {
                        val newOffset = offset + pan

                        // Calculate max offset to prevent panning too far
                        val maxX = (size.width * (scale - 1)) / 2
                        val maxY = (size.height * (scale - 1)) / 2

                        offset = Offset(
                            x = newOffset.x.coerceIn(-maxX, maxX),
                            y = newOffset.y.coerceIn(-maxY, maxY)
                        )
                    } else {
                        // Reset offset when zoomed out
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}
