package com.example.notes.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.notes.data.NoteEntity
import com.example.notes.data.NoteEntity_
import com.example.notes.data.ObjectBoxStore
import com.example.notes.ml.ImageEmbedderHelper
import com.example.notes.ml.ImagePreprocessor
import com.example.notes.ml.OCREngine
import com.example.notes.ml.OCREngineFactory
import com.example.notes.ml.TextEmbedderHelperOnnx
import com.example.notes.ml.TrOCREncoderHelper
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import io.objectbox.Box
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScannerManager(
    private val context: Context
) {

    val options = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setResultFormats(RESULT_FORMAT_JPEG)
        .setScannerMode(SCANNER_MODE_FULL)
        .setPageLimit(1)
        .build()

    private val embedder = ImageEmbedderHelper(context)
    // Shared text embedder (all-MiniLM-L6-v2) used for BOTH ML Kit and ColorBased OCR text
    private val textEmbedder = TextEmbedderHelperOnnx(context)
    private val trocrEncoder = TrOCREncoderHelper(context)
    private val clipEmbedder = com.example.notes.ml.CLIPImageEmbedder(context)
    
    // OCR Engines: ML Kit (fast, accurate for print) and ColorBased (handwriting)
    private val mlKitOCR: OCREngine = OCREngineFactory.createMLKitOCR(context)
    private val colorBasedOCR: OCREngine = OCREngineFactory.create(context)
    private var useMLKit = true // Default to ML Kit for better accuracy
    
    // Processing state data class
    data class ProcessingState(
        val step: ProcessingStep = ProcessingStep.IDLE,
        val progress: Float = 0f,
        val message: String = "",
        val bitmap: Bitmap? = null,
        val extractedText: String = "",
        val imageValidation: String = ""
    )
    
    enum class ProcessingStep {
        IDLE,
        LOADING_IMAGE,
        GENERATING_IMAGE_EMBEDDING,
        GENERATING_TROCR_EMBEDDING,
        RUNNING_OCR,
        GENERATING_TEXT_EMBEDDING,
        SAVING,
        COMPLETE,
        ERROR
    }

    fun updateEmbedderDelegate(delegate: Int) {
        embedder.setDelegate(delegate)
        textEmbedder.setDelegate(delegate)
        trocrEncoder.setDelegate(delegate)
    }
    
    fun setOCREngine(useMLKitEngine: Boolean) {
        useMLKit = useMLKitEngine
        Log.d(TAG, "OCR engine switched to: ${if (useMLKit) "ML Kit" else "ColorBased"}")
    }
    
    /**
     * Verify all embedders are properly initialized and ready
     * @return Map of embedder status (true = ready, false = not ready)
     */
    fun checkEmbeddersStatus(): Map<String, Boolean> {
        // Initialize CLIP embedder if not ready
        if (!clipEmbedder.ready) {
            clipEmbedder.initialize()
        }

        val status = mapOf(
            "ImageEmbedder" to (embedder != null),
            "CLIPEmbedder" to clipEmbedder.ready,
            "TextEmbedder" to (textEmbedder != null),
            "TrOCREncoder" to (trocrEncoder != null),
            "MLKitOCR" to (mlKitOCR != null),
            "ColorBasedOCR" to (colorBasedOCR != null)
        )
        
        Log.d(TAG, "===== EMBEDDERS STATUS =====")
        status.forEach { (name, ready) ->
            Log.d(TAG, "$name: ${if (ready) "✓ Ready" else "✗ Not Ready"}")
        }
        Log.d(TAG, "============================")
        
        return status
    }
    
    fun cleanup() {
        mlKitOCR.release()
        colorBasedOCR.release()
        trocrEncoder.close()
        clipEmbedder.close()
    }

    fun processScanResult(
        uri: Uri, 
        noteId: Long = 0, 
        onProgress: ((ProcessingState) -> Unit)? = null,
        onWaitingForConfirmation: (suspend () -> Unit)? = null,
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
                val bitmap = ImagePreprocessor.loadBitmapFromUri(context, uri)
                Log.d(TAG, "Bitmap loaded. Dimensions: ${bitmap.width}x${bitmap.height}")
                
                val validation = buildString {
                    append("✓ Size: ${bitmap.width}x${bitmap.height}\n")
                    if (bitmap.width < 100 || bitmap.height < 100) {
                        append("⚠ Image too small (min 100x100)\n")
                    } else {
                        append("✓ Size adequate\n")
                    }
                    if (bitmap.width > 4000 || bitmap.height > 4000) {
                        append("⚠ Image very large (may be slow)\n")
                    } else {
                        append("✓ Size optimal\n")
                    }
                }
                
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.LOADING_IMAGE,
                    0.15f,
                    "Image loaded. Review before processing.",
                    bitmap = bitmap,
                    imageValidation = validation
                ))
                
                onWaitingForConfirmation?.invoke()
                
                // 2. Generate Image Embedding (ALWAYS ENABLED)
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.GENERATING_IMAGE_EMBEDDING,
                    0.2f,
                    "Generating image embedding (1280-dim vector)..."
                ))
                val result = embedder.embed(bitmap)
                val embedding = result?.embedding
                if (embedding != null) {
                    Log.d(TAG, "Image Embedding Success. Vector size: ${embedding.size}")
                } else {
                    Log.w(TAG, "Image Embedding returned null, continuing without it")
                }
                
                // 2b. Generate TrOCR Visual Embedding (ALWAYS ENABLED)
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.GENERATING_TROCR_EMBEDDING,
                    0.3f,
                    "Generating TrOCR visual embedding (768-dim vector)..."
                ))
                val trocrEmbedding = try {
                    val trocrResult = trocrEncoder.embedCLS(bitmap)
                    if (trocrResult != null) {
                        Log.d(TAG, "TrOCR Embedding Success. Vector size: ${trocrResult.size}")
                        trocrResult
                    } else {
                        Log.w(TAG, "TrOCR Embedding returned null")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "TrOCR Embedding failed: ${e.message}", e)
                    null
                }

                // 2c. Generate CLIP Image Embedding (512-dim, robust to variations)
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.GENERATING_IMAGE_EMBEDDING,
                    0.35f,
                    "Generating CLIP image embedding (512-dim vector)..."
                ))

                // Initialize CLIP if not ready
                if (!clipEmbedder.ready) {
                    Log.d(TAG, "Initializing CLIP embedder...")
                    clipEmbedder.initialize()
                }

                val clipEmbedding = try {
                    if (clipEmbedder.ready) {
                        val clipResult = clipEmbedder.embed(bitmap)
                        if (clipResult != null) {
                            Log.d(TAG, "CLIP Embedding Success. Vector size: ${clipResult.embedding.size}")
                            clipResult.embedding
                        } else {
                            Log.w(TAG, "CLIP Embedding returned null")
                            null
                        }
                    } else {
                        Log.w(TAG, "CLIP embedder not ready, skipping")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "CLIP Embedding failed: ${e.message}", e)
                    null
                }

                // 3. Run BOTH OCR Engines
                // 3a. ML Kit OCR
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.RUNNING_OCR,
                    0.4f,
                    "Running ML Kit OCR for printed text..."
                ))
                Log.d(TAG, "Starting ML Kit OCR...")
                val mlKitResult = mlKitOCR.recognizeText(bitmap)
                val mlKitText = mlKitResult.text
                Log.d(TAG, "ML Kit OCR Result - Text length: ${mlKitText.length}, " +
                    "Confidence: ${mlKitResult.confidence}")
                
                // 3b. ColorBased OCR
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.RUNNING_OCR,
                    0.5f,
                    "Running ColorBased OCR for handwriting..."
                ))
                Log.d(TAG, "Starting ColorBased OCR...")
                val colorBasedResult = colorBasedOCR.recognizeText(bitmap)
                val colorBasedText = colorBasedResult.text
                Log.d(TAG, "ColorBased OCR Result - Text length: ${colorBasedText.length}, " +
                    "Confidence: ${colorBasedResult.confidence}")
                
                // Combine both results for display
                val combinedText = buildString {
                    if (mlKitText.isNotBlank()) {
                        append("ML Kit:\n$mlKitText\n")
                    }
                    if (colorBasedText.isNotBlank()) {
                        if (mlKitText.isNotBlank()) append("\n---\n\n")
                        append("ColorBased:\n$colorBasedText")
                    }
                }
                
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.RUNNING_OCR,
                    0.6f,
                    "Both OCR engines complete. Review extracted text.",
                    extractedText = combinedText.ifBlank { "(No text detected)" }
                ))
                
                onWaitingForConfirmation?.invoke()
                
                // 4. Generate Text Embeddings from Both OCR Results (using same all-MiniLM-L6-v2 model)
                // 4a. ML Kit Text Embedding (all-MiniLM-L6-v2)
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.GENERATING_TEXT_EMBEDDING,
                    0.7f,
                    "Generating text embedding from ML Kit OCR using all-MiniLM-L6-v2 (384-dim)..."
                ))
                val mlKitTextEmbedding = if (mlKitText.isNotBlank()) {
                    try {
                        // Using shared textEmbedder instance (all-MiniLM-L6-v2)
                        val result = textEmbedder.embed(mlKitText)
                        if (result != null) {
                            Log.d(TAG, "ML Kit Text Embedding Success. Vector size: ${result.size}")
                            result
                        } else {
                            Log.w(TAG, "ML Kit Text Embedding returned null")
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "ML Kit Text Embedding failed: ${e.message}", e)
                        null
                    }
                } else {
                    Log.d(TAG, "ML Kit text is blank, skipping embedding")
                    null
                }
                
                // 4b. ColorBased Text Embedding (all-MiniLM-L6-v2)
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.GENERATING_TEXT_EMBEDDING,
                    0.75f,
                    "Generating text embedding from ColorBased OCR using all-MiniLM-L6-v2 (384-dim)..."
                ))
                val colorBasedTextEmbedding = if (colorBasedText.isNotBlank()) {
                    try {
                        // Using same shared textEmbedder instance (all-MiniLM-L6-v2)
                        val result = textEmbedder.embed(colorBasedText)
                        if (result != null) {
                            Log.d(TAG, "ColorBased Text Embedding Success. Vector size: ${result.size}")
                            result
                        } else {
                            Log.w(TAG, "ColorBased Text Embedding returned null")
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "ColorBased Text Embedding failed: ${e.message}", e)
                        null
                    }
                } else {
                    Log.d(TAG, "ColorBased text is blank, skipping embedding")
                    null
                }

                // 5. Save to DB
                onProgress?.invoke(ProcessingState(
                    ProcessingStep.SAVING,
                    0.9f,
                    "Saving note with all embeddings to database..."
                ))
                val box: Box<NoteEntity> = ObjectBoxStore.store.boxFor(NoteEntity::class.java)
                        
                val noteToSave = if (noteId > 0) {
                    val existing = box.get(noteId)
                    if (existing != null) {
                        existing.imagePath = uri.toString()
                        existing.embedding = embedding
                        existing.clipEmbedding = clipEmbedding
                        existing.mlKitText = mlKitText
                        existing.colorBasedText = colorBasedText
                        existing.ocrText = combinedText // Legacy field
                        existing.mlKitTextEmbedding = mlKitTextEmbedding
                        existing.colorBasedTextEmbedding = colorBasedTextEmbedding
                        existing.textEmbedding = mlKitTextEmbedding // Legacy field (prefer ML Kit)
                        existing.trocrEmbedding = trocrEmbedding
                        existing.timestamp = System.currentTimeMillis()
                        existing
                    } else {
                        NoteEntity(
                            title = "Note ${System.currentTimeMillis()}",
                            imagePath = uri.toString(),
                            embedding = embedding,
                            clipEmbedding = clipEmbedding,
                            mlKitText = mlKitText,
                            colorBasedText = colorBasedText,
                            ocrText = combinedText,
                            mlKitTextEmbedding = mlKitTextEmbedding,
                            colorBasedTextEmbedding = colorBasedTextEmbedding,
                            textEmbedding = mlKitTextEmbedding,
                            trocrEmbedding = trocrEmbedding
                        )
                    }
                } else {
                    NoteEntity(
                        title = "Note ${System.currentTimeMillis()}",
                        imagePath = uri.toString(),
                        embedding = embedding,
                        clipEmbedding = clipEmbedding,
                        mlKitText = mlKitText,
                        colorBasedText = colorBasedText,
                        ocrText = combinedText,
                        mlKitTextEmbedding = mlKitTextEmbedding,
                        colorBasedTextEmbedding = colorBasedTextEmbedding,
                        textEmbedding = mlKitTextEmbedding,
                        trocrEmbedding = trocrEmbedding
                    )
                }

                val savedNoteId = box.put(noteToSave)

                // Log all embeddings status
                Log.d(TAG, "===== EMBEDDINGS SUMMARY =====")
                Log.d(TAG, "Saved note with ID: $savedNoteId")
                Log.d(TAG, "Image Embedding (MobileNetV3): ${if (embedding != null) "${embedding.size}-dim ✓" else "null ✗"}")
                if (embedding != null) {
                    val min = embedding.minOrNull() ?: 0f
                    val max = embedding.maxOrNull() ?: 0f
                    val norm = kotlin.math.sqrt(embedding.map { it * it }.sum())
                    Log.d(TAG, "  Stats - min: $min, max: $max, norm: $norm")
                }
                Log.d(TAG, "Image Embedding (CLIP): ${if (clipEmbedding != null) "${clipEmbedding.size}-dim ✓" else "null ✗"}")
                if (clipEmbedding != null) {
                    val min = clipEmbedding.minOrNull() ?: 0f
                    val max = clipEmbedding.maxOrNull() ?: 0f
                    val norm = kotlin.math.sqrt(clipEmbedding.map { it * it }.sum())
                    Log.d(TAG, "  Stats - min: $min, max: $max, norm: $norm")
                }
                Log.d(TAG, "TrOCR Embedding: ${if (trocrEmbedding != null) "${trocrEmbedding.size}-dim ✓" else "null ✗"}")
                Log.d(TAG, "ML Kit Text Embedding (all-MiniLM-L6-v2): ${if (mlKitTextEmbedding != null) "${mlKitTextEmbedding.size}-dim ✓" else "null ✗"}")
                Log.d(TAG, "ColorBased Text Embedding (all-MiniLM-L6-v2): ${if (colorBasedTextEmbedding != null) "${colorBasedTextEmbedding.size}-dim ✓" else "null ✗"}")
                Log.d(TAG, "Note: Both text embeddings use the SAME all-MiniLM-L6-v2 model")
                Log.d(TAG, "Total active embeddings: ${listOf(embedding, clipEmbedding, trocrEmbedding, mlKitTextEmbedding, colorBasedTextEmbedding).count { it != null }}/5")

                // Verify it was saved
                val totalNotesInDb = box.count()
                Log.d(TAG, "Total notes now in database: $totalNotesInDb")
                Log.d(TAG, "==============================")

                onProgress?.invoke(ProcessingState(
                    ProcessingStep.COMPLETE,
                    1.0f,
                    "Note saved with ${listOf(embedding, clipEmbedding, trocrEmbedding, mlKitTextEmbedding, colorBasedTextEmbedding).count { it != null }}/5 embeddings!"
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
    
    data class SearchResult(
        val note: NoteEntity,
        val score: Double,
        val imageScore: Double? = null,
        val clipScore: Double? = null,
        val trocrScore: Double? = null,
        val mlKitTextScore: Double? = null,
        val colorBasedTextScore: Double? = null
    )

    companion object {
        // Cosine distance thresholds: 0.0 = identical, 2.0 = opposite
        // Lower threshold = stricter matching (only very similar results)

        // Individual thresholds for each embedding type
        private const val CLIP_THRESHOLD = 0.2          // CLIP image similarity (strict)
        private const val TROCR_THRESHOLD = 0.2         // TrOCR visual patterns (strict)
        private const val MLKIT_TEXT_THRESHOLD = 0.2   // ML Kit semantic text (strict)
        private const val COLORBASED_TEXT_THRESHOLD = 0.2  // ColorBased text (strict)

        // Legacy unified threshold (for backward compatibility)
        private const val SEARCH_THRESHOLD = 0.2  // Fallback if individual not specified

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

                // Parallel Processing
                // MobileNetV3 disabled - using CLIP only for image search
                // val imageEmbeddingJob = async {
                //     onProgress?.invoke(ProcessingState(
                //         ProcessingStep.GENERATING_IMAGE_EMBEDDING,
                //         0.2f,
                //         "Generating image embedding for search..."
                //     ))
                //     Log.d(TAG, "Generating MobileNetV3 embedding...")
                //     val bitmap = ImagePreprocessor.loadBitmapFromUri(context, uri)
                //     val result = embedder.embed(bitmap)?.embedding
                //     Log.d(TAG, "MobileNetV3 embedding generated. Size: ${result?.size}")
                //     result
                // }

                val clipEmbeddingJob = async {
                    try {
                        onProgress?.invoke(ProcessingState(
                            ProcessingStep.GENERATING_IMAGE_EMBEDDING,
                            0.22f,
                            "Generating CLIP image embedding for search..."
                        ))
                        Log.d(TAG, "===== CLIP EMBEDDING GENERATION =====")
                        Log.d(TAG, "CLIP embedder ready: ${clipEmbedder.ready}")

                        // Initialize CLIP if not ready
                        if (!clipEmbedder.ready) {
                            Log.d(TAG, "Initializing CLIP embedder...")
                            clipEmbedder.initialize()
                            Log.d(TAG, "CLIP embedder initialized: ${clipEmbedder.ready}")
                        }

                        val bitmap = ImagePreprocessor.loadBitmapFromUri(context, uri)
                        Log.d(TAG, "Bitmap loaded: ${bitmap.width}x${bitmap.height}")

                        val result = if (clipEmbedder.ready) {
                            val embedding = clipEmbedder.embed(bitmap)?.embedding
                            if (embedding != null) {
                                Log.d(TAG, "✓ CLIP embedding generated successfully")
                                Log.d(TAG, "  Size: ${embedding.size}")
                                Log.d(TAG, "  Norm: ${kotlin.math.sqrt(embedding.map { it * it }.sum())}")
                                Log.d(TAG, "  Range: [${embedding.minOrNull()}, ${embedding.maxOrNull()}]")
                            } else {
                                Log.e(TAG, "✗ CLIP embed() returned null!")
                            }
                            embedding
                        } else {
                            Log.e(TAG, "✗ CLIP embedder not ready after initialization attempt!")
                            null
                        }
                        Log.d(TAG, "=====================================")
                        result
                    } catch (e: Exception) {
                        Log.e(TAG, "✗ CRITICAL: CLIP embedding failed with exception", e)
                        Log.e(TAG, "  Exception type: ${e.javaClass.simpleName}")
                        Log.e(TAG, "  Message: ${e.message}")
                        e.printStackTrace()
                        null
                    }
                }

                val trocrEmbeddingJob = async {
                    try {
                        onProgress?.invoke(ProcessingState(
                            ProcessingStep.GENERATING_TROCR_EMBEDDING,
                            0.25f,
                            "Generating TrOCR visual embedding for search..."
                        ))
                        Log.d(TAG, "Generating TrOCR embedding...")
                        val bitmap = ImagePreprocessor.loadBitmapFromUri(context, uri)
                        val result = try {
                            trocrEncoder.embedCLS(bitmap)
                        } catch (e: Exception) {
                            Log.e(TAG, "TrOCR embedding error: ${e.message}")
                            null
                        }
                        Log.d(TAG, "TrOCR embedding generated. Size: ${result?.size}")
                        result
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during TrOCR embedding in search", e)
                        null
                    }
                }

                val textEmbeddingJob = async {
                    try {
                        onProgress?.invoke(ProcessingState(
                            ProcessingStep.RUNNING_OCR,
                            0.3f,
                            "Running both OCR engines on search image..."
                        ))

                        val bitmap2 = ImagePreprocessor.loadBitmapFromUri(context, uri)

                        // Run ML Kit OCR
                        Log.d(TAG, "===== ML KIT OCR (SEARCH QUERY) =====")
                        val mlKitResult = mlKitOCR.recognizeText(bitmap2)
                        val mlKitText = mlKitResult.text
                        Log.d(TAG, "ML Kit OCR completed")
                        Log.d(TAG, "  Text length: ${mlKitText.length} characters")
                        Log.d(TAG, "  Confidence: ${mlKitResult.confidence}")
                        Log.d(TAG, "  Text preview: \"${mlKitText.take(100).replace("\n", " ")}${if (mlKitText.length > 100) "..." else ""}\"")

                        // Run ColorBased OCR
                        Log.d(TAG, "===== COLORBASED OCR (SEARCH QUERY) =====")
                        val colorBasedResult = colorBasedOCR.recognizeText(bitmap2)
                        val colorBasedText = colorBasedResult.text
                        Log.d(TAG, "ColorBased OCR completed")
                        Log.d(TAG, "  Text length: ${colorBasedText.length} characters")
                        Log.d(TAG, "  Confidence: ${colorBasedResult.confidence}")
                        Log.d(TAG, "  Text preview: \"${colorBasedText.take(100).replace("\n", " ")}${if (colorBasedText.length > 100) "..." else ""}\"")

                        // Generate embeddings for both
                        onProgress?.invoke(ProcessingState(
                            ProcessingStep.GENERATING_TEXT_EMBEDDING,
                            0.4f,
                            "Generating text embeddings for search..."
                        ))

                        Log.d(TAG, "===== GENERATING TEXT EMBEDDINGS =====")
                        val mlKitEmbedding = if (mlKitText.isNotBlank()) {
                            Log.d(TAG, "Generating ML Kit text embedding...")
                            val embedding = textEmbedder.embed(mlKitText)
                            if (embedding != null) {
                                val norm = kotlin.math.sqrt(embedding.map { it * it }.sum())
                                Log.d(TAG, "✓ ML Kit embedding generated")
                                Log.d(TAG, "  Size: ${embedding.size}, Norm: $norm")
                                if (Math.abs(norm - 1.0) > 0.1) {
                                    Log.w(TAG, "  ⚠️ WARNING: Embedding not L2-normalized!")
                                }
                            } else {
                                Log.e(TAG, "✗ ML Kit embedding generation failed (returned null)")
                            }
                            embedding
                        } else {
                            Log.w(TAG, "ML Kit text is blank, skipping embedding")
                            null
                        }
                        
                        val colorBasedEmbedding = if (colorBasedText.isNotBlank()) {
                            textEmbedder.embed(colorBasedText)
                        } else null
                        
                        Pair(mlKitEmbedding, colorBasedEmbedding)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during OCR/Text embedding in search", e)
                        e.printStackTrace()
                        Pair(null, null)
                    }
                }

                // MobileNetV3 disabled - using CLIP only for image search
                // val imageEmbedding = imageEmbeddingJob.await()
                val clipEmbedding = clipEmbeddingJob.await()
                val trocrEmbedding = trocrEmbeddingJob.await()
                val (mlKitTextEmbedding, colorBasedTextEmbedding) = textEmbeddingJob.await()

                if (clipEmbedding == null) {
                    Log.w(TAG, "CLIP embedding failed, returning empty results.")
                    withContext(Dispatchers.Main) { onComplete(emptyList()) }
                    return@withContext
                }

                onProgress?.invoke(ProcessingState(
                    ProcessingStep.SAVING,
                    0.7f,
                    "Searching database with 2 active embeddings (CLIP image + ML Kit text)..."
                ))
                
                val box: Box<NoteEntity> = ObjectBoxStore.store.boxFor(NoteEntity::class.java)

                // Log total notes in database
                val totalNotes = box.count()
                Log.d(TAG, "===== DATABASE INFO =====")
                Log.d(TAG, "Total notes in database: $totalNotes")
                if (totalNotes > 0) {
                    val allNotes = box.all
                    allNotes.forEachIndexed { idx, note ->
                        Log.d(TAG, "  Note $idx: ID=${note.id}, title=${note.title}")
                        Log.d(TAG, "    Has CLIP embedding: ${note.clipEmbedding != null}")
                        Log.d(TAG, "    Has MobileNet embedding: ${note.embedding != null} (not used in search)")
                        Log.d(TAG, "    Has TrOCR embedding: ${note.trocrEmbedding != null}")
                        Log.d(TAG, "    Has ML Kit text embedding: ${note.mlKitTextEmbedding != null}")
                        Log.d(TAG, "    Has ColorBased text embedding: ${note.colorBasedTextEmbedding != null}")
                    }
                }
                Log.d(TAG, "=========================")

                // 1. MobileNetV3 Image Search - DISABLED (using CLIP only)
                // Log.d(TAG, "===== STARTING MOBILENETV3 SEARCH =====")
                // Log.d(TAG, "Query embedding stats:")
                // val queryMin = imageEmbedding.minOrNull() ?: 0f
                // val queryMax = imageEmbedding.maxOrNull() ?: 0f
                // val queryMean = imageEmbedding.average().toFloat()
                // val queryNorm = kotlin.math.sqrt(imageEmbedding.map { it * it }.sum())
                // Log.d(TAG, "  min: $queryMin, max: $queryMax, mean: $queryMean, norm: $queryNorm")

                // val imageResults = try {
                //     val results = box.query(NoteEntity_.embedding.nearestNeighbors(imageEmbedding, 10))
                //         .build()
                //         .findWithScores()
                //     ...
                // } catch (e: Exception) {
                //     Log.e(TAG, "Crash/Error during MobileNetV3 Search: ${e.message}", e)
                //     emptyList()
                // }
                val imageResults = emptyList<SearchResult>()
                Log.d(TAG, "MobileNetV3 Search DISABLED (using CLIP only)")
                Log.d(TAG, "")

                // 2. CLIP Image Search (Primary image search method)
                Log.d(TAG, "===== STARTING CLIP SEARCH =====")
                val clipResults = if (clipEmbedding != null) {
                    try {
                        Log.d(TAG, "✓ CLIP query embedding available")
                        Log.d(TAG, "CLIP Query embedding stats:")
                        val clipQueryMin = clipEmbedding.minOrNull() ?: 0f
                        val clipQueryMax = clipEmbedding.maxOrNull() ?: 0f
                        val clipQueryMean = clipEmbedding.average().toFloat()
                        val clipQueryNorm = kotlin.math.sqrt(clipEmbedding.map { it * it }.sum())
                        Log.d(TAG, "  min: $clipQueryMin, max: $clipQueryMax, mean: $clipQueryMean, norm: $clipQueryNorm")

                        // Check how many notes have CLIP embeddings
                        val notesWithClip = box.all.count { it.clipEmbedding != null }
                        val totalNotes = box.count()
                        Log.d(TAG, "Database: $notesWithClip/$totalNotes notes have CLIP embeddings")
                        if (notesWithClip == 0) {
                            Log.w(TAG, "⚠️ WARNING: No notes in database have CLIP embeddings!")
                            Log.w(TAG, "   Notes need to be re-scanned to generate CLIP embeddings")
                        }

                        val results = box.query(NoteEntity_.clipEmbedding.nearestNeighbors(clipEmbedding, 10))
                            .build()
                            .findWithScores()

                        Log.d(TAG, "")
                        Log.d(TAG, "========================================")
                        Log.d(TAG, "ALL CLIP SCORES (BEFORE FILTERING)")
                        Log.d(TAG, "CLIP Threshold: $CLIP_THRESHOLD")
                        Log.d(TAG, "========================================")

                        results.forEachIndexed { index, scoredResult ->
                            val note = scoredResult.get()
                            val score = scoredResult.score
                            val willMatch = if (score < CLIP_THRESHOLD) "✓ WILL MATCH" else "✗ FILTERED OUT"

                            Log.d(TAG, "")
                            Log.d(TAG, "CLIP [$index]: ${note.title}")
                            Log.d(TAG, "        ID: ${note.id}")
                            Log.d(TAG, "        SCORE: $score")
                            Log.d(TAG, "        STATUS: $willMatch")

                            if (note.clipEmbedding != null) {
                                val storedNorm = kotlin.math.sqrt(note.clipEmbedding!!.map { it * it }.sum())
                                Log.d(TAG, "        Norm: $storedNorm")
                            } else {
                                Log.w(TAG, "        ⚠️ NULL CLIP embedding!")
                            }
                        }

                        Log.d(TAG, "")
                        Log.d(TAG, "========================================")
                        Log.d(TAG, "CLIP SEARCH RESULTS:")
                        Log.d(TAG, "  Matches passing threshold (<$CLIP_THRESHOLD): ${results.count { it.score < CLIP_THRESHOLD }}")
                        Log.d(TAG, "  Other notes with CLIP scores (>$CLIP_THRESHOLD): ${results.count { it.score >= CLIP_THRESHOLD }}")
                        Log.d(TAG, "========================================")

                        // Store ALL CLIP scores (not just those passing threshold)
                        // This ensures notes found by other searches still show their CLIP scores
                        val allClipScores = results.map { SearchResult(it.get(), it.score) }

                        // But only return matches passing threshold for search results
                        val filteredClipResults = allClipScores.filter { it.score < CLIP_THRESHOLD }

                        Log.d(TAG, "")
                        Log.d(TAG, "FINAL CLIP RESULTS: ${filteredClipResults.size} matches")
                        if (filteredClipResults.isEmpty()) {
                            Log.w(TAG, "⚠️ No CLIP matches found (all scores > $CLIP_THRESHOLD)")
                            if (notesWithClip == 0) {
                                Log.e(TAG, "✗ Root cause: Database has NO notes with CLIP embeddings!")
                                Log.e(TAG, "  Solution: Re-scan your notes to generate CLIP embeddings")
                            }
                        } else {
                            filteredClipResults.forEachIndexed { idx, result ->
                                Log.d(TAG, "  ✓ Match $idx: ${result.note.title} (score: ${result.score})")
                            }
                        }
                        Log.d(TAG, "========================================")

                        // Return ALL scores (for merging), not just filtered ones
                        allClipScores
                    } catch (e: Exception) {
                        Log.e(TAG, "✗ Crash/Error during CLIP Search: ${e.message}", e)
                        e.printStackTrace()
                        emptyList()
                    }
                } else {
                    Log.e(TAG, "✗ CLIP embedding is null, skipping CLIP search")
                    Log.e(TAG, "  Check CLIP embedding generation logs above for errors")
                    emptyList()
                }
                Log.d(TAG, "")

                // 3. TrOCR Visual Search (OCR-specific visual patterns)
                Log.d(TAG, "===== STARTING TROCR SEARCH =====")
                val trocrResults = if (trocrEmbedding != null) {
                    try {
                        val results = box.query(NoteEntity_.trocrEmbedding.nearestNeighbors(trocrEmbedding, 10))
                            .build()
                            .findWithScores()

                        Log.d(TAG, "TrOCR search completed: ${results.size} results")
                        results.map { SearchResult(it.get(), it.score) }
                            .filter { it.score < TROCR_THRESHOLD }
                            .also {
                                Log.d(TAG, "TrOCR matches passing threshold (<$TROCR_THRESHOLD): ${it.size}")
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during TrOCR search: ${e.message}", e)
                        emptyList()
                    }
                } else {
                    Log.w(TAG, "TrOCR embedding is null, skipping TrOCR search")
                    emptyList()
                }

                // 3. ML Kit Text Search (Semantic text understanding)
                Log.d(TAG, "===== STARTING ML KIT TEXT SEARCH =====")
                val mlKitTextResults = if (mlKitTextEmbedding != null) {
                    try {
                        Log.d(TAG, "✓ ML Kit text query embedding available")
                        Log.d(TAG, "ML Kit text query embedding stats:")
                        val mlKitQueryMin = mlKitTextEmbedding.minOrNull() ?: 0f
                        val mlKitQueryMax = mlKitTextEmbedding.maxOrNull() ?: 0f
                        val mlKitQueryMean = mlKitTextEmbedding.average().toFloat()
                        val mlKitQueryNorm = kotlin.math.sqrt(mlKitTextEmbedding.map { it * it }.sum())
                        val mlKitQueryAllSame = mlKitTextEmbedding.distinct().size == 1
                        val mlKitQueryAllZero = mlKitTextEmbedding.all { it == 0f }

                        Log.d(TAG, "  Size: ${mlKitTextEmbedding.size}")
                        Log.d(TAG, "  Range: [$mlKitQueryMin, $mlKitQueryMax]")
                        Log.d(TAG, "  Mean: $mlKitQueryMean")
                        Log.d(TAG, "  Norm: $mlKitQueryNorm (should be ~1.0 if normalized)")
                        Log.d(TAG, "  All same values: $mlKitQueryAllSame")
                        Log.d(TAG, "  All zeros: $mlKitQueryAllZero")

                        if (mlKitQueryAllZero) {
                            Log.e(TAG, "✗ CRITICAL: Text embedding is all zeros!")
                        } else if (mlKitQueryAllSame) {
                            Log.e(TAG, "✗ CRITICAL: Text embedding has all same values!")
                        } else if (Math.abs(mlKitQueryNorm - 1.0) > 0.1) {
                            Log.w(TAG, "⚠️ WARNING: Text embedding not L2-normalized (norm != 1.0)")
                            Log.w(TAG, "   This will cause incorrect cosine distance calculations")
                        }

                        // Check how many notes have ML Kit text embeddings
                        val notesWithMlKitText = box.all.count { it.mlKitTextEmbedding != null }
                        Log.d(TAG, "Database: $notesWithMlKitText/$totalNotes notes have ML Kit text embeddings")

                        val results = box.query(NoteEntity_.mlKitTextEmbedding.nearestNeighbors(mlKitTextEmbedding, 10))
                            .build()
                            .findWithScores()

                        Log.d(TAG, "")
                        Log.d(TAG, "========================================")
                        Log.d(TAG, "ALL ML KIT TEXT SCORES (BEFORE FILTERING)")
                        Log.d(TAG, "ML Kit Text Threshold: $MLKIT_TEXT_THRESHOLD")
                        Log.d(TAG, "========================================")

                        results.forEachIndexed { index, scoredResult ->
                            val note = scoredResult.get()
                            val score = scoredResult.score
                            val willMatch = if (score < MLKIT_TEXT_THRESHOLD) "✓ WILL MATCH" else "✗ FILTERED OUT"

                            Log.d(TAG, "")
                            Log.d(TAG, "ML Kit Text [$index]: ${note.title}")
                            Log.d(TAG, "        ID: ${note.id}")
                            Log.d(TAG, "        SCORE: $score")
                            Log.d(TAG, "        STATUS: $willMatch")

                            // Show stored text and embedding info
                            val storedText = note.mlKitText ?: "(no text)"
                            val textPreview = storedText.take(80).replace("\n", " ")
                            Log.d(TAG, "        Text (${storedText.length} chars): \"$textPreview${if (storedText.length > 80) "..." else ""}\"")

                            if (note.mlKitTextEmbedding != null) {
                                val storedNorm = kotlin.math.sqrt(note.mlKitTextEmbedding!!.map { it * it }.sum())
                                val storedMin = note.mlKitTextEmbedding!!.minOrNull() ?: 0f
                                val storedMax = note.mlKitTextEmbedding!!.maxOrNull() ?: 0f
                                Log.d(TAG, "        Embedding - Norm: $storedNorm, Range: [$storedMin, $storedMax]")

                                if (Math.abs(storedNorm - 1.0) > 0.1) {
                                    Log.w(TAG, "        ⚠️ Stored embedding not normalized!")
                                }
                            } else {
                                Log.w(TAG, "        ⚠️ NULL ML Kit text embedding!")
                            }
                        }

                        Log.d(TAG, "")
                        Log.d(TAG, "========================================")
                        Log.d(TAG, "APPLYING FILTER (threshold <$MLKIT_TEXT_THRESHOLD)...")
                        Log.d(TAG, "========================================")

                        results.map { SearchResult(it.get(), it.score) }
                            .filter { it.score < MLKIT_TEXT_THRESHOLD }
                            .also {
                                Log.d(TAG, "")
                                Log.d(TAG, "FINAL ML KIT TEXT RESULTS: ${it.size} matches")
                                it.forEachIndexed { idx, result ->
                                    Log.d(TAG, "  ✓ Match $idx: ${result.note.title} (score: ${result.score})")
                                }
                                Log.d(TAG, "========================================")
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "Crash/Error during ML Kit Text Search: ${e.message}", e)
                        emptyList()
                    }
                } else {
                    Log.w(TAG, "ML Kit text embedding is null, skipping ML Kit text search")
                    emptyList()
                }
                Log.d(TAG, "")

                // 4. ColorBased Text Search (Handwriting OCR semantic text)
                Log.d(TAG, "===== STARTING COLORBASED TEXT SEARCH =====")
                val colorBasedTextResults = if (colorBasedTextEmbedding != null) {
                    try {
                        val results = box.query(NoteEntity_.colorBasedTextEmbedding.nearestNeighbors(colorBasedTextEmbedding, 10))
                            .build()
                            .findWithScores()

                        Log.d(TAG, "ColorBased text search completed: ${results.size} results")
                        results.map { SearchResult(it.get(), it.score) }
                            .filter { it.score < COLORBASED_TEXT_THRESHOLD }
                            .also {
                                Log.d(TAG, "ColorBased text matches passing threshold (<$COLORBASED_TEXT_THRESHOLD): ${it.size}")
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during ColorBased text search: ${e.message}", e)
                        emptyList()
                    }
                } else {
                    Log.w(TAG, "ColorBased text embedding is null, skipping ColorBased text search")
                    emptyList()
                }

                // 5. Merge Results with Weighted Scoring
                val combinedMap = mutableMapOf<Long, MutableMap<String, Double>>()

                imageResults.forEach {
                    combinedMap.getOrPut(it.note.id) { mutableMapOf() }["image"] = it.score
                }
                clipResults.forEach {
                    combinedMap.getOrPut(it.note.id) { mutableMapOf() }["clip"] = it.score
                }
                trocrResults.forEach {
                    combinedMap.getOrPut(it.note.id) { mutableMapOf() }["trocr"] = it.score
                }
                mlKitTextResults.forEach {
                    combinedMap.getOrPut(it.note.id) { mutableMapOf() }["mlkit"] = it.score
                }
                colorBasedTextResults.forEach {
                    combinedMap.getOrPut(it.note.id) { mutableMapOf() }["colorbased"] = it.score
                }

                val finalResults = combinedMap.mapNotNull { (id, scores) ->
                    val note = box.get(id)

                    val mlKitScore = scores["mlkit"]
                    val colorBasedScore = scores["colorbased"]
                    val trocrScore = scores["trocr"]
                    val imageScore = scores["image"]  // Always null (MobileNetV3 disabled)
                    val clipScore = scores["clip"]

                    // Weighted combination strategy:
                    // ACTIVE: CLIP (40%) + ML Kit Text (60%)
                    // - CLIP: Primary image embedding, robust to variations
                    // - ML Kit Text: Semantic understanding from printed text
                    //
                    // DISABLED: TrOCR (OCR visual patterns), ColorBased (handwriting)
                    // Available combinations if other embeddings are re-enabled:
                    val finalScore = when {
                        // All 4 embeddings available
                        clipScore != null && mlKitScore != null && colorBasedScore != null && trocrScore != null -> {
                            0.3 * clipScore + 0.25 * mlKitScore + 0.2 * colorBasedScore + 0.25 * trocrScore
                        }

                        // CLIP + 2 text embeddings
                        clipScore != null && mlKitScore != null && trocrScore != null -> {
                            0.35 * clipScore + 0.35 * mlKitScore + 0.3 * trocrScore
                        }
                        clipScore != null && mlKitScore != null && colorBasedScore != null -> {
                            0.35 * clipScore + 0.4 * mlKitScore + 0.25 * colorBasedScore
                        }
                        clipScore != null && colorBasedScore != null && trocrScore != null -> {
                            0.35 * clipScore + 0.3 * colorBasedScore + 0.35 * trocrScore
                        }

                        // CLIP + 1 text embedding
                        clipScore != null && mlKitScore != null -> {
                            0.4 * clipScore + 0.6 * mlKitScore
                        }
                        clipScore != null && colorBasedScore != null -> {
                            0.4 * clipScore + 0.6 * colorBasedScore
                        }
                        clipScore != null && trocrScore != null -> {
                            0.5 * clipScore + 0.5 * trocrScore
                        }

                        // Text-only combinations (when CLIP not available - rare)
                        mlKitScore != null && colorBasedScore != null && trocrScore != null -> {
                            0.35 * mlKitScore + 0.25 * colorBasedScore + 0.4 * trocrScore
                        }
                        mlKitScore != null && colorBasedScore != null -> {
                            0.55 * mlKitScore + 0.45 * colorBasedScore
                        }
                        mlKitScore != null && trocrScore != null -> {
                            0.5 * mlKitScore + 0.5 * trocrScore
                        }
                        colorBasedScore != null && trocrScore != null -> {
                            0.5 * colorBasedScore + 0.5 * trocrScore
                        }

                        // Single embedding fallbacks
                        clipScore != null -> clipScore
                        mlKitScore != null -> mlKitScore
                        colorBasedScore != null -> colorBasedScore
                        trocrScore != null -> trocrScore
                        else -> 1.0
                    }

                    if (finalScore < SEARCH_THRESHOLD) {
                        SearchResult(
                            note = note,
                            score = finalScore,
                            imageScore = null,  // MobileNetV3 disabled
                            clipScore = clipScore,
                            trocrScore = trocrScore,
                            mlKitTextScore = mlKitScore,
                            colorBasedTextScore = colorBasedScore
                        )
                    } else {
                        null
                    }
                }.sortedBy { it.score }

                // Log search embeddings summary
                Log.d(TAG, "===== SEARCH EMBEDDINGS USED =====")
                Log.d(TAG, "Image (CLIP): ${if (clipEmbedding != null) "✓ ACTIVE" else "✗"}")
                Log.d(TAG, "ML Kit Text: ${if (mlKitTextEmbedding != null) "✓ ACTIVE" else "✗"}")
                Log.d(TAG, "TrOCR: ${if (trocrEmbedding != null) "✓ (generated but search disabled)" else "✗ DISABLED"}")
                Log.d(TAG, "ColorBased Text: ${if (colorBasedTextEmbedding != null) "✓ (generated but search disabled)" else "✗ DISABLED"}")
                Log.d(TAG, "Active searches: ${listOf(clipEmbedding, mlKitTextEmbedding).count { it != null }}/2 (CLIP + ML Kit)")
                Log.d(TAG, "Note: MobileNetV3, TrOCR, ColorBased searches disabled")
                Log.d(TAG, "==================================")

                // Log detailed results with individual scores for each match
                Log.d(TAG, "")
                Log.d(TAG, "========================================")
                Log.d(TAG, "FINAL SEARCH RESULTS WITH INDIVIDUAL SCORES")
                Log.d(TAG, "Total matches: ${finalResults.size}")
                Log.d(TAG, "========================================")

                if (finalResults.isEmpty()) {
                    Log.d(TAG, "No matches found")
                } else {
                    finalResults.forEachIndexed { index, result ->
                        Log.d(TAG, "")
                        Log.d(TAG, "───────────────────────────────────────")
                        Log.d(TAG, "Match #${index + 1}: ${result.note.title}")
                        Log.d(TAG, "  Note ID: ${result.note.id}")
                        Log.d(TAG, "  Image Path: ${result.note.imagePath.takeLast(50)}")
                        Log.d(TAG, "")
                        Log.d(TAG, "  SCORES:")
                        Log.d(TAG, "  ├─ Final Score: ${String.format("%.4f", result.score)} ⭐")
                        Log.d(TAG, "  ├─ CLIP Score: ${result.clipScore?.let { String.format("%.4f", it) } ?: "null"} (40% weight)")
                        Log.d(TAG, "  ├─ ML Kit Text Score: ${result.mlKitTextScore?.let { String.format("%.4f", it) } ?: "null"} (60% weight)")
                        Log.d(TAG, "  ├─ TrOCR Score: ${result.trocrScore?.let { String.format("%.4f", it) } ?: "disabled"}")
                        Log.d(TAG, "  └─ ColorBased Score: ${result.colorBasedTextScore?.let { String.format("%.4f", it) } ?: "disabled"}")

                        // Show calculation if both scores available
                        if (result.clipScore != null && result.mlKitTextScore != null) {
                            val calculated = 0.4 * result.clipScore + 0.6 * result.mlKitTextScore
                            Log.d(TAG, "  Calculation: 0.4×${String.format("%.4f", result.clipScore)} + 0.6×${String.format("%.4f", result.mlKitTextScore)} = ${String.format("%.4f", calculated)}")
                        }

                        // Show preview of OCR text
                        val textPreview = result.note.mlKitText?.take(100)?.replace("\n", " ") ?: "(no text)"
                        Log.d(TAG, "  Text Preview: \"$textPreview${if (result.note.mlKitText?.length ?: 0 > 100) "..." else ""}\"")

                        // Diagnostic information
                        if (result.note.clipEmbedding == null) {
                            Log.w(TAG, "  ⚠️ WARNING: This note has NO CLIP embedding!")
                            Log.w(TAG, "     Re-scan this note to generate CLIP embedding")
                        } else if (result.clipScore != null && result.clipScore >= SEARCH_THRESHOLD) {
                            Log.d(TAG, "  ℹ️ Note: CLIP score (${String.format("%.4f", result.clipScore)}) alone didn't pass threshold ($SEARCH_THRESHOLD)")
                            Log.d(TAG, "     But note matches due to strong text similarity")
                        }
                    }

                    Log.d(TAG, "")
                    Log.d(TAG, "───────────────────────────────────────")
                    Log.d(TAG, "Best Match: ${finalResults.firstOrNull()?.note?.title ?: "none"}")
                    Log.d(TAG, "  └─ Score: ${finalResults.firstOrNull()?.score?.let { String.format("%.4f", it) } ?: "n/a"}")

                    // Summary of missing CLIP embeddings
                    val notesWithoutClip = finalResults.count { it.note.clipEmbedding == null }
                    if (notesWithoutClip > 0) {
                        Log.d(TAG, "")
                        Log.d(TAG, "⚠️ CLIP EMBEDDING STATUS:")
                        Log.d(TAG, "  ${notesWithoutClip}/${finalResults.size} results are missing CLIP embeddings")
                        Log.d(TAG, "  Re-scan these notes to enable CLIP-based image search")
                    }
                }

                Log.d(TAG, "========================================")
                Log.d(TAG, "")

                onProgress?.invoke(ProcessingState(
                    ProcessingStep.COMPLETE,
                    1.0f,
                    "Found ${finalResults.size} matches using ${listOf(clipEmbedding, mlKitTextEmbedding).count { it != null }}/2 active embeddings (CLIP + ML Kit)"
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
