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
 * TrOCR Encoder Helper using ONNX model
 * Generates 768-dimensional visual embeddings from images
 * Uses Vision Transformer (BEiT) encoder for OCR-specific image understanding
 */
class TrOCREncoderHelper(private val context: Context) {
    private val TAG = "TrOCREncoderHelper"
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var ready = false

    companion object {
        private const val MODEL_PATH = "trocr_encoder_quantized.onnx"
        private const val INPUT_SIZE = 384
        private const val EMBEDDING_SIZE = 768
        private const val NUM_PATCHES = 577 // 24x24 patches + 1 CLS token
    }

    init {
        try {
            val modelBytes = context.assets.open(MODEL_PATH).use { it.readBytes() }
            val sessionOptions = OrtSession.SessionOptions()
            session = env.createSession(modelBytes, sessionOptions)
            ready = true
            Log.d(TAG, "TrOCR Encoder initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TrOCR Encoder", e)
            ready = false
        }
    }

    fun isReady(): Boolean = ready

    data class EncoderResult(
        val clsEmbedding: FloatArray,      // CLS token embedding [768]
        val avgEmbedding: FloatArray,       // Average pooled embedding [768]
        val fullEmbedding: FloatArray       // Full sequence embedding [577 x 768]
    )

    /**
     * Extract embeddings from image
     * Returns CLS token, average pooled, and full sequence embeddings
     */
    fun embed(bitmap: Bitmap): EncoderResult? {
        if (!ready || session == null) {
            Log.e(TAG, "TrOCR Encoder not ready")
            return null
        }

        try {
            // Preserve aspect ratio with letterboxing (like ImageEmbedderHelper)
            val resized = resizeAndPadToSquare(bitmap, INPUT_SIZE)
            val pixelValues = bitmapToNormalizedFloatBuffer(resized)

            // Create input tensor [1, 3, 384, 384]
            val inputShape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            val inputTensor = OnnxTensor.createTensor(env, pixelValues, inputShape)

            // Run inference
            val startTime = System.currentTimeMillis()
            val results = session!!.run(mapOf("pixel_values" to inputTensor))
            val inferenceTime = System.currentTimeMillis() - startTime

            // Get output [1, 577, 768]
            val output = results[0].value as Array<Array<FloatArray>>
            val embeddings = output[0] // [577, 768]

            // Extract different embedding types
            val clsEmbeddingRaw = embeddings[0] // First token (CLS)
            val avgEmbedding = computeAverageEmbedding(embeddings)
            val fullEmbedding = embeddings.flatMap { it.toList() }.toFloatArray()

            // Diagnostic logging BEFORE normalization
            val minRaw = clsEmbeddingRaw.minOrNull() ?: 0f
            val maxRaw = clsEmbeddingRaw.maxOrNull() ?: 0f
            val meanRaw = clsEmbeddingRaw.average().toFloat()
            val normRaw = kotlin.math.sqrt(clsEmbeddingRaw.map { it * it }.sum())

            // L2 NORMALIZE for cosine distance
            val clsEmbedding = l2Normalize(clsEmbeddingRaw)
            val normNormalized = kotlin.math.sqrt(clsEmbedding.map { it * it }.sum())

            Log.d(TAG, "===== TROCR EMBEDDING DIAGNOSTIC =====")
            Log.d(TAG, "TrOCR encoding completed in ${inferenceTime}ms")
            Log.d(TAG, "RAW Stats - min: $minRaw, max: $maxRaw, mean: $meanRaw, norm: $normRaw")
            Log.d(TAG, "NORMALIZED norm: $normNormalized (should be ~1.0)")
            Log.d(TAG, "First 10 values: ${clsEmbedding.take(10).joinToString(", ")}")
            Log.d(TAG, "=======================================")

            inputTensor.close()
            results.close()

            return EncoderResult(clsEmbedding, avgEmbedding, fullEmbedding)
        } catch (e: Exception) {
            Log.e(TAG, "Error during TrOCR encoding", e)
            return null
        }
    }

    /**
     * Get CLS token embedding only (fastest option for image similarity)
     */
    fun embedCLS(bitmap: Bitmap): FloatArray? {
        return embed(bitmap)?.clsEmbedding
    }

    /**
     * Get average pooled embedding (good balance for image representation)
     */
    fun embedAverage(bitmap: Bitmap): FloatArray? {
        return embed(bitmap)?.avgEmbedding
    }

