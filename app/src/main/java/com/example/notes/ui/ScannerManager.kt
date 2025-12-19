package com.example.notes.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.notes.data.NoteEntity
import com.example.notes.data.NoteEntity_
import com.example.notes.data.ObjectBoxStore
import com.example.notes.ml.ImageEmbedderHelper
import com.example.notes.ml.ImagePreprocessor
import com.example.notes.ml.PPOCRv4Helper
import com.example.notes.ml.TextEmbedderHelper
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import io.objectbox.Box
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScannerManager(private val context: Context) {
    
    val options = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setResultFormats(RESULT_FORMAT_JPEG)
        .setScannerMode(SCANNER_MODE_FULL)
        .setPageLimit(1)
        .build()

    private val embedder = ImageEmbedderHelper(context)
    private val textEmbedder = TextEmbedderHelper(context)
    private val ocrHelper = PPOCRv4Helper(context)
    
    // Processing state data class
    data class ProcessingState(
        val step: ProcessingStep = ProcessingStep.IDLE,
        val progress: Float = 0f,
        val message: String = ""
    )
    
    enum class ProcessingStep {
        IDLE,
        LOADING_IMAGE,
        GENERATING_IMAGE_EMBEDDING,
        RUNNING_OCR,
        GENERATING_TEXT_EMBEDDING,
        SAVING,
        COMPLETE,
        ERROR
    }

    fun updateEmbedderDelegate(delegate: Int) {
        embedder.setDelegate(delegate)
        textEmbedder.setDelegate(delegate)
    }
    
    fun cleanup() {
        ocrHelper.release()
    }

    fun processScanResult(
        uri: Uri, 
        noteId: Long = 0, 
        onProgress: ((ProcessingState) -> Unit)? = null,
        onComplete: (NoteEntity) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Load Bitmap
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.LOADING_IMAGE,
                    0.1f,
                    "Loading image..."
                ))
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                Log.d(TAG, "Bitmap loaded. Dimensions: ${bitmap.width}x${bitmap.height}")
                
                // 2. Generate Image Embedding
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.GENERATING_IMAGE_EMBEDDING,
                    0.2f,
                    "Generating image embedding (1024-dim vector)..."
                ))
                val result = embedder.embed(bitmap)
                val embedding = result?.embedding
                if (embedding != null) {
                    Log.d(TAG, "Image Embedding Success. Vector size: ${embedding.size}")
                } else {
                    Log.w(TAG, "Image Embedding returned null, continuing without it")
                }
                
                // 3. OCR with PP-OCRv4
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.RUNNING_OCR,
                    0.4f,
                    "Running PP-OCRv4 text recognition..."
                ))
                Log.d(TAG, "Starting PP-OCRv4 OCR...")
                
                // Preprocess image optimized for English handwriting
                val preprocessed = ImagePreprocessor.preprocessForOCR(
                    bitmap = bitmap,
                    applyGrayscale = true,
                    applyBinarization = true,
                    threshold = 0.5f,  // Lower threshold for lighter handwriting
                    applyNoiseReduction = false,  // Skip to preserve thin strokes
                    applyDeskew = true  // Enable deskew for better alignment
                )
                
                val ocrResult = ocrHelper.recognizeText(preprocessed)
                val text = ocrResult.text
                
                Log.d(TAG, "OCR Result - Text length: ${text.length}, " +
                    "Confidence: ${ocrResult.confidence}, " +
                    "Boxes: ${ocrResult.boxes.size}")
                
                // 4. Generate Text Embedding
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.GENERATING_TEXT_EMBEDDING,
                    0.7f,
                    "Generating text embedding (384-dim vector) from OCR text..."
                ))
                val textEmbedding = if (text.isNotBlank()) {
                    val result = textEmbedder.embed(text)
                    if (result != null) {
                        Log.d(TAG, "Text Embedding Success. Vector size: ${result.size}")
                        result
                    } else {
                        Log.w(TAG, "Text Embedding returned null")
                        null
                    }
                } else {
                    null
                }

                // 5. Save to DB
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.SAVING,
                    0.9f,
                    "Saving note to database..."
                ))
                val box: Box<NoteEntity> = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                        
                        val noteToSave = if (noteId > 0) {
                            val existing = box.get(noteId)
                            if (existing != null) {
                                // Update existing
                                existing.imagePath = uri.toString() // Potentially updating image
                                existing.embedding = embedding
                                existing.ocrText = text
                                existing.textEmbedding = textEmbedding
                                existing.timestamp = System.currentTimeMillis() // Update timestamp on edit?
                                existing
                            } else {
                                // Fallback if not found (shouldn't happen)
                                NoteEntity(
                                    title = "Note ${System.currentTimeMillis()}",
                                    imagePath = uri.toString(),
                                    embedding = embedding,
                                    ocrText = text,
                                    textEmbedding = textEmbedding
                                )
                            }
                        } else {
                            // Insert New
                            NoteEntity(
                                title = "Note ${System.currentTimeMillis()}",
                                imagePath = uri.toString(),
                                embedding = embedding,
                                ocrText = text,
                                textEmbedding = textEmbedding
                            )
                        }

                box.put(noteToSave)
                
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.COMPLETE,
                    1.0f,
                    "Note saved successfully!"
                ))
                
                withContext(Dispatchers.Main) {
                    onComplete(noteToSave)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.ERROR,
                    0f,
                    "Error: ${e.message}"
                ))
            }
        }
    }
    data class SearchResult(val note: NoteEntity, val score: Double, val textScore: Double? = null, val imageScore: Double? = null)

    companion object {
        private const val SEARCH_THRESHOLD = 0.45 // Distance threshold (Lower is better). 0.45 = Cosine Similarity > 0.55
        private const val TAG = "ScannerManager"
    }

    suspend fun search(
        uri: Uri, 
        onProgress: ((ProcessingState) -> Unit)? = null,
        onComplete: (List<SearchResult>) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.LOADING_IMAGE,
                    0.1f,
                    "Loading search image..."
                ))
                Log.d(TAG, "Starting search...")
                
                // Parallel Processing using async
                val imageEmbeddingJob = async {
                    onProgress?.invoke(ProcessingState(
                        ProcessingStep.GENERATING_IMAGE_EMBEDDING,
                        0.2f,
                        "Generating image embedding for search..."
                    ))
                    Log.d(TAG, "Generating image embedding...")
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val result = embedder.embed(bitmap)?.embedding
                    Log.d(TAG, "Image embedding generated. Size: ${result?.size}")
                    result
                }

                val textEmbeddingJob = async {
                    try {
                        onProgress?.invoke(ProcessingState(
                            ProcessingStep.RUNNING_OCR,
                            0.4f,
                            "Running OCR on search image..."
                        ))
                        
                        // Load bitmap for OCR
                        val inputStream2 = context.contentResolver.openInputStream(uri)
                        val bitmap2 = BitmapFactory.decodeStream(inputStream2)
                        
                        // Preprocess optimized for English handwriting
                        val preprocessed = ImagePreprocessor.preprocessForOCR(
                            bitmap = bitmap2,
                            applyGrayscale = true,
                            applyBinarization = true,
                            threshold = 0.5f,  // Lower threshold for lighter handwriting
                            applyNoiseReduction = false,  // Skip to preserve thin strokes
                            applyDeskew = true  // Enable deskew for better alignment
                        )
                        
                        val ocrResult = ocrHelper.recognizeText(preprocessed)
                        val text = ocrResult.text
                        
                        Log.d(TAG, "Search OCR completed. Text length: ${text.length}")
                        
                        if (text.isNotBlank()) {
                            onProgress?.invoke(ProcessingState(
                                ProcessingStep.GENERATING_TEXT_EMBEDDING,
                                0.5f,
                                "Generating text embedding for search..."
                            ))
                            textEmbedder.embed(text)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during OCR/Text embedding in search", e)
                        e.printStackTrace()
                        null
                    }
                }

                val imageEmbedding = imageEmbeddingJob.await()
                val textEmbedding = textEmbeddingJob.await()

                if (imageEmbedding == null) {
                    Log.w(TAG, "Image embedding failed, returning empty results.")
                    withContext(Dispatchers.Main) { onComplete(emptyList()) }
                    return@withContext
                }

                onProgress?.invoke(ProcessingState(
                    ProcessingStep.SAVING,  // Reusing SAVING for "Searching database"
                    0.6f,
                    "Searching database..."
                ))
                
                val box: Box<NoteEntity> = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                
                // 1. Image Search
                Log.d(TAG, "Executing ObjectBox Image Search...")
                val imageResults = try {
                    box.query(NoteEntity_.embedding.nearestNeighbors(imageEmbedding, 10))
                        .build()
                        .findWithScores()
                        .map { SearchResult(it.get(), it.score) }
                        .filter { it.score < SEARCH_THRESHOLD }
                } catch (e: Exception) {
                    Log.e(TAG, "Crash/Error during Image Search: ${e.message}", e)
                    emptyList()
                }
                Log.d(TAG, "Image Search complete. Found: ${imageResults.size}")

                // 2. Text Search (if available)
                val textResults = if (textEmbedding != null) {
                    Log.d(TAG, "Executing ObjectBox Text Search...")
                    try {
                        box.query(NoteEntity_.textEmbedding.nearestNeighbors(textEmbedding, 10))
                            .build()
                            .findWithScores()
                            .map { SearchResult(it.get(), it.score) }
                            .filter { it.score < SEARCH_THRESHOLD }
                    } catch (e: Exception) {
                        Log.e(TAG, "Crash/Error during Text Search: ${e.message}", e)
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                Log.d(TAG, "Text Search complete. Found: ${textResults.size}")

                // 3. Merge Strategies
                // Prioritize text results:
                // - If in both: Weighted average (0.7 * Text + 0.3 * Image)
                // - If only in Text: Keep original score
                // - If only in Image: Apply small penalty (1.1x) to deprioritize vs strong text matches
                
                val combinedMap = mutableMapOf<Long, MutableList<Pair<Double, String>>>() // Score, Source

                imageResults.forEach { 
                    combinedMap.getOrPut(it.note.id) { mutableListOf() }.add(it.score to "image")
                }
                textResults.forEach { 
                    combinedMap.getOrPut(it.note.id) { mutableListOf() }.add(it.score to "text")
                }

                val finalResults = combinedMap.mapNotNull { (id, entries) ->
                    val note = box.get(id)
                    
                    val textEntry = entries.find { it.second == "text" }
                    val imageEntry = entries.find { it.second == "image" }
                    
                    val finalScore = when {
                        textEntry != null -> {
                             // Document type (has text match): 
                             // "Consider text embedding score alone"
                             textEntry.first
                        }
                        imageEntry != null -> {
                             // Others (Image only):
                             // "Consider image embedding"
                             imageEntry.first 
                        }
                        else -> 1.0 // Should not happen
                    }
                    
                    if (finalScore < SEARCH_THRESHOLD) {
                        SearchResult(
                            note, 
                            finalScore, 
                            textScore = textEntry?.first, 
                            imageScore = imageEntry?.first
                        )
                    } else {
                        null
                    }
                }.sortedBy { it.score } // Sort by final score (ascending)

                 onProgress?.invoke(ProcessingState(
                     ProcessingStep.COMPLETE,
                     1.0f,
                     "Found ${finalResults.size} matching notes"
                 ))
                 
                 Log.d(TAG, "Search finished. Returning ${finalResults.size} results.")
                 withContext(Dispatchers.Main) {
                     onComplete(finalResults)
                 }

            } catch (e: Exception) {
                Log.e(TAG, "Critical error in search: ${e.message}", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) { onComplete(emptyList()) }
            }
        }
    }

    suspend fun findSimilarInCollection(bitmap: Bitmap, collection: String): SearchResult? {
        return withContext(Dispatchers.IO) {
            try {
                val embedding = embedder.embed(bitmap)?.embedding ?: return@withContext null
                val box: Box<NoteEntity> = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                
                // Find nearest neighbors that are ALSO in the specific collection
                // ObjectBox currently doesn't support complex filtering on vector query directly in one go easily depending on version,
                // but we can query vector nearest first, then filter by collection in memory if finding top 1.
                // Or use a query builder with filter.
                
                // Strategy: Get top 5 visual matches, check if any belong to 'collection' and are < THRESHOLD.
                val nearest = box.query(NoteEntity_.embedding.nearestNeighbors(embedding, 10))
                    .build()
                    .findWithScores()
                
                val bestMatch = nearest
                    .map { SearchResult(it.get(), it.score) }
                    .filter { it.note.collection == collection && it.score < SEARCH_THRESHOLD }
                    .minByOrNull { it.score }
                    
                bestMatch
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
