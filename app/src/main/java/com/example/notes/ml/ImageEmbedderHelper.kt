package com.example.notes.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder.ImageEmbedderOptions
import kotlin.jvm.Volatile

class ImageEmbedderHelper(
    private val context: Context,
    var currentDelegate: Int = DELEGATE_CPU,
    var listener: EmbedderListener? = null
) {
    private var imageEmbedder: ImageEmbedder? = null
    @Volatile
    private var isInitialized = false

    init {
        try {
            System.loadLibrary("mediapipe_tasks_vision_jni")
        } catch (e: UnsatisfiedLinkError) {
             Log.e(TAG, "Failed to load native library: " + e.message)
        }
        
        // Smart initialization of delegate
        if (currentDelegate == DELEGATE_GPU) {
             val compatibilityList = org.tensorflow.lite.gpu.CompatibilityList()
             if (!compatibilityList.isDelegateSupportedOnThisDevice) {
                 Log.d(TAG, "GPU not supported on this device, switching to CPU")
                 currentDelegate = DELEGATE_CPU
             }
        }
        
        setupImageEmbedder()
    }

    fun setupImageEmbedder() {
        if (isInitialized && imageEmbedder != null) {
            Log.d(TAG, "Image embedder already initialized and loaded, skipping re-initialization")
            return
        }
        
        val baseOptionsBuilder = BaseOptions.builder()
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
                Log.d(TAG, "Setting up image embedder with CPU delegate")
            }
            DELEGATE_GPU -> {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
                Log.d(TAG, "Setting up image embedder with GPU delegate")
            }
        }
        
        // MobileNetV3 Large (switched from Small)
        val modelName = "mobilenet_v3_large.tflite"
        
        baseOptionsBuilder.setModelAssetPath(modelName)

        try {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder =
                ImageEmbedderOptions.builder().setBaseOptions(baseOptions)
                .setQuantize(false) // Floating point for HNSW
                .setL2Normalize(true) // Cosine similarity expects normalized vectors

            val options = optionsBuilder.build()
            imageEmbedder = ImageEmbedder.createFromOptions(context, options)
            isInitialized = true
            Log.d(TAG, "Image embedder successfully initialized and ready")
        } catch (e: Exception) {
            listener?.onError("Image embedder failed to initialize: " + e.message)
            Log.e(TAG, "Image embedder init error: " + e.message)
            isInitialized = false
            
            // Fallback to CPU if GPU failed
            if (currentDelegate == DELEGATE_GPU) {
                Log.w(TAG, "Falling back to CPU delegate")
                currentDelegate = DELEGATE_CPU
                setupImageEmbedder()
            }
        } catch (e: UnsatisfiedLinkError) {
            listener?.onError("Image embedder failed to load native library: " + e.message)
            Log.e(TAG, "Image embedder native lib error: " + e.message)
        }
    }

    @Synchronized
    fun embed(bitmap: Bitmap): ResultBundle? {
        val startTime = SystemClock.uptimeMillis()
        
        // Resize and pad to A4 ratio (1:√2 ≈ 1:1.414) for document representation
        // A4 dimensions: 224x317 maintains the paper aspect ratio
        val targetWidth = 224
        val targetHeight = 317  // 224 * 1.414 ≈ 317
        val safeBitmap = resizeAndPadA4(bitmap, targetWidth, targetHeight)

        Log.d(TAG, "Embedding bitmap: ${safeBitmap.width}x${safeBitmap.height}, Config: ${safeBitmap.config}")
        
        val mpImage = BitmapImageBuilder(safeBitmap).build()
        
        imageEmbedder?.let {
            try {
                val result = it.embed(mpImage)
                
                if (result == null) {
                    Log.e(TAG, "Image embedder returned null result")
                    return null
                }

                val embeddingResult = result.embeddingResult()?.embeddings()?.firstOrNull()
                if (embeddingResult == null) {
                    Log.e(TAG, "No embedding result found")
                    return null
                }
                
                val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
                
                // Return raw float array for ObjectBox storage
                return ResultBundle(
                    embeddingResult.floatEmbedding(),
                    inferenceTimeMs
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error running image embedder: " + e.message)
                return null
            }
        }
        return null
    }

    fun clearImageEmbedder() {
        imageEmbedder?.close()
        imageEmbedder = null
        isInitialized = false
        Log.d(TAG, "Image embedder cleared")
    }

    fun setDelegate(delegate: Int) {
        if (currentDelegate != delegate) {
            currentDelegate = delegate
            clearImageEmbedder()
            setupImageEmbedder()
        }
    }
    
    /**
     * Check if embedder is ready for inference
     */
    fun isReady(): Boolean = isInitialized && imageEmbedder != null

    private fun resizeAndPadA4(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val srcWidth = source.width
        val srcHeight = source.height
        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()

        var newWidth: Int
        var newHeight: Int

        // Scale to fit within A4 bounds while maintaining aspect ratio
        if (srcRatio > targetRatio) {
            // Source is wider than A4 - fit to width
            newWidth = targetWidth
            newHeight = (targetWidth / srcRatio).toInt()
        } else {
            // Source is taller than A4 - fit to height
            newHeight = targetHeight
            newWidth = (targetHeight * srcRatio).toInt()
        }

        val scaledBitmap = Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
        
        // Create a black A4 bitmap (portrait orientation)
        val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        canvas.drawColor(Color.BLACK)

        // Calculate position to center the image in A4 canvas
        val left = (targetWidth - newWidth) / 2
        val top = (targetHeight - newHeight) / 2

        val paint = Paint()
        paint.isFilterBitmap = true
        
        canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), paint)
        
        // Clean up intermediate bitmap if it's not the source (createScaledBitmap might return source if no scaling needed)
        if (scaledBitmap != source) {
            scaledBitmap.recycle()
        }
        
        return outputBitmap
    }

    data class ResultBundle(
        val embedding: FloatArray,
        val inferenceTime: Long,
    )

    interface EmbedderListener {
        fun onError(error: String, errorCode: Int = 0)
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val TAG = "ImageEmbedderHelper"
    }
}