    /**
     * Convert bitmap to normalized float buffer for TrOCR
     * Normalizes with mean=[0.5, 0.5, 0.5] and std=[0.5, 0.5, 0.5]
     */
    private fun bitmapToNormalizedFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Create buffer for [1, 3, H, W] format
        val buffer = FloatBuffer.allocate(3 * width * height)

        // Normalize: (pixel / 255.0 - 0.5) / 0.5 = (pixel / 255.0) * 2.0 - 1.0
        for (pixel in pixels) {
            val r = ((pixel shr 16 and 0xFF) / 255.0f) * 2.0f - 1.0f
            val g = ((pixel shr 8 and 0xFF) / 255.0f) * 2.0f - 1.0f
            val b = ((pixel and 0xFF) / 255.0f) * 2.0f - 1.0f
            
            buffer.put(r)
        }
        
        for (pixel in pixels) {
            val g = ((pixel shr 8 and 0xFF) / 255.0f) * 2.0f - 1.0f
            buffer.put(g)
        }
        
        for (pixel in pixels) {
            val b = ((pixel and 0xFF) / 255.0f) * 2.0f - 1.0f
            buffer.put(b)
        }

        buffer.rewind()
        return buffer
    }

    /**
     * L2 normalize an embedding vector to unit length
     * Required for cosine distance to work correctly
     */
    private fun l2Normalize(vector: FloatArray): FloatArray {
        val norm = kotlin.math.sqrt(vector.map { it * it }.sum())
        return if (norm > 0f) {
            vector.map { it / norm }.toFloatArray()
        } else {
            Log.w(TAG, "Zero norm vector detected, returning as-is")
            vector
        }
    }

    /**
     * Resize and pad to square while preserving aspect ratio (letterboxing)
     * This preserves document/image layout instead of squashing
     */
    private fun resizeAndPadToSquare(source: Bitmap, targetSize: Int): Bitmap {
        val srcWidth = source.width
        val srcHeight = source.height
        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()

        var newWidth: Int
        var newHeight: Int

        // Scale to fit within square bounds while maintaining aspect ratio
        if (srcWidth > srcHeight) {
            // Wider than tall - fit to width
            newWidth = targetSize
            newHeight = (targetSize / srcRatio).toInt()
        } else {
            // Taller than wide - fit to height
            newHeight = targetSize
            newWidth = (targetSize * srcRatio).toInt()
        }

        val scaledBitmap = Bitmap.createScaledBitmap(source, newWidth, newHeight, true)

        // Create a black square canvas for letterboxing
        val outputBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        canvas.drawColor(Color.BLACK)

        // Calculate position to center the image
        val left = (targetSize - newWidth) / 2
        val top = (targetSize - newHeight) / 2

        val paint = Paint()
        paint.isFilterBitmap = true

        canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), paint)

        // Clean up intermediate bitmap if it's not the source
        if (scaledBitmap != source) {
            scaledBitmap.recycle()
        }

        return outputBitmap
    }

    /**
     * Compute average embedding across all patch tokens
     */
    private fun computeAverageEmbedding(embeddings: Array<FloatArray>): FloatArray {
        val embeddingSize = embeddings[0].size
        val avgEmbedding = FloatArray(embeddingSize)
        
        for (embedding in embeddings) {
            for (i in embedding.indices) {
                avgEmbedding[i] += embedding[i]
            }
        }
        
        val numTokens = embeddings.size.toFloat()
        for (i in avgEmbedding.indices) {
            avgEmbedding[i] /= numTokens
        }
        
        return avgEmbedding
    }

    /**
     * Compute cosine similarity between two embeddings
     */
    fun computeSimilarity(emb1: FloatArray, emb2: FloatArray): Float {
        if (emb1.size != emb2.size) {
            Log.e(TAG, "Embedding size mismatch: ${emb1.size} vs ${emb2.size}")
            return 0f
        }

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in emb1.indices) {
            dotProduct += emb1[i] * emb2[i]
            norm1 += emb1[i] * emb1[i]
            norm2 += emb2[i] * emb2[i]
        }

        val magnitude = kotlin.math.sqrt(norm1 * norm2)
        return if (magnitude > 0f) dotProduct / magnitude else 0f
    }

    fun setDelegate(delegate: Int) {
        // Placeholder for delegate switching if needed
        Log.d(TAG, "Delegate switching not implemented for ONNX")
    }

    fun close() {
        try {
            session?.close()
            ready = false
            Log.d(TAG, "TrOCR Encoder closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TrOCR Encoder", e)
        }
    }
}
