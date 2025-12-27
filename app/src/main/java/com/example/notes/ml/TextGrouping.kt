package com.example.notes.ml

import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Text grouping algorithms to combine character-level boxes into word/line-level boxes
 *
 * Solves the problem where color-based detection finds individual characters
 * instead of complete words.
 */
object TextGrouping {
    private const val TAG = "TextGrouping"

    /**
     * Group character boxes into word-level boxes
     *
     * Algorithm:
     * 1. Sort boxes by Y coordinate (top to bottom)
     * 2. Group boxes into lines based on Y overlap
     * 3. Within each line, group boxes into words based on horizontal distance
     * 4. Merge grouped boxes into single bounding boxes
     *
     * @param boxes Array of character-level boxes
     * @param lineThreshold Maximum vertical distance to be considered same line (default: box height * 0.3)
     * @param wordThreshold Maximum horizontal distance to be considered same word (default: box width * 1.5)
     * @return Array of word-level boxes
     */
    fun groupIntoWords(
        boxes: Array<Array<FloatArray>>,
        lineThreshold: Float? = null,
        wordThreshold: Float? = null
    ): Array<Array<FloatArray>> {
        if (boxes.isEmpty()) return boxes

        Log.d(TAG, "Grouping ${boxes.size} character boxes into words")

        // Step 1: Convert to BoundingBox objects for easier manipulation
        val boundingBoxes = boxes.map { box ->
            val minX = box.minOf { it[0] }
            val maxX = box.maxOf { it[0] }
            val minY = box.minOf { it[1] }
            val maxY = box.maxOf { it[1] }
            BoundingBox(minX, minY, maxX, maxY, box)
        }

        // Step 2: Group into lines
        val lines = groupIntoLines(boundingBoxes, lineThreshold)
        Log.d(TAG, "Grouped into ${lines.size} lines")

        // Step 3: Group each line into words
        val wordBoxes = mutableListOf<Array<FloatArray>>()
        for (line in lines) {
            val words = groupLineIntoWords(line, wordThreshold)
            wordBoxes.addAll(words)
        }

        Log.d(TAG, "Final result: ${wordBoxes.size} word boxes from ${boxes.size} character boxes")
        return wordBoxes.toTypedArray()
    }

    /**
     * Group bounding boxes into horizontal lines
     */
    private fun groupIntoLines(
        boxes: List<BoundingBox>,
        threshold: Float?
    ): List<List<BoundingBox>> {
        if (boxes.isEmpty()) return emptyList()

        // Calculate adaptive line threshold based on median box height
        val heights = boxes.map { it.height() }
        val medianHeight = heights.sorted()[heights.size / 2]
        val lineThreshold = threshold ?: (medianHeight * 0.3f)

        // Sort by Y coordinate
        val sorted = boxes.sortedBy { it.minY }

        val lines = mutableListOf<MutableList<BoundingBox>>()
        var currentLine = mutableListOf<BoundingBox>()
        var currentLineY = sorted[0].centerY()

        for (box in sorted) {
            val boxCenterY = box.centerY()

            // Check if box belongs to current line (vertical overlap/proximity)
            if (abs(boxCenterY - currentLineY) <= lineThreshold ||
                verticalOverlap(currentLine, box) > 0.5f) {
                currentLine.add(box)
                // Update line center Y as average
                currentLineY = currentLine.map { it.centerY() }.average().toFloat()
            } else {
                // Start new line
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = mutableListOf(box)
                currentLineY = box.centerY()
            }
        }

        // Add last line
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        return lines
    }

    /**
     * Calculate vertical overlap ratio between a line and a box
     */
    private fun verticalOverlap(line: List<BoundingBox>, box: BoundingBox): Float {
        if (line.isEmpty()) return 0f

        val lineMinY = line.minOf { it.minY }
        val lineMaxY = line.maxOf { it.maxY }

        val overlapStart = max(lineMinY, box.minY)
        val overlapEnd = min(lineMaxY, box.maxY)
        val overlap = max(0f, overlapEnd - overlapStart)

        val lineHeight = lineMaxY - lineMinY
        val boxHeight = box.maxY - box.minY

        return overlap / min(lineHeight, boxHeight)
    }

