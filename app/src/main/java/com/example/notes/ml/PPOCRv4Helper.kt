package com.example.notes.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.equationl.paddleocr4android.OCR
import com.equationl.paddleocr4android.OcrConfig
import com.equationl.paddleocr4android.callback.OcrInitCallback
import com.equationl.paddleocr4android.callback.OcrRunCallback
import kotlin.jvm.Volatile

/**
 * PP-OCR Helper for high-accuracy text recognition on Android
 * Uses paddleocr4android library (built from source)
 * 
 * Pipeline: Detection → Classification → Recognition
 */
class PPOCRv4Helper(private val context: Context) {
    
    companion object {
        private const val TAG = "PPOCRv4Helper"
        
        // Model directory (using PP-OCRv4 models - ~10% better accuracy than v3)
        private const val MODEL_PATH = "models/ch_PP-OCRv4"
        private const val DICT_FILE = "ppocr_keys_v1.txt"
    }
    
    private var ocr: OCR? = null
    @Volatile
    private var isInitialized = false
    @Volatile
    private var isInitializing = false
    private val initLock = Any()
    
    /**
     * Result class for OCR operations
     */
    data class OCRResult(
        val text: String,
        val confidence: Float,
        val boxes: List<TextBox>
    )
    
    /**
     * Text box with location and content
     */
    data class TextBox(
        val text: String,
        val confidence: Float,
        val points: List<android.graphics.PointF>
    )
    
    init {
        initializeOCR()
    }
    
    /**
     * Initialize OCR with configuration (thread-safe, single initialization)
     */
    private fun initializeOCR() {
        synchronized(initLock) {
            if (isInitialized || isInitializing) {
                Log.d(TAG, "OCR already initialized or initializing, skipping")
                return
            }
            
            isInitializing = true
            try {
                val config = OcrConfig(
                    modelPath = MODEL_PATH,
                    labelPath = "labels/$DICT_FILE",  // Dictionary in labels subdirectory
                    cpuThreadNum = 4,
                    cpuPowerMode = com.equationl.paddleocr4android.CpuPowerMode.LITE_POWER_FULL,  // Use all cores for better accuracy
                    detModelFilename = "det.nb",
                    recModelFilename = "rec.nb",
                    clsModelFilename = "cls.nb",
                    isRunDet = true,
                    isRunCls = true,  // Important for rotated text
                    isRunRec = true,
                    scoreThreshold = 0.3f,  // Lower threshold to detect lighter/faded handwriting
                    detLongSize = 1280  // Increase from default 960 for better detail capture
                )
                
                ocr = OCR(context)
                ocr?.initModel(config, object : OcrInitCallback {
                    override fun onSuccess() {
                        Log.d(TAG, "PaddleOCR initialized successfully - models loaded and ready")
                        isInitialized = true
                        isInitializing = false
                    }
                    
                    override fun onFail(e: Throwable) {
                        Log.e(TAG, "PaddleOCR initialization failed", e)
                        isInitialized = false
                        isInitializing = false
                    }
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize PaddleOCR", e)
                isInitializing = false
                throw e
            }
        }
    }
    
    /**
     * Check if OCR is ready for inference
     */
    fun isReady(): Boolean = isInitialized
    
    /**
     * Wait for OCR to be ready (with timeout)
     */
    fun waitUntilReady(timeoutMs: Long = 5000): Boolean {
        val startTime = System.currentTimeMillis()
        while (!isInitialized && (System.currentTimeMillis() - startTime) < timeoutMs) {
            Thread.sleep(100)
        }
        return isInitialized
    }
    
    /**
     * Main recognition method - synchronous wrapper
     */
    fun recognizeText(bitmap: Bitmap): OCRResult {
        if (!isInitialized) {
            Log.w(TAG, "OCR not ready yet, waiting for initialization...")
            if (!waitUntilReady(5000)) {
                Log.e(TAG, "OCR initialization timeout - models not loaded")
                return OCRResult(text = "", confidence = 0f, boxes = emptyList())
            }
            Log.d(TAG, "OCR ready, proceeding with recognition")
        }
        
        val startTime = System.currentTimeMillis()
        
        return try {
            // Use a simple blocking approach with a callback
            var result: OCRResult? = null
            var error: Throwable? = null
            val lock = Object()
            
            ocr?.run(bitmap, object : OcrRunCallback {
                override fun onSuccess(ocrResult: com.equationl.paddleocr4android.bean.OcrResult) {
                    synchronized(lock) {
                        result = convertResult(ocrResult)
                        lock.notify()
                    }
                }
                
                override fun onFail(e: Throwable) {
                    synchronized(lock) {
                        error = e
                        lock.notify()
                    }
                }
            })
            
            // Wait for callback (with timeout)
            synchronized(lock) {
                lock.wait(10000) // 10 second timeout
            }
            
            val elapsedTime = System.currentTimeMillis() - startTime
            
            when {
                result != null -> {
                    Log.d(TAG, "OCR completed in ${elapsedTime}ms: ${result!!.text}")
                    result!!
                }
                error != null -> {
                    Log.e(TAG, "OCR error after ${elapsedTime}ms", error)
                    OCRResult(text = "", confidence = 0f, boxes = emptyList())
                }
                else -> {
                    Log.w(TAG, "OCR timeout after ${elapsedTime}ms")
                    OCRResult(text = "", confidence = 0f, boxes = emptyList())
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "OCR exception", e)
            OCRResult(text = "", confidence = 0f, boxes = emptyList())
        }
    }
    
    /**
     * Convert library result format to our format
     */
    private fun convertResult(ocrResult: com.equationl.paddleocr4android.bean.OcrResult): OCRResult {
        val textBoxes = mutableListOf<TextBox>()
        val fullText = StringBuilder()
        var totalConfidence = 0f
        var count = 0
        
        // Process each word result from raw output
        ocrResult.outputRawResult.forEach { wordResult ->
            val text = wordResult.label ?: ""
            val confidence = wordResult.confidence
            val points = wordResult.points.map { point ->
                android.graphics.PointF(point.x.toFloat(), point.y.toFloat())
            }
            
            if (text.isNotEmpty()) {
                textBoxes.add(TextBox(text, confidence, points))
                fullText.append(text).append(" ")
                totalConfidence += confidence
                count++
            }
        }
        
        val avgConfidence = if (count > 0) totalConfidence / count else 0f
        
        return OCRResult(
            text = fullText.toString().trim(),
            confidence = avgConfidence,
            boxes = textBoxes
        )
    }
    
    /**
     * Release resources
     */
    fun release() {
        try {
            ocr?.releaseModel()
            ocr = null
            isInitialized = false
            Log.d(TAG, "PaddleOCR resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing PaddleOCR", e)
        }
    }
}
