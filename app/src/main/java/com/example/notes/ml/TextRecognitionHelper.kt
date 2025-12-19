package com.example.notes.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

import android.net.Uri
import java.io.IOException

class TextRecognitionHelper(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun extractText(bitmap: Bitmap, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                onSuccess(visionText.text)
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }
    suspend fun extractTextSuspend(bitmap: Bitmap): String = kotlin.coroutines.suspendCoroutine { continuation ->
        extractText(bitmap,
            onSuccess = { text -> continuation.resumeWith(Result.success(text)) },
            onError = { e -> continuation.resumeWith(Result.failure(e)) }
        )
    }

    suspend fun extractTextFromUri(uri: Uri): String = kotlin.coroutines.suspendCoroutine { continuation ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resumeWith(Result.success(visionText.text))
                }
                .addOnFailureListener { e ->
                    continuation.resumeWith(Result.failure(e))
                }
        } catch (e: IOException) {
            continuation.resumeWith(Result.failure(e))
        }
    }
}
