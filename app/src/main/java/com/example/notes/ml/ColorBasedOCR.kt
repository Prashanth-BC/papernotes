package com.example.notes.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * Color-Based OCR Engine
 *
 * Complete OCR pipeline using:
 * 1. HSV color-based text detection (no neural network needed)
 * 2. ONNX recognition model for text recognition
 *
 * Best for: Handwritten text, colored text on contrasting backgrounds
 */
class ColorBasedOCR(
    private val context: Context,
    private val config: ColorBasedOCRConfig = ColorBasedOCRConfig()
) {
    private val TAG = "ColorBasedOCR"

    private val textDetector: ColorBasedTextDetector
    private val textRecognizer: TextRecognizer

    init {
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed!")
        } else {
            Log.d(TAG, "OpenCV initialized successfully")
        }

        textDetector = ColorBasedTextDetector(
            minWidth = config.minWidth,
            minHeight = config.minHeight,
            maxWidth = config.maxWidth,
            maxHeight = config.maxHeight,
            minAspectRatio = config.minAspectRatio,
            maxAspectRatio = config.maxAspectRatio,
            useGPU = config.useGPU,
            enableGrouping = config.enableGrouping,
            groupingMethod = config.groupingMethod
        )

        textRecognizer = TextRecognizer(
            context = context,
            modelPath = config.recModelPath,
            dictPath = config.dictPath,
            useGPU = config.useGPU
        )

        Log.d(TAG, "ColorBasedOCR initialized")
    }

    /**
     * Run OCR on input image with post-recognition grouping
     *
     * Pipeline:
     * 1. Detect character-level boxes (color-based)
     * 2. Recognize each character individually
     * 3. Group recognized characters into words
     *
     * @param bitmap Input image
     * @return OCRResult with recognized text, boxes, and scores
     */
    fun recognize(bitmap: Bitmap): OCRResult {
        try {
            Log.d(TAG, "Starting OCR on ${bitmap.width}x${bitmap.height} image")

            // Step 1: Detect character-level boxes (grouping disabled in detector)
            val charBoxes = textDetector.detectTextRegions(bitmap)

            if (charBoxes.isEmpty()) {
                Log.w(TAG, "No text regions detected")
                return OCRResult()
            }

            Log.d(TAG, "Detected ${charBoxes.size} character boxes")

            // Step 2: Recognize each character individually
            val recognizedChars = mutableListOf<PostRecognitionGrouping.RecognizedChar>()

            for (box in charBoxes) {
                val croppedImg = ImageUtils.getRotateCropImage(bitmap, box)
                val (text, score) = textRecognizer.recognize(croppedImg)

                if (text.trim().isNotEmpty() && score >= config.minConfidence) {
                    recognizedChars.add(
                        PostRecognitionGrouping.RecognizedChar(
                            text = text.trim(),
                            confidence = score,
                            box = box
                        )
                    )
                }
            }

            Log.d(TAG, "Recognized ${recognizedChars.size}/${charBoxes.size} characters")

            if (recognizedChars.isEmpty()) {
                Log.w(TAG, "No characters recognized above confidence threshold")
                return OCRResult()
            }

            // Step 3: Group recognized characters into words
            val words = if (config.usePostRecognitionGrouping) {
                PostRecognitionGrouping.groupCharsIntoWords(
                    recognizedChars,
                    wordSpacingRatio = config.wordSpacingRatio
                )
            } else {
                // No grouping - treat each character as a word
                recognizedChars.map { char ->
                    PostRecognitionGrouping.RecognizedWord(
                        text = char.text,
                        confidence = char.confidence,
                        box = char.box,
                        charCount = 1
                    )
                }
            }

            Log.d(TAG, "Grouped into ${words.size} words")

            val texts = words.map { it.text }
            val scores = words.map { it.confidence }
            val boxes = words.map { it.box }.toTypedArray()

            return OCRResult(
                text = texts.joinToString(" "),
                confidence = if (scores.isNotEmpty()) scores.average().toFloat() else 0f,
                boxes = boxes,
                texts = texts,
                scores = scores
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error during OCR", e)
            return OCRResult()
        }
    }

    fun close() {
        textRecognizer.close()
        Log.d(TAG, "ColorBasedOCR closed")
    }
}

