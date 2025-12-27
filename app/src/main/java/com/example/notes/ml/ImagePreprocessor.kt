package com.example.notes.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log

/**
 * Image preprocessing utilities
 * Loads images from Google ML Kit Document Scanner output (URI)
 */
object ImagePreprocessor {
    private const val TAG = "ImagePreprocessor"
    private const val MAX_DIMENSION = 2048

    /**
     * Load bitmap from URI (from Google ML Kit Document Scanner)
     * Supports downsampling for large images
     */
    fun loadBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_DIMENSION
    ): Bitmap {
        Log.d(TAG, "Loading bitmap from URI: $uri")

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open URI: $uri")

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        val width = options.outWidth
        val height = options.outHeight
        var inSampleSize = 1

        if (height > maxDimension || width > maxDimension) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= maxDimension &&
                halfWidth / inSampleSize >= maxDimension) {
                inSampleSize *= 2
            }
        }

        options.inSampleSize = inSampleSize
        options.inJustDecodeBounds = false

        val inputStream2 = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open URI: $uri")

        val bitmap = BitmapFactory.decodeStream(inputStream2, null, options)
            ?: throw IllegalArgumentException("Cannot decode bitmap from URI: $uri")

        inputStream2.close()

        Log.d(TAG, "Loaded bitmap: ${bitmap.width}x${bitmap.height} (sample size: $inSampleSize)")
        return bitmap
    }
}
