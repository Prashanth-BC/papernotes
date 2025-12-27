package com.example.notes.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * CLIP (Contrastive Language-Image Pre-training) Image Embedder
 *
 * Generates robust 512-dimensional image embeddings using CLIP ViT-B/32 model.
 * CLIP embeddings are much more robust to variations compared to MobileNetV3:
 * - Handles lighting changes (brightness/contrast)
 * - Robust to cropping (up to 30-40%)
 * - Handles rotation (up to 45°)
 * - Semantic understanding of image content
 *
 * Model: openai/clip-vit-base-patch32 (quantized INT8)
 * Input: [1, 3, 224, 224] - RGB image
 * Output: [1, 512] - Image embedding
 *
 * Preprocessing: CLIP-style normalization
 * - Mean: [0.48145466, 0.4578275, 0.40821073]
 * - Std:  [0.26862954, 0.26130258, 0.27577711]
 */
class CLIPImageEmbedder(private val context: Context) {

    companion object {
        private const val TAG = "CLIPImageEmbedder"
        private const val MODEL_NAME = "clip_vit_b32_quantized.onnx"
        private const val INPUT_SIZE = 224
        private const val EMBEDDING_SIZE = 512

        // CLIP normalization parameters (ImageNet-based, optimized for CLIP)
        private val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        private val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
    }

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    var ready = false
        private set

    data class EmbeddingResult(
        val embedding: FloatArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EmbeddingResult
            return embedding.contentEquals(other.embedding)
        }