    /**
     * Group boxes within a line into words based on horizontal spacing
     */
    private fun groupLineIntoWords(
        line: List<BoundingBox>,
        threshold: Float?
    ): List<Array<FloatArray>> {
        if (line.isEmpty()) return emptyList()

        // Sort line boxes by X coordinate (left to right)
        val sorted = line.sortedBy { it.minX }

        // Calculate adaptive word threshold based on median box width
        val widths = sorted.map { it.width() }
        val medianWidth = widths.sorted()[widths.size / 2]
        val wordThreshold = threshold ?: (medianWidth * 1.5f)

        val words = mutableListOf<MutableList<BoundingBox>>()
        var currentWord = mutableListOf<BoundingBox>()

        for (i in sorted.indices) {
            val box = sorted[i]

            if (currentWord.isEmpty()) {
                currentWord.add(box)
            } else {
                // Calculate horizontal gap between last box in word and current box
                val lastBox = currentWord.last()
                val gap = box.minX - lastBox.maxX

                if (gap <= wordThreshold) {
                    // Same word
                    currentWord.add(box)
                } else {
                    // New word
                    words.add(currentWord)
                    currentWord = mutableListOf(box)
                }
            }
        }

        // Add last word
        if (currentWord.isNotEmpty()) {
            words.add(currentWord)
        }

        // Merge each word's boxes into a single bounding box
        return words.map { word ->
            mergeBoxes(word)
        }
    }

    /**
     * Merge multiple bounding boxes into a single box
     */
    private fun mergeBoxes(boxes: List<BoundingBox>): Array<FloatArray> {
        val minX = boxes.minOf { it.minX }
        val maxX = boxes.maxOf { it.maxX }
        val minY = boxes.minOf { it.minY }
        val maxY = boxes.maxOf { it.maxY }

        // Create 4-point box (clockwise from top-left)
        return arrayOf(
            floatArrayOf(minX, minY),
            floatArrayOf(maxX, minY),
            floatArrayOf(maxX, maxY),
            floatArrayOf(minX, maxY)
        )
    }

    /**
     * Internal bounding box representation for easier manipulation
     */
    private data class BoundingBox(
        val minX: Float,
        val minY: Float,
        val maxX: Float,
        val maxY: Float,
        val originalBox: Array<FloatArray>
    ) {
        fun width() = maxX - minX
        fun height() = maxY - minY
        fun centerX() = (minX + maxX) / 2
        fun centerY() = (minY + maxY) / 2

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as BoundingBox
            return minX == other.minX && minY == other.minY &&
                   maxX == other.maxX && maxY == other.maxY
        }

        override fun hashCode(): Int {
            var result = minX.hashCode()
            result = 31 * result + minY.hashCode()
            result = 31 * result + maxX.hashCode()
            result = 31 * result + maxY.hashCode()
            return result
        }
    }

    /**
     * Advanced grouping using dilation-based approach
     * This groups text boxes by dilating them and finding connected components
     *
     * @param boxes Array of character-level boxes
     * @param dilationX Horizontal dilation factor (default: 0.5 = 50% of box width)
     * @param dilationY Vertical dilation factor (default: 0.3 = 30% of box height)
     * @return Array of grouped boxes
     */
    fun groupByDilation(
        boxes: Array<Array<FloatArray>>,
        dilationX: Float = 0.5f,
        dilationY: Float = 0.3f
    ): Array<Array<FloatArray>> {
        if (boxes.isEmpty()) return boxes

        Log.d(TAG, "Grouping ${boxes.size} boxes using dilation method")

        // Convert to BoundingBox objects
        val boundingBoxes = boxes.map { box ->
            val minX = box.minOf { it[0] }
            val maxX = box.maxOf { it[0] }
            val minY = box.minOf { it[1] }
            val maxY = box.maxOf { it[1] }
            BoundingBox(minX, minY, maxX, maxY, box)
        }.toMutableList()

        // Dilate boxes
        val dilatedBoxes = boundingBoxes.map { box ->
            val width = box.width()
            val height = box.height()
            val expandX = width * dilationX
            val expandY = height * dilationY

            BoundingBox(
                box.minX - expandX,
                box.minY - expandY,
                box.maxX + expandX,
                box.maxY + expandY,
                box.originalBox
            )
        }

        // Find connected components (overlapping dilated boxes)
        val groups = mutableListOf<MutableList<BoundingBox>>()
        val visited = BooleanArray(dilatedBoxes.size)

        for (i in dilatedBoxes.indices) {
            if (visited[i]) continue

            val group = mutableListOf<BoundingBox>()
            val queue = mutableListOf(i)
            visited[i] = true

            while (queue.isNotEmpty()) {
                val current = queue.removeAt(0)
                group.add(boundingBoxes[current]) // Use original box

                // Find overlapping boxes
                for (j in dilatedBoxes.indices) {
                    if (!visited[j] && boxesOverlap(dilatedBoxes[current], dilatedBoxes[j])) {
                        visited[j] = true
                        queue.add(j)
                    }
                }
            }

            groups.add(group)
        }

        Log.d(TAG, "Dilation grouping: ${groups.size} groups from ${boxes.size} boxes")

        // Merge each group into a single box
        return groups.map { group ->
            mergeBoxes(group)
        }.toTypedArray()
    }

    /**
     * Check if two bounding boxes overlap
     */
    private fun boxesOverlap(box1: BoundingBox, box2: BoundingBox): Boolean {
        return !(box1.maxX < box2.minX || box2.maxX < box1.minX ||
                box1.maxY < box2.minY || box2.maxY < box1.minY)
    }
}
