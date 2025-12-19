package com.example.notes.ml

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.util.Log
import kotlin.math.*

/**
 * Image preprocessing utilities for improving OCR accuracy
 * Especially useful for handwritten notes on colored paper
 */
object ImagePreprocessor {
    
    private const val TAG = "ImagePreprocessor"
    
    /**
     * Complete preprocessing pipeline optimized for handwritten notes
     * 
     * @param bitmap Input image
     * @param applyGrayscale Convert to grayscale
     * @param applyBinarization Apply binary thresholding
     * @param threshold Binarization threshold (0-1)
     * @param applyNoiseReduction Remove small noise artifacts
     * @param applyDeskew Correct rotation
     * @return Preprocessed bitmap
     */
    fun preprocessForOCR(
        bitmap: Bitmap,
        applyGrayscale: Boolean = true,
        applyBinarization: Boolean = true,
        threshold: Float = 0.6f,
        applyNoiseReduction: Boolean = false,
        applyDeskew: Boolean = false
    ): Bitmap {
        var processed = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        try {
            // Step 1: Convert to grayscale
            if (applyGrayscale) {
                processed = toGrayscale(processed)
                Log.d(TAG, "Applied grayscale conversion")
            }
            
            // Step 2: Apply binary thresholding (black & white)
            if (applyBinarization) {
                processed = binarize(processed, threshold)
                Log.d(TAG, "Applied binarization with threshold $threshold")
            }
            
            // Step 3: Reduce noise
            if (applyNoiseReduction) {
                processed = reduceNoise(processed)
                Log.d(TAG, "Applied noise reduction")
            }
            
            // Step 4: Correct rotation
            if (applyDeskew) {
                val angle = detectSkewAngle(processed)
                if (abs(angle) > 0.5f) {
                    processed = rotate(processed, angle)
                    Log.d(TAG, "Deskewed by $angle degrees")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Preprocessing failed", e)
            return bitmap // Return original on error
        }
        
        return processed
    }
    
    /**
     * Preset for yellow legal pad notes
     */
    fun preprocessYellowPaper(bitmap: Bitmap): Bitmap {
        return preprocessForOCR(
            bitmap = bitmap,
            applyGrayscale = true,
            applyBinarization = true,
            threshold = 0.55f,  // Lower threshold for yellow background
            applyNoiseReduction = true,
            applyDeskew = true
        )
    }
    
    /**
     * Preset for white paper with light pencil
     */
    fun preprocessLightPencil(bitmap: Bitmap): Bitmap {
        return preprocessForOCR(
            bitmap = bitmap,
            applyGrayscale = true,
            applyBinarization = true,
            threshold = 0.7f,  // Higher threshold for light writing
            applyNoiseReduction = false,
            applyDeskew = true
        )
    }
    
    /**
     * Preset for dark ink on white paper
     */
    fun preprocessDarkInk(bitmap: Bitmap): Bitmap {
        return preprocessForOCR(
            bitmap = bitmap,
            applyGrayscale = true,
            applyBinarization = true,
            threshold = 0.5f,
            applyNoiseReduction = false,
            applyDeskew = true
        )
    }
    
    /**
     * Convert to grayscale using luminosity method
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            
            // Luminosity method: 0.299R + 0.587G + 0.114B
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = Color.rgb(gray, gray, gray)
        }
        
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
    
    /**
     * Apply binary thresholding (Otsu's method or fixed threshold)
     */
    private fun binarize(bitmap: Bitmap, threshold: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Calculate automatic threshold if adaptive
        val autoThreshold = if (threshold < 0) {
            calculateOtsuThreshold(pixels)
        } else {
            (threshold * 255).toInt()
        }
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val gray = Color.red(pixel) // Already grayscale
            
            pixels[i] = if (gray > autoThreshold) {
                Color.WHITE
            } else {
                Color.BLACK
            }
        }
        
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
    
    /**
     * Calculate optimal threshold using Otsu's method
     */
    private fun calculateOtsuThreshold(pixels: IntArray): Int {
        // Build histogram
        val histogram = IntArray(256)
        for (pixel in pixels) {
            val gray = Color.red(pixel)
            histogram[gray]++
        }
        
        val total = pixels.size
        var sum = 0f
        for (i in 0..255) {
            sum += i * histogram[i]
        }
        
        var sumB = 0f
        var wB = 0
        var wF: Int
        var maxVariance = 0f
        var threshold = 0
        
        for (t in 0..255) {
            wB += histogram[t]
            if (wB == 0) continue
            
            wF = total - wB
            if (wF == 0) break
            
            sumB += t * histogram[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            
            val variance = wB * wF * (mB - mF) * (mB - mF)
            
            if (variance > maxVariance) {
                maxVariance = variance
                threshold = t
            }
        }
        
        return threshold
    }
    
    /**
     * Simple noise reduction using median filter
     */
    private fun reduceNoise(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val resultPixels = IntArray(width * height)
        val kernelSize = 3
        val offset = kernelSize / 2
        
        for (y in offset until height - offset) {
            for (x in offset until width - offset) {
                val neighbors = mutableListOf<Int>()
                
                for (ky in -offset..offset) {
                    for (kx in -offset..offset) {
                        val idx = (y + ky) * width + (x + kx)
                        neighbors.add(Color.red(pixels[idx]))
                    }
                }
                
                neighbors.sort()
                val median = neighbors[neighbors.size / 2]
                resultPixels[y * width + x] = Color.rgb(median, median, median)
            }
        }
        
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }
    
    /**
     * Detect skew angle using Hough transform (simplified)
     */
    private fun detectSkewAngle(bitmap: Bitmap): Float {
        // Simplified skew detection
        // Production implementation would use Hough line transform
        return 0f // Placeholder
    }
    
    /**
     * Rotate bitmap by angle (degrees)
     */
    private fun rotate(bitmap: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            bitmap, 0, 0,
            bitmap.width, bitmap.height,
            matrix, true
        )
    }
    
    /**
     * Increase image contrast
     */
    fun adjustContrast(bitmap: Bitmap, contrast: Float = 1.5f): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val factor = (259f * (contrast + 255f)) / (255f * (259f - contrast))
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            
            val newR = ((factor * (r - 128)) + 128).toInt().coerceIn(0, 255)
            val newG = ((factor * (g - 128)) + 128).toInt().coerceIn(0, 255)
            val newB = ((factor * (b - 128)) + 128).toInt().coerceIn(0, 255)
            
            pixels[i] = Color.rgb(newR, newG, newB)
        }
        
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
    
    /**
     * Sharpen image for better text detection
     */
    fun sharpen(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Sharpening kernel
        val kernel = arrayOf(
            floatArrayOf(0f, -1f, 0f),
            floatArrayOf(-1f, 5f, -1f),
            floatArrayOf(0f, -1f, 0f)
        )
        
        val resultPixels = IntArray(width * height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var r = 0f
                var g = 0f
                var b = 0f
                
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val idx = (y + ky) * width + (x + kx)
                        val pixel = pixels[idx]
                        val weight = kernel[ky + 1][kx + 1]
                        
                        r += Color.red(pixel) * weight
                        g += Color.green(pixel) * weight
                        b += Color.blue(pixel) * weight
                    }
                }
                
                resultPixels[y * width + x] = Color.rgb(
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                )
            }
        }
        
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }
    
    /**
     * Resize while maintaining aspect ratio
     */
    fun resizeMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        
        val scale = min(
            maxDimension.toFloat() / width,
            maxDimension.toFloat() / height
        )
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
