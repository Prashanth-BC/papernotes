package com.example.notes.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

/**
 * Example usage of PP-OCRv4 for different scenarios
 * 
 * Use this as a reference for integrating OCR into your app
 */
object PPOCRExamples {
    
    private const val TAG = "PPOCRExamples"
    
    /**
     * Example 1: Basic text recognition
     */
    fun basicRecognition(context: Context, bitmap: Bitmap) {
        val ocrHelper = PPOCRv4Helper(context)
        
        try {
            val result = ocrHelper.recognizeText(bitmap)
            
            Log.d(TAG, "Recognized text:")
            Log.d(TAG, result.text)
            Log.d(TAG, "Overall confidence: ${result.confidence}")
            Log.d(TAG, "Found ${result.boxes.size} text regions")
            
            // Print each text box
            result.boxes.forEachIndexed { index, box ->
                Log.d(TAG, "Box $index: '${box.text}' (confidence: ${box.confidence})")
            }
            
        } finally {
            ocrHelper.release()
        }
    }
    
    /**
     * Example 2: Handwritten notes on yellow legal pad
     */
    fun yellowPadNotes(context: Context, bitmap: Bitmap) {
        val ocrHelper = PPOCRv4Helper(context)
        
        try {
            // Preprocess for yellow background
            val preprocessed = ImagePreprocessor.preprocessYellowPaper(bitmap)
            
            // Run OCR
            val result = ocrHelper.recognizeText(preprocessed)
            
            if (result.confidence > 0.7f) {
                Log.d(TAG, "High confidence result:")
                Log.d(TAG, result.text)
            } else {
                Log.d(TAG, "Low confidence (${result.confidence}), may need adjustment")
            }
            
        } finally {
            ocrHelper.release()
        }
    }
    
    /**
     * Example 3: Light pencil on white paper
     */
    fun lightPencilNotes(context: Context, bitmap: Bitmap) {
        val ocrHelper = PPOCRv4Helper(context)
        
        try {
            // Preprocess for light writing
            val preprocessed = ImagePreprocessor.preprocessLightPencil(bitmap)
            
            // Optional: Increase contrast
            val enhanced = ImagePreprocessor.adjustContrast(preprocessed, 1.8f)
            
            val result = ocrHelper.recognizeText(enhanced)
            Log.d(TAG, "Extracted text: ${result.text}")
            
        } finally {
            ocrHelper.release()
        }
    }
    
    /**
     * Example 4: Dark ink on white paper
     */
    fun darkInkNotes(context: Context, bitmap: Bitmap) {
        val ocrHelper = PPOCRv4Helper(context)
        
        try {
            // Minimal preprocessing needed for dark ink
            val preprocessed = ImagePreprocessor.preprocessDarkInk(bitmap)
            
            val result = ocrHelper.recognizeText(preprocessed)
            Log.d(TAG, "Recognized: ${result.text}")
            
        } finally {
            ocrHelper.release()
        }
    }
    
    /**
     * Example 5: Custom preprocessing
     */
    fun customPreprocessing(context: Context, bitmap: Bitmap) {
        val ocrHelper = PPOCRv4Helper(context)
        
        try {
            // Custom preprocessing pipeline
            var processed = bitmap
            
            // 1. Resize if too large (improves speed)
            processed = ImagePreprocessor.resizeMaxDimension(processed, 2048)
            
            // 2. Sharpen for better edge detection
            processed = ImagePreprocessor.sharpen(processed)
            
            // 3. Custom preprocessing
            processed = ImagePreprocessor.preprocessForOCR(
                bitmap = processed,
                applyGrayscale = true,
                applyBinarization = true,
                threshold = 0.55f,  // Custom threshold
                applyNoiseReduction = true,
                applyDeskew = false  // Disable if already straight
            )
            
            val result = ocrHelper.recognizeText(processed)
            Log.d(TAG, "Custom pipeline result: ${result.text}")
            
        } finally {
            ocrHelper.release()
        }
    }
    