        override fun hashCode(): Int {
            return embedding.contentHashCode()
        }
    }

    /**
     * Initialize ONNX Runtime environment and load CLIP model
     */
    fun initialize() {
        try {
            Log.d(TAG, "Initializing CLIP Image Embedder...")

            // Create ONNX Runtime environment
            env = OrtEnvironment.getEnvironment()

            // Load model from assets
            val modelBytes = context.assets.open(MODEL_NAME).use { it.readBytes() }
            session = env!!.createSession(modelBytes)

            Log.d(TAG, "✓ CLIP model loaded: $MODEL_NAME")
            Log.d(TAG, "  Input size: ${INPUT_SIZE}x$INPUT_SIZE")
            Log.d(TAG, "  Embedding size: $EMBEDDING_SIZE")
            Log.d(TAG, "  Model size: ${modelBytes.size / (1024 * 1024)} MB")

            ready = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CLIP embedder", e)
            ready = false
        }
    }

    /**
     * Resize and pad image to square (letterboxing) while preserving aspect ratio
     * Uses bicubic interpolation for better quality (CLIP preprocessing)
     */
    private fun resizeAndPadToSquare(source: Bitmap, targetSize: Int): Bitmap {
        val srcWidth = source.width
        val srcHeight = source.height
        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()

        var newWidth: Int
        var newHeight: Int

        // Scale to fit within square bounds while maintaining aspect ratio
        if (srcWidth > srcHeight) {
            newWidth = targetSize
            newHeight = (targetSize / srcRatio).toInt()
        } else {
            newHeight = targetSize
            newWidth = (targetSize * srcRatio).toInt()
        }

        // Use high-quality filtering for CLIP (bicubic interpolation)
        val scaledBitmap = Bitmap.createScaledBitmap(source, newWidth, newHeight, true)

        // Create RGB canvas (CLIP expects RGB, not black padding)
        // Use middle gray [0.5, 0.5, 0.5] for padding (neutral in CLIP's space)
        val outputBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        canvas.drawColor(Color.rgb(128, 128, 128)) // Gray padding

        // Center the image
        val left = (targetSize - newWidth) / 2
        val top = (targetSize - newHeight) / 2

        val paint = Paint()
        paint.isFilterBitmap = true // Enable bilinear filtering

        canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), paint)

        if (scaledBitmap != source) {
            scaledBitmap.recycle()
        }

        return outputBitmap
    }

    /**
     * Convert bitmap to normalized float buffer for CLIP
     *
     * CLIP preprocessing:
     * 1. Resize to 224x224 with letterboxing
     * 2. Convert to RGB [0, 255]
     * 3. Scale to [0, 1]: pixel / 255.0
     * 4. Normalize with CLIP mean/std: (pixel - mean) / std
     * 5. Channel order: RGB (not BGR)
     * 6. Format: [1, 3, H, W] (NCHW)
     */
    private fun bitmapToNormalizedFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val buffer = FloatBuffer.allocate(1 * 3 * INPUT_SIZE * INPUT_SIZE)

        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        var idx = 0
        // CLIP uses NCHW format: [batch, channel, height, width]
        // Channel 0: R
        for (i in intValues.indices) {
            val pixelValue = intValues[i]
            val r = ((pixelValue shr 16) and 0xFF) / 255.0f
            buffer.put(idx, (r - MEAN[0]) / STD[0])
            idx++
        }

        // Channel 1: G
        idx = INPUT_SIZE * INPUT_SIZE
        for (i in intValues.indices) {
            val pixelValue = intValues[i]
            val g = ((pixelValue shr 8) and 0xFF) / 255.0f
            buffer.put(idx, (g - MEAN[1]) / STD[1])
            idx++
        }

        // Channel 2: B
        idx = INPUT_SIZE * INPUT_SIZE * 2
        for (i in intValues.indices) {
            val pixelValue = intValues[i]
            val b = (pixelValue and 0xFF) / 255.0f
            buffer.put(idx, (b - MEAN[2]) / STD[2])
            idx++
        }

        buffer.rewind()
        return buffer
    }

    /**
     * L2 normalize embedding (unit vector)
     * Required for cosine distance in ObjectBox HNSW
     */
    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (v in vector) {
            sumSquares += (v * v)
        }

        val norm = Math.sqrt(sumSquares).toFloat()
        if (norm < 1e-12f) {
            Log.w(TAG, "⚠️ Near-zero norm detected: $norm")
            return vector
        }

        return FloatArray(vector.size) { vector[it] / norm }
    }

    /**
     * Generate CLIP embedding for an image
     *
     * @param bitmap Input image (any size, will be resized with letterboxing)
     * @return 512-dimensional L2-normalized embedding, or null on error
     */
    fun embed(bitmap: Bitmap): EmbeddingResult? {
        if (!ready || session == null || env == null) {
            Log.e(TAG, "CLIP embedder not ready")
            return null
        }

        try {
            // Preserve aspect ratio with letterboxing
            val resized = resizeAndPadToSquare(bitmap, INPUT_SIZE)
            val inputBuffer = bitmapToNormalizedFloatBuffer(resized)

            // Create ONNX tensor [1, 3, 224, 224]
            val inputShape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            val inputTensor = OnnxTensor.createTensor(env, inputBuffer, inputShape)

            // Run inference
            val results = session!!.run(mapOf("pixel_values" to inputTensor))

            // Get output [1, 512]
            val output = results[0].value as Array<FloatArray>
            val rawEmbedding = output[0]

            inputTensor.close()
            results.close()

            // L2 normalize for cosine distance
            val embedding = l2Normalize(rawEmbedding)

            // Diagnostic logging
            val norm = Math.sqrt(rawEmbedding.sumOf { (it * it).toDouble() }).toFloat()
            Log.d(TAG, "CLIP embedding generated:")
            Log.d(TAG, "  Raw norm: $norm")
            Log.d(TAG, "  Normalized norm: ${Math.sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()}")
            Log.d(TAG, "  Embedding range: [${embedding.minOrNull()}, ${embedding.maxOrNull()}]")

            if (resized != bitmap) {
                resized.recycle()
            }

            return EmbeddingResult(embedding)

        } catch (e: Exception) {
            Log.e(TAG, "Error generating CLIP embedding", e)
            return null
        }
    }

    /**
     * Release ONNX Runtime resources
     */
    fun close() {
        try {
            session?.close()
            session = null
            ready = false
            Log.d(TAG, "CLIP embedder closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing CLIP embedder", e)
        }
    }
}
