package com.example.notes.ml

import android.util.Log
import kotlin.math.abs

/**
 * Post-Recognition Text Grouping
 *
 * Groups recognized characters into words based on:
 * 1. Spatial proximity (horizontal distance)
 * 2. Vertical alignment (same line)
 * 3. Recognition confidence
 *
 * This approach recognizes individual characters first, then groups them,
 * allowing for better handling of varied spacing and character-level confidence.
 */
object PostRecognitionGrouping {
    private const val TAG = "PostRecGrouping"

    /**
     * Recognized character with its bounding box and metadata
     */
    data class RecognizedChar(
        val text: String,
        val confidence: Float,
        val box: Array<FloatArray>
    ) {
        val minX = box.minOf { it[0] }
        val maxX = box.maxOf { it[0] }
        val minY = box.minOf { it[1] }
        val maxY = box.maxOf { it[1] }
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val width = maxX - minX
        val height = maxY - minY

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as RecognizedChar
            return text == other.text && confidence == other.confidence &&
                   box.contentDeepEquals(other.box)
        }

        override fun hashCode(): Int {
            var result = text.hashCode()
            result = 31 * result + confidence.hashCode()
            result = 31 * result + box.contentDeepHashCode()
            return result
        }
    }

    /**
     * Grouped word with combined text and metadata
     */
    data class RecognizedWord(
        val text: String,
        val confidence: Float,
        val box: Array<FloatArray>,
        val charCount: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as RecognizedWord
            return text == other.text && confidence == other.confidence &&
                   box.contentDeepEquals(other.box) && charCount == other.charCount
        }

        override fun hashCode(): Int {
            var result = text.hashCode()
            result = 31 * result + confidence.hashCode()
            result = 31 * result + box.contentDeepHashCode()
            result = 31 * result + charCount
            return result
        }
    }

    /**
     * Group recognized characters into words
     *
     * @param chars List of recognized characters with boxes
     * @param lineThreshold Max vertical distance for same line (default: auto)
     * @param wordSpacingRatio Horizontal spacing ratio for word breaks (default: 1.5)
     * @return List of grouped words
     */
    fun groupCharsIntoWords(
        chars: List<RecognizedChar>,
        lineThreshold: Float? = null,
        wordSpacingRatio: Float = 1.5f
    ): List<RecognizedWord> {
        if (chars.isEmpty()) return emptyList()

        Log.d(TAG, "Grouping ${chars.size} recognized characters into words")

        // Filter out empty/invalid characters
        val validChars = chars.filter { it.text.isNotBlank() && it.confidence > 0.1f }
        if (validChars.isEmpty()) {
            Log.w(TAG, "No valid characters to group")
            return emptyList()
        }

        // Step 1: Sort by reading order (top to bottom, left to right)
        val sorted = validChars.sortedWith(compareBy({ it.centerY }, { it.centerX }))

        // Step 2: Group into lines
        val lines = groupIntoLines(sorted, lineThreshold)
        Log.d(TAG, "Grouped into ${lines.size} lines")

        // Step 3: Group each line into words
        val words = mutableListOf<RecognizedWord>()
        for (line in lines) {
            val lineWords = groupLineIntoWords(line, wordSpacingRatio)
            words.addAll(lineWords)
        }

        Log.d(TAG, "Grouped into ${words.size} words from ${validChars.size} characters")
        return words
    }

    /**
     * Group characters into horizontal lines
     */
    private fun groupIntoLines(
        chars: List<RecognizedChar>,
        threshold: Float?
    ): List<List<RecognizedChar>> {
        if (chars.isEmpty()) return emptyList()

        // Calculate adaptive line threshold based on median character height
        val heights = chars.map { it.height }
        val medianHeight = heights.sorted()[heights.size / 2]
        val lineThreshold = threshold ?: (medianHeight * 0.5f)

        val lines = mutableListOf<MutableList<RecognizedChar>>()
        var currentLine = mutableListOf<RecognizedChar>()
        var currentLineY = chars[0].centerY

        for (char in chars) {
            // Check if character is on the same line
            if (abs(char.centerY - currentLineY) <= lineThreshold) {
                currentLine.add(char)
            } else {
                // Start new line
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = mutableListOf(char)
                currentLineY = char.centerY
            }
        }

        // Add last line
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        return lines
    }

    /**
     * Group characters in a line into words based on horizontal spacing
     */
    private fun groupLineIntoWords(
        line: List<RecognizedChar>,
        spacingRatio: Float
    ): List<RecognizedWord> {
        if (line.isEmpty()) return emptyList()

        // Sort line by X coordinate
        val sorted = line.sortedBy { it.centerX }

        // Calculate median character width for spacing threshold
        val widths = sorted.map { it.width }
        val medianWidth = widths.sorted()[widths.size / 2]
        val wordSpacingThreshold = medianWidth * spacingRatio

        val words = mutableListOf<MutableList<RecognizedChar>>()
        var currentWord = mutableListOf<RecognizedChar>()

        for (i in sorted.indices) {
            val char = sorted[i]

            if (currentWord.isEmpty()) {
                currentWord.add(char)
            } else {
                // Calculate horizontal gap between last character and current
                val lastChar = currentWord.last()
                val gap = char.minX - lastChar.maxX

                if (gap <= wordSpacingThreshold) {
                    // Same word
                    currentWord.add(char)
                } else {
                    // New word - save current word and start new one
                    words.add(currentWord)
                    currentWord = mutableListOf(char)
                }
            }
        }

        // Add last word
        if (currentWord.isNotEmpty()) {
            words.add(currentWord)
        }

        // Convert character groups to RecognizedWord objects
        return words.mapNotNull { chars ->
            if (chars.isEmpty()) null
            else combineCharsIntoWord(chars)
        }
    }

    /**
     * Combine multiple characters into a single word
     */
    private fun combineCharsIntoWord(chars: List<RecognizedChar>): RecognizedWord {
        val text = chars.joinToString("") { it.text }
        val avgConfidence = chars.map { it.confidence }.average().toFloat()

        // Create bounding box around all characters
        val minX = chars.minOf { it.minX }
        val maxX = chars.maxOf { it.maxX }
        val minY = chars.minOf { it.minY }
        val maxY = chars.maxOf { it.maxY }

        val box = arrayOf(
            floatArrayOf(minX, minY),
            floatArrayOf(maxX, minY),
            floatArrayOf(maxX, maxY),
            floatArrayOf(minX, maxY)
        )

        return RecognizedWord(
            text = text,
            confidence = avgConfidence,
            box = box,
            charCount = chars.size
        )
    }

    /**
     * Advanced grouping with confidence-based merging
     *
     * Merges low-confidence characters with neighbors if they're very close
     */
    fun groupWithConfidenceMerging(
        chars: List<RecognizedChar>,
        lowConfidenceThreshold: Float = 0.5f,
        mergeDistanceRatio: Float = 0.8f
    ): List<RecognizedWord> {
        if (chars.isEmpty()) return emptyList()

        // First do standard grouping
        val standardWords = groupCharsIntoWords(chars)

        // Then check for low-confidence single characters that should be merged
        val merged = mutableListOf<RecognizedWord>()
        var i = 0

        while (i < standardWords.size) {
            val word = standardWords[i]

            // Check if this is a low-confidence single character
            if (word.charCount == 1 && word.confidence < lowConfidenceThreshold) {
                // Try to merge with previous or next word
                val prev = merged.lastOrNull()
                val next = standardWords.getOrNull(i + 1)

                val mergedWithPrev = prev != null && shouldMerge(prev, word, mergeDistanceRatio)
                val mergedWithNext = next != null && shouldMerge(word, next, mergeDistanceRatio)

                when {
                    mergedWithPrev -> {
                        // Merge with previous word
                        merged[merged.lastIndex] = mergeWords(prev!!, word)
                    }
                    mergedWithNext -> {
                        // Merge with next word and skip it
                        merged.add(mergeWords(word, next!!))
                        i++ // Skip next word
                    }
                    else -> {
                        // Keep as is
                        merged.add(word)
                    }
                }
            } else {
                merged.add(word)
            }

            i++
        }

        Log.d(TAG, "Confidence-based merging: ${standardWords.size} -> ${merged.size} words")
        return merged
    }

    /**
     * Check if two words should be merged based on distance
     */
    private fun shouldMerge(word1: RecognizedWord, word2: RecognizedWord, distanceRatio: Float): Boolean {
        val word1Right = word1.box.maxOf { it[0] }
        val word2Left = word2.box.minOf { it[0] }
        val gap = word2Left - word1Right

        val avgWidth = ((word1.box.maxOf { it[0] } - word1.box.minOf { it[0] }) +
                       (word2.box.maxOf { it[0] } - word2.box.minOf { it[0] })) / 2f

        return gap < avgWidth * distanceRatio
    }

    /**
     * Merge two words into one
     */
    private fun mergeWords(word1: RecognizedWord, word2: RecognizedWord): RecognizedWord {
        val combinedText = word1.text + word2.text
        val avgConfidence = (word1.confidence * word1.charCount + word2.confidence * word2.charCount) /
                           (word1.charCount + word2.charCount)

        val minX = kotlin.math.min(word1.box.minOf { it[0] }, word2.box.minOf { it[0] })
        val maxX = kotlin.math.max(word1.box.maxOf { it[0] }, word2.box.maxOf { it[0] })
        val minY = kotlin.math.min(word1.box.minOf { it[1] }, word2.box.minOf { it[1] })
        val maxY = kotlin.math.max(word1.box.maxOf { it[1] }, word2.box.maxOf { it[1] })

        val box = arrayOf(
            floatArrayOf(minX, minY),
            floatArrayOf(maxX, minY),
            floatArrayOf(maxX, maxY),
            floatArrayOf(minX, maxY)
        )

        return RecognizedWord(
            text = combinedText,
            confidence = avgConfidence,
            box = box,
            charCount = word1.charCount + word2.charCount
        )
    }
}