/**
 * Text Recognizer using ONNX recognition model
 */
class TextRecognizer(
    context: Context,
    modelPath: String,
    dictPath: String,
    useGPU: Boolean
) {
    private val TAG = "TextRecognizer"
    private val session: OnnxInferenceSession
    private val charDict: List<String>

    init {
        session = OnnxInferenceSession(context, modelPath, useGPU)

        charDict = buildList {
            add("blank")
            context.assets.open(dictPath).bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    add(line.trim())
                }
            }
        }

        Log.d(TAG, "TextRecognizer initialized with ${charDict.size} characters")
    }

    /**
     * Recognize text in cropped image
     */
    fun recognize(bitmap: Bitmap): Pair<String, Float> {
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return Pair("", 0f)
        }

        val resized = ImageUtils.resizeForRecognition(bitmap, targetHeight = 48, maxWidth = 320)
        val inputData = ImageUtils.bitmapToNormalizedFloatArray(resized)
        val inputShape = longArrayOf(1, 3, resized.height.toLong(), resized.width.toLong())

        val (outputData, outputShape) = session.run(inputData, inputShape)

        val timeSteps = outputShape[1].toInt()
        val numClasses = outputShape[2].toInt()

        val predsIdx = IntArray(timeSteps)
        val predsProb = FloatArray(timeSteps)

        for (t in 0 until timeSteps) {
            var maxIdx = 0
            var maxProb = Float.NEGATIVE_INFINITY

            for (c in 0 until numClasses) {
                val prob = outputData[t * numClasses + c]
                if (prob > maxProb) {
                    maxProb = prob
                    maxIdx = c
                }
            }

            predsIdx[t] = maxIdx
            predsProb[t] = maxProb
        }

        val text = StringBuilder()
        var lastIdx = 0
        for (idx in predsIdx) {
            if (idx > 0 && idx != lastIdx && idx < charDict.size) {
                text.append(charDict[idx])
            }
            lastIdx = idx
        }

        val confidence = predsProb.average().toFloat()

        return Pair(text.toString(), confidence)
    }

    fun close() {
        session.close()
    }
}

/**
 * Configuration for ColorBasedOCR
 */
data class ColorBasedOCRConfig(
    val recModelPath: String = "models/en_PP-OCRv5/rec.onnx",
    val dictPath: String = "labels/ppocrv5_dict.txt",
    val useGPU: Boolean = true,
    val minConfidence: Float = 0.3f,
    val minWidth: Int = 15,
    val minHeight: Int = 8,
    val maxWidth: Int = 1000,
    val maxHeight: Int = 200,
    val minAspectRatio: Float = 0.5f,
    val maxAspectRatio: Float = 20f,
    // Pre-recognition grouping (DISABLED - using post-recognition grouping instead)
    val enableGrouping: Boolean = false,
    val groupingMethod: ColorBasedTextDetector.GroupingMethod = ColorBasedTextDetector.GroupingMethod.NONE,
    // Post-recognition grouping (characters grouped AFTER recognition)
    val usePostRecognitionGrouping: Boolean = true,
    val wordSpacingRatio: Float = 1.5f  // Horizontal spacing ratio for word breaks
)

/**
 * OCR Result
 */
data class OCRResult(
    val text: String = "",
    val confidence: Float = 0f,
    val boxes: Array<Array<FloatArray>> = emptyArray(),
    val texts: List<String> = emptyList(),
    val scores: List<Float> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OCRResult

        if (text != other.text) return false
        if (confidence != other.confidence) return false
        if (!boxes.contentDeepEquals(other.boxes)) return false
        if (texts != other.texts) return false
        if (scores != other.scores) return false

        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + boxes.contentDeepHashCode()
        result = 31 * result + texts.hashCode()
        result = 31 * result + scores.hashCode()
        return result
    }
}