    /**
     * Example 6: Batch processing multiple images
     */
    fun batchProcessing(context: Context, bitmaps: List<Bitmap>): List<String> {
        val ocrHelper = PPOCRv4Helper(context)
        val results = mutableListOf<String>()
        
        try {
            bitmaps.forEachIndexed { index, bitmap ->
                Log.d(TAG, "Processing image ${index + 1}/${bitmaps.size}")
                
                val preprocessed = ImagePreprocessor.preprocessForOCR(bitmap)
                val result = ocrHelper.recognizeText(preprocessed)
                results.add(result.text)
                
                Log.d(TAG, "Image $index: Found ${result.text.length} characters")
            }
            
        } finally {
            ocrHelper.release()
        }
        
        return results
    }
    
    /**
     * Example 7: Processing with error handling
     */
    fun robustProcessing(context: Context, bitmap: Bitmap): Result<String> {
        return try {
            val ocrHelper = PPOCRv4Helper(context)
            
            try {
                // Validate input
                if (bitmap.width < 100 || bitmap.height < 100) {
                    return Result.failure(Exception("Image too small"))
                }
                
                // Preprocess
                val preprocessed = ImagePreprocessor.preprocessForOCR(bitmap)
                
                // Run OCR
                val result = ocrHelper.recognizeText(preprocessed)
                
                // Validate output
                if (result.confidence < 0.3f) {
                    Log.w(TAG, "Low confidence result: ${result.confidence}")
                }
                
                Result.success(result.text)
                
            } finally {
                ocrHelper.release()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "OCR processing failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Example 8: Save preprocessed image for debugging
     */
    fun debugPreprocessing(context: Context, bitmap: Bitmap): File? {
        return try {
            val preprocessed = ImagePreprocessor.preprocessForOCR(
                bitmap = bitmap,
                applyGrayscale = true,
                applyBinarization = true,
                threshold = 0.6f
            )
            
            // Save to cache for inspection
            val outputFile = File(context.cacheDir, "preprocessed_${System.currentTimeMillis()}.jpg")
            outputFile.outputStream().use { out ->
                preprocessed.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            
            Log.d(TAG, "Saved preprocessed image to: ${outputFile.absolutePath}")
            outputFile
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save debug image", e)
            null
        }
    }
    
    /**
     * Example 9: Compare different preprocessing methods
     */
    fun comparePreprocessing(context: Context, bitmap: Bitmap) {
        val ocrHelper = PPOCRv4Helper(context)
        
        try {
            // Method 1: No preprocessing
            val result1 = ocrHelper.recognizeText(bitmap)
            Log.d(TAG, "No preprocessing: confidence=${result1.confidence}")
            
            // Method 2: Grayscale only
            val gray = ImagePreprocessor.preprocessForOCR(
                bitmap, applyGrayscale = true, applyBinarization = false
            )
            val result2 = ocrHelper.recognizeText(gray)
            Log.d(TAG, "Grayscale only: confidence=${result2.confidence}")
            
            // Method 3: Full preprocessing
            val full = ImagePreprocessor.preprocessForOCR(bitmap)
            val result3 = ocrHelper.recognizeText(full)
            Log.d(TAG, "Full preprocessing: confidence=${result3.confidence}")
            
            // Report best method
            val best = listOf(
                "None" to result1.confidence,
                "Grayscale" to result2.confidence,
                "Full" to result3.confidence
            ).maxByOrNull { it.second }
            
            Log.d(TAG, "Best method: ${best?.first} (${best?.second})")
            
        } finally {
            ocrHelper.release()
        }
    }
    
    /**
     * Example 10: Load from file and process
     */
    fun processFromFile(context: Context, imagePath: String): String {
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw IllegalArgumentException("Failed to load image from $imagePath")
        
        val ocrHelper = PPOCRv4Helper(context)
        
        try {
            val preprocessed = ImagePreprocessor.preprocessForOCR(bitmap)
            val result = ocrHelper.recognizeText(preprocessed)
            
            Log.d(TAG, "Processed file: $imagePath")
            Log.d(TAG, "Result: ${result.text.take(100)}...")
            
            return result.text
            
        } finally {
            ocrHelper.release()
            bitmap.recycle()
        }
    }
}
