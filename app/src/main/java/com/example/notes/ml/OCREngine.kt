package com.example.notes.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

/**
 * Unified OCR Engine interface for papernotes
 * Uses color-based HSV detection for handwritten text
 */
interface OCREngine {
    fun recognizeText(bitmap: Bitmap): OCRResult
    fun release()

    data class OCRResult(
        val text: String,
        val confidence: Float,
        val boxes: List<TextBox> = emptyList()
    )

    data class TextBox(
        val text: String,
        val confidence: Float,
        val points: Array<FloatArray>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TextBox
            if (text != other.text) return false
            if (confidence != other.confidence) return false
            if (!points.contentDeepEquals(other.points)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = text.hashCode()
            result = 31 * result + confidence.hashCode()
            result = 31 * result + points.contentDeepHashCode()
            return result
        }
    }
}

/**
 * Color-Based OCR Engine Implementation
 *
 * Uses HSV color-based text detection (no neural network needed for detection)
 * Best for: Handwritten text, colored text on contrasting backgrounds
 */
class ColorBasedOCREngine(
    context: Context,
    config: ColorBasedOCRConfig = ColorBasedOCRConfig()
) : OCREngine {
    private val TAG = "ColorBasedOCREngine"
    private val colorBasedOCR = ColorBasedOCR(context, config)

    override fun recognizeText(bitmap: Bitmap): OCREngine.OCRResult {
        return try {
            val result = colorBasedOCR.recognize(bitmap)

            val textBoxes = mutableListOf<OCREngine.TextBox>()
            for (i in result.boxes.indices) {
                if (i < result.texts.size && i < result.scores.size) {
                    textBoxes.add(
                        OCREngine.TextBox(
                            text = result.texts[i],
                            confidence = result.scores[i],
                            points = result.boxes[i]
                        )
                    )
                }
            }

            Log.d(TAG, "ColorBasedOCR recognized ${textBoxes.size} text boxes")

            OCREngine.OCRResult(
                text = result.text,
                confidence = result.confidence,
                boxes = textBoxes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in ColorBasedOCR recognition", e)
            OCREngine.OCRResult("", 0f, emptyList())
        }
    }

    override fun release() {
        colorBasedOCR.close()
    }
}

/**
 * Factory for creating OCR engines
 */
object OCREngineFactory {

    /**
     * Create default OCR engine (ColorBased)
     */
    fun create(context: Context): OCREngine {
        return ColorBasedOCREngine(context)
    }

    /**
     * Create ColorBasedOCR engine with custom configuration
     */
    fun createColorBasedOCR(context: Context, config: ColorBasedOCRConfig): OCREngine {
        return ColorBasedOCREngine(context, config)
    }

    /**
     * Create ColorBasedOCR engine optimized for handwriting
     * Uses post-recognition character grouping for better accuracy
     */
    fun createHandwritingScanner(context: Context): OCREngine {
        val config = ColorBasedOCRConfig(
            minConfidence = 0.3f,
            minWidth = 10,
            minHeight = 6,
            maxWidth = 1500,
            maxHeight = 300,
            minAspectRatio = 0.3f,
            maxAspectRatio = 25f,
            useGPU = true,
            // Post-recognition grouping
            usePostRecognitionGrouping = true,
            wordSpacingRatio = 1.5f
        )
        return ColorBasedOCREngine(context, config)
    }

    /**
     * Create ColorBasedOCR engine optimized for printed text
     * Uses post-recognition character grouping for better accuracy
     */
    fun createPrintedTextScanner(context: Context): OCREngine {
        val config = ColorBasedOCRConfig(
            minConfidence = 0.4f,
            minWidth = 15,
            minHeight = 8,
            maxWidth = 1000,
            maxHeight = 200,
            minAspectRatio = 0.5f,
            maxAspectRatio = 20f,
            useGPU = true,
            // Post-recognition grouping
            usePostRecognitionGrouping = true,
            wordSpacingRatio = 1.5f
        )
        return ColorBasedOCREngine(context, config)
    }

    /**
     * Create ML Kit TextRecognition engine
     * Best for: Quick on-device OCR for printed text, supports multiple languages
     */
    fun createMLKitOCR(context: Context): OCREngine {
        return MLKitOCREngine(context)
    }
}
