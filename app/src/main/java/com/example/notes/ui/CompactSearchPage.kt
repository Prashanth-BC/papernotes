package com.example.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.core.content.edit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactSearchPage(
    searchResults: List<ScannerManager.SearchResult>,
    isSearching: Boolean,
    onDismiss: () -> Unit,
    onThresholdsChanged: (clip: Float, trocr: Float, mlkit: Float, colorbased: Float) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }

    var clipThreshold by remember {
        mutableFloatStateOf(prefs.getFloat("clip_threshold", 0.2f))
    }
    var trocrThreshold by remember {
        mutableFloatStateOf(prefs.getFloat("trocr_threshold", 0.2f))
    }
    var mlkitThreshold by remember {
        mutableFloatStateOf(prefs.getFloat("mlkit_threshold", 0.2f))
    }
    var colorbasedThreshold by remember {
        mutableFloatStateOf(prefs.getFloat("colorbased_threshold", 0.2f))
    }
    var showThresholdControls by remember { mutableStateOf(false) }

    // Filter results based on current thresholds
    val filteredResults = remember(searchResults, clipThreshold, trocrThreshold, mlkitThreshold, colorbasedThreshold) {
        searchResults.filter { result ->
            val passesClip = result.clipScore?.let { it < clipThreshold } ?: true
            val passesTrocr = result.trocrScore?.let { it < trocrThreshold } ?: true
            val passesMlkit = result.mlKitTextScore?.let { it < mlkitThreshold } ?: true
            val passesColor = result.colorBasedTextScore?.let { it < colorbasedThreshold } ?: true
            passesClip || passesTrocr || passesMlkit || passesColor
        }.sortedBy { it.score }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Search Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(onClick = { showThresholdControls = !showThresholdControls }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Threshold Controls",
                        tint = if (showThresholdControls) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }


            // Threshold Controls (expandable)
            if (showThresholdControls) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Threshold Controls",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        ThresholdSlider(
                            label = "CLIP Image",
                            value = clipThreshold,
                            onValueChange = {
                                clipThreshold = it
                                prefs.edit { putFloat("clip_threshold", it) }
                                onThresholdsChanged(clipThreshold, trocrThreshold, mlkitThreshold, colorbasedThreshold)
                            },
                            color = Color(0xFF4CAF50)  // Success green
                        )

                        ThresholdSlider(
                            label = "TrOCR Visual",
                            value = trocrThreshold,
                            onValueChange = {
                                trocrThreshold = it
                                prefs.edit { putFloat("trocr_threshold", it) }
                                onThresholdsChanged(clipThreshold, trocrThreshold, mlkitThreshold, colorbasedThreshold)
                            },
                            color = MaterialTheme.colorScheme.secondary
                        )

                        ThresholdSlider(
                            label = "ML Kit Text",
                            value = mlkitThreshold,
                            onValueChange = {
                                mlkitThreshold = it
                                prefs.edit { putFloat("mlkit_threshold", it) }
                                onThresholdsChanged(clipThreshold, trocrThreshold, mlkitThreshold, colorbasedThreshold)
                            },
                            color = Color(0xFFFF9800)  // Warning orange
                        )

                        ThresholdSlider(
                            label = "ColorBased Text",
                            value = colorbasedThreshold,
                            onValueChange = {
                                colorbasedThreshold = it
                                prefs.edit { putFloat("colorbased_threshold", it) }
                                onThresholdsChanged(clipThreshold, trocrThreshold, mlkitThreshold, colorbasedThreshold)
                            },
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }

            // Results Stats Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${filteredResults.size} matches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "of ${searchResults.size} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Results List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                if (!isSearching && filteredResults.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No matches found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Try adjusting the thresholds",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                items(filteredResults) { result ->
                    CompactResultCard(result)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Bottom padding
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun ThresholdSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Color
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format("%.2f", value),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.05f..0.5f,
            steps = 44,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
fun CompactResultCard(result: ScannerManager.SearchResult) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Surface(
                modifier = Modifier.size(80.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                AsyncImage(
                    model = result.note.imagePath,
                    contentDescription = "Note thumbnail",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Title and Overall Score
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = result.note.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format("%.3f", result.score),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF4CAF50),  // Success green
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Individual Scores (compact)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    result.clipScore?.let {
                        ScoreChip("C", it, Color(0xFF4CAF50))  // Success green
                    }
                    result.trocrScore?.let {
                        ScoreChip("T", it, MaterialTheme.colorScheme.secondary)
                    }
                    result.mlKitTextScore?.let {
                        ScoreChip("M", it, Color(0xFFFF9800))  // Warning orange
                    }
                    result.colorBasedTextScore?.let {
                        ScoreChip("H", it, MaterialTheme.colorScheme.error)
                    }
                }

                // Text Preview
                result.note.mlKitText?.take(60)?.let { text ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"${text.replace("\n", " ")}...\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
fun ScoreChip(label: String, score: Double, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.height(22.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format("%.2f", score),
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}
