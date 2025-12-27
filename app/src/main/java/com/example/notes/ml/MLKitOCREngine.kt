package com.example.notes.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.runBlocking

/**
 * ML Kit TextRecognition OCR Engine Implementation
 *
 * Uses Google's ML Kit Text Recognition API
 * Best for: Quick on-device OCR for printed text, supports multiple languages
 * Advantages:
 *   - Fast and accurate for printed text
 *   - Minimal setup required
 *   - Works offline
 *   - Regular updates from Google
 */
class MLKitOCREngine(private val context: Context) : OCREngine {
    private val TAG = "MLKitOCREngine"
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun recognizeText(bitmap: Bitmap): OCREngine.OCRResult {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            
            // ML Kit uses Tasks API - we need to block since our interface is synchronous
            val result = runBlocking {
                recognizer.process(image).await()
            }

            val textBoxes = mutableListOf<OCREngine.TextBox>()
            var allText = StringBuilder()
            var totalConfidence = 0f
            var blockCount = 0

            // Process text blocks
            for (block in result.textBlocks) {
                allText.append(block.text).append("\n")
                
                // Calculate average confidence for this block
                var lineConfidence = 0f
                var lineCount = 0
                
                for (line in block.lines) {
                    lineConfidence += line.confidence ?: 0.5f
                    lineCount++
                    
                    // Convert bounding box to points array
                    val boundingBox = line.boundingBox
                    val points = if (boundingBox != null) {
                        arrayOf(
                            floatArrayOf(boundingBox.left.toFloat(), boundingBox.top.toFloat()),
                            floatArrayOf(boundingBox.right.toFloat(), boundingBox.top.toFloat()),
                            floatArrayOf(boundingBox.right.toFloat(), boundingBox.bottom.toFloat()),
                            floatArrayOf(boundingBox.left.toFloat(), boundingBox.bottom.toFloat())
                        )
                    } else {
                        arrayOf(
                            floatArrayOf(0f, 0f),
                            floatArrayOf(0f, 0f),
                            floatArrayOf(0f, 0f),
                            floatArrayOf(0f, 0f)
                        )
                    }
                    
                    textBoxes.add(
                        OCREngine.TextBox(
                            text = line.text,
                            confidence = line.confidence ?: 0.5f,
                            points = points
                        )
                    )
                }
                
                val avgLineConfidence = if (lineCount > 0) lineConfidence / lineCount else 0.5f
                totalConfidence += avgLineConfidence
                blockCount++
            }

            val overallConfidence = if (blockCount > 0) totalConfidence / blockCount else 0f
            val finalText = allText.toString().trim()

            Log.d(TAG, "ML Kit OCR recognized ${textBoxes.size} text lines with confidence $overallConfidence")
            Log.d(TAG, "Extracted text length: ${finalText.length}")

            OCREngine.OCRResult(
                text = finalText,
                confidence = overallConfidence,
                boxes = textBoxes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in ML Kit text recognition", e)
            OCREngine.OCRResult("", 0f, emptyList())
        }
    }

    override fun release() {
        try {
            recognizer.close()
            Log.d(TAG, "ML Kit recognizer closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ML Kit recognizer", e)
        }
    }
}
