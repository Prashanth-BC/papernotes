package com.example.notes.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Image embedding helper using TFLite model
 * Generates 1280-dimensional image embeddings
 */
class ImageEmbedderHelper(private val context: Context) {
    private val TAG = "ImageEmbedderHelper"
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    companion object {
        private const val MODEL_PATH = "mobilenet_v3_image_embedder.tflite"
        private const val INPUT_SIZE = 224
        private const val EMBEDDING_SIZE = 1280
    }

    init {
        try {
            val model = loadModelFile()
            val options = Interpreter.Options()

            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                gpuDelegate = GpuDelegate(delegateOptions)
                options.addDelegate(gpuDelegate)
                Log.d(TAG, "GPU delegate enabled")
            } else {
                options.setNumThreads(4)
                Log.d(TAG, "Using CPU with 4 threads")
            }

            interpreter = Interpreter(model, options)
            Log.d(TAG, "ImageEmbedderHelper initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ImageEmbedderHelper", e)
        }
    }

    data class EmbeddingResult(val embedding: FloatArray)

    fun embed(bitmap: Bitmap): EmbeddingResult? {
        try {
            // Preserve aspect ratio with document-aware padding (like old MediaPipe version)
            // Use square output for TFLite compatibility, but preserve aspect ratio with letterboxing
            val resized = resizeAndPadToSquare(bitmap, INPUT_SIZE)
            val inputBuffer = bitmapToByteBuffer(resized)

            val outputArray = Array(1) { FloatArray(EMBEDDING_SIZE) }
            interpreter?.run(inputBuffer, outputArray)

            val rawEmbedding = outputArray[0]

            // Diagnostic logging BEFORE normalization
            val minRaw = rawEmbedding.minOrNull() ?: 0f
            val maxRaw = rawEmbedding.maxOrNull() ?: 0f
            val meanRaw = rawEmbedding.average().toFloat()
            val normRaw = kotlin.math.sqrt(rawEmbedding.map { it * it }.sum())
            val allZeros = rawEmbedding.all { it == 0f }

            Log.d(TAG, "===== IMAGE EMBEDDING DIAGNOSTIC =====")
            Log.d(TAG, "RAW Stats - min: $minRaw, max: $maxRaw, mean: $meanRaw")
            Log.d(TAG, "RAW L2 norm: $normRaw, All zeros: $allZeros")
            Log.d(TAG, "First 10 values: ${rawEmbedding.take(10).joinToString(", ")}")

            // L2 NORMALIZE (critical for cosine distance!)
            // This matches the old MediaPipe behavior: setL2Normalize(true)
            val embedding = l2Normalize(rawEmbedding)

            val normNormalized = kotlin.math.sqrt(embedding.map { it * it }.sum())
            Log.d(TAG, "NORMALIZED L2 norm: $normNormalized (should be ~1.0)")
            Log.d(TAG, "=====================================")

            return EmbeddingResult(embedding)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating embedding", e)
            return null
        }
    }

    fun setDelegate(delegate: Int) {
        // Placeholder for delegate switching
    }

    /**
     * L2 normalize an embedding vector to unit length
     * This matches the old MediaPipe behavior: setL2Normalize(true)
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
     * This preserves document layout instead of squashing
     * Matches the old MediaPipe preprocessing approach
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

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val width = bitmap.width
        val height = bitmap.height
        val byteBuffer = ByteBuffer.allocateDirect(4 * width * height * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(width * height)
        bitmap.getPixels(intValues, 0, width, 0, 0, width, height)

        var pixel = 0
        for (i in 0 until height) {
            for (j in 0 until width) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16 and 0xFF) - 127.5f) / 127.5f)
                byteBuffer.putFloat(((value shr 8 and 0xFF) - 127.5f) / 127.5f)
                byteBuffer.putFloat(((value and 0xFF) - 127.5f) / 127.5f)
            }
        }

        return byteBuffer
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
    }
}
