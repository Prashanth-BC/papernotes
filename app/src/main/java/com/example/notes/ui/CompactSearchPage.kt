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
        color = Color(0xFF0D1117)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF8B949E)
                    )
                }

                Text(
                    text = "Search Results",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF58A6FF)
                )

                IconButton(onClick = { showThresholdControls = !showThresholdControls }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Threshold Controls",
                        tint = if (showThresholdControls) Color(0xFF58A6FF) else Color(0xFF8B949E)
                    )
                }
            }


            // Threshold Controls (expandable)
            if (showThresholdControls) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF161B22)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Threshold Controls",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF58A6FF)
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
                            color = Color(0xFF3FB950)
                        )

                        ThresholdSlider(
                            label = "TrOCR Visual",
                            value = trocrThreshold,
                            onValueChange = {
                                trocrThreshold = it
                                prefs.edit { putFloat("trocr_threshold", it) }
                                onThresholdsChanged(clipThreshold, trocrThreshold, mlkitThreshold, colorbasedThreshold)
                            },
                            color = Color(0xFF58A6FF)
                        )

                        ThresholdSlider(
                            label = "ML Kit Text",
                            value = mlkitThreshold,
                            onValueChange = {
                                mlkitThreshold = it
                                prefs.edit { putFloat("mlkit_threshold", it) }
                                onThresholdsChanged(clipThreshold, trocrThreshold, mlkitThreshold, colorbasedThreshold)
                            },
                            color = Color(0xFFD29922)
                        )

                        ThresholdSlider(
                            label = "ColorBased Text",
                            value = colorbasedThreshold,
                            onValueChange = {
                                colorbasedThreshold = it
                                prefs.edit { putFloat("colorbased_threshold", it) }
                                onThresholdsChanged(clipThreshold, trocrThreshold, mlkitThreshold, colorbasedThreshold)
                            },
                            color = Color(0xFFFF7B72)
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFF30363D))
            }

            // Results Stats Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0D1117)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${filteredResults.size} matches",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E)
                    )
                    Text(
                        text = "of ${searchResults.size} total",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF6E7681)
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
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = Color(0xFF6E7681)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Try adjusting the thresholds",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFF6E7681)
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
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFF8B949E)
            )
            Text(
                text = String.format("%.2f", value),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
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
                inactiveTrackColor = Color(0xFF30363D)
            )
        )
    }
}

@Composable
fun CompactResultCard(result: ScannerManager.SearchResult) {
    Surface(
        color = Color(0xFF161B22),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Surface(
                modifier = Modifier.size(80.dp),
                color = Color(0xFF0D1117),
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
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF58A6FF),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format("%.3f", result.score),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF3FB950),
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
                        ScoreChip("C", it, Color(0xFF3FB950))
                    }
                    result.trocrScore?.let {
                        ScoreChip("T", it, Color(0xFF58A6FF))
                    }
                    result.mlKitTextScore?.let {
                        ScoreChip("M", it, Color(0xFFD29922))
                    }
                    result.colorBasedTextScore?.let {
                        ScoreChip("H", it, Color(0xFFFF7B72))
                    }
                }

                // Text Preview
                result.note.mlKitText?.take(60)?.let { text ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"${text.replace("\n", " ")}...\"",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF6E7681),
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
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.height(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format("%.2f", score),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = color
            )
        }
    }
}
