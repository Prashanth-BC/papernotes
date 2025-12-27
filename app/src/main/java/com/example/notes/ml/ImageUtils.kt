package com.example.notes.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.sqrt

/**
 * Image preprocessing utilities for OCR
 */
object ImageUtils {

    /**
     * Convert bitmap to normalized float array for ONNX model input
     * Format: CHW (Channel, Height, Width)
     * Normalization: (pixel/255.0 - 0.5) / 0.5
     */
    fun bitmapToNormalizedFloatArray(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val floatArray = FloatArray(3 * width * height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            // Normalize: (pixel/255.0 - 0.5) / 0.5
            floatArray[i] = (r - 0.5f) / 0.5f
            floatArray[width * height + i] = (g - 0.5f) / 0.5f
            floatArray[2 * width * height + i] = (b - 0.5f) / 0.5f
        }

        return floatArray
    }

    /**
     * Resize image to target height while maintaining aspect ratio
     * Max width is enforced
     */
    fun resizeForRecognition(
        bitmap: Bitmap,
        targetHeight: Int = 48,
        maxWidth: Int = 320
    ): Bitmap {
        val height = bitmap.height
        val width = bitmap.width
        val ratio = targetHeight.toFloat() / height
        var newWidth = (width * ratio).toInt()

        if (newWidth > maxWidth) {
            newWidth = maxWidth
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, targetHeight, true)
    }

    /**
     * Crop and rotate a region from the image based on 4-point box
     */
    fun getRotateCropImage(bitmap: Bitmap, box: Array<FloatArray>): Bitmap {
        val points = orderPoints(box)

        val width1 = distance(points[0], points[1])
        val width2 = distance(points[2], points[3])
        val maxWidth = kotlin.math.max(width1, width2).toInt().coerceAtLeast(1)

        val height1 = distance(points[0], points[3])
        val height2 = distance(points[1], points[2])
        val maxHeight = kotlin.math.max(height1, height2).toInt().coerceAtLeast(1)

        val src = floatArrayOf(
            points[0][0], points[0][1],
            points[1][0], points[1][1],
            points[2][0], points[2][1],
            points[3][0], points[3][1]
        )

        val dst = floatArrayOf(
            0f, 0f,
            maxWidth.toFloat(), 0f,
            maxWidth.toFloat(), maxHeight.toFloat(),
            0f, maxHeight.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)

        val output = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)

        return output
    }

    /**
     * Order points in clockwise order: top-left, top-right, bottom-right, bottom-left
     */
    private fun orderPoints(points: Array<FloatArray>): Array<FloatArray> {
        val sorted = points.sortedWith(compareBy({ it[1] }, { it[0] }))

        val topPoints = sorted.take(2).sortedBy { it[0] }
        val bottomPoints = sorted.takeLast(2).sortedBy { it[0] }

        return arrayOf(
            topPoints[0],
            topPoints[1],
            bottomPoints[1],
            bottomPoints[0]
        )
    }

    /**
     * Calculate Euclidean distance between two points
     */
    private fun distance(p1: FloatArray, p2: FloatArray): Float {
        val dx = p1[0] - p2[0]
        val dy = p1[1] - p2[1]
        return sqrt(dx * dx + dy * dy)
    }
}
