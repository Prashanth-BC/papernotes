package com.example.notes.ml

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.LongBuffer

/**
 * Text embedding helper using ONNX model
 * Generates 384-dimensional text embeddings
 */
class TextEmbedderHelperOnnx(private val context: Context) {
    private val TAG = "TextEmbedderHelperOnnx"
    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var vocab: Map<String, Int>? = null
    private var ready = false

    companion object {
        private const val MODEL_PATH = "all_minilm_l6_v2.onnx"
        private const val TOKENIZER_PATH = "tokenizer.json"
        private const val MAX_LENGTH = 128
        private const val PAD_TOKEN_ID = 0
    }

    init {
        try {
            Log.d(TAG, "Initializing TextEmbedderHelperOnnx...")
            Log.d(TAG, "Loading ONNX model: $MODEL_PATH")

            val modelBytes = context.assets.open(MODEL_PATH).use { it.readBytes() }
            Log.d(TAG, "Model loaded: ${modelBytes.size} bytes")

            val sessionOptions = OrtSession.SessionOptions()
            session = env.createSession(modelBytes, sessionOptions)
            Log.d(TAG, "ONNX session created successfully")

            loadVocabulary()

            if (vocab != null) {
                ready = true
                Log.d(TAG, "✓ TextEmbedderHelperOnnx initialized successfully (vocab: ${vocab!!.size} tokens)")
            } else {
                ready = false
                Log.e(TAG, "✗ TextEmbedderHelperOnnx initialization failed: vocabulary is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error initializing TextEmbedderHelperOnnx: ${e.message}", e)
            e.printStackTrace()
            ready = false
        }
    }

    fun isReady(): Boolean = ready

    fun embed(text: String): FloatArray? {
        if (!ready || session == null || vocab == null) {
            Log.e(TAG, "Embedder not ready")
            return null
        }

        try {
            val tokens = tokenize(text)
            val inputIds = LongBuffer.wrap(tokens.map { it.toLong() }.toLongArray())
            val attentionMask = LongBuffer.wrap(tokens.map { if (it != PAD_TOKEN_ID) 1L else 0L }.toLongArray())
            val tokenTypeIds = LongBuffer.wrap(LongArray(tokens.size) { 0L }) // All zeros for single sequence

            val inputShape = longArrayOf(1, tokens.size.toLong())

            val inputIdsTensor = OnnxTensor.createTensor(env, inputIds, inputShape)
            val attentionMaskTensor = OnnxTensor.createTensor(env, attentionMask, inputShape)
            val tokenTypeIdsTensor = OnnxTensor.createTensor(env, tokenTypeIds, inputShape)

            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor,
                "token_type_ids" to tokenTypeIdsTensor
            )

            val results = session!!.run(inputs)
            val output = results[0].value as Array<*>

            // Extract [CLS] token embedding (first token) - shape is [batch=1, seq_len, hidden_size=384]
            // We want output[0][0] which is the [CLS] embedding of 384 dimensions
            val batchOutput = output[0] as Array<*>
            val clsEmbeddingRaw = batchOutput[0] as FloatArray

            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()
            results.close()

            // Diagnostic logging BEFORE normalization
            val minRaw = clsEmbeddingRaw.minOrNull() ?: 0f
            val maxRaw = clsEmbeddingRaw.maxOrNull() ?: 0f
            val meanRaw = clsEmbeddingRaw.average().toFloat()
            val normRaw = kotlin.math.sqrt(clsEmbeddingRaw.map { it * it }.sum())

            // L2 NORMALIZE for cosine distance
            val clsEmbedding = l2Normalize(clsEmbeddingRaw)
            val normNormalized = kotlin.math.sqrt(clsEmbedding.map { it * it }.sum())

            Log.d(TAG, "===== TEXT EMBEDDING DIAGNOSTIC =====")
            Log.d(TAG, "Text: ${text.take(50)}...")
            Log.d(TAG, "RAW Stats - min: $minRaw, max: $maxRaw, mean: $meanRaw, norm: $normRaw")
            Log.d(TAG, "NORMALIZED norm: $normNormalized (should be ~1.0)")
            Log.d(TAG, "First 10 values: ${clsEmbedding.take(10).joinToString(", ")}")
            Log.d(TAG, "=====================================")

            return clsEmbedding
        } catch (e: Exception) {
            Log.e(TAG, "Error generating text embedding", e)
            return null
        }
    }

    fun setDelegate(delegate: Int) {
        // Placeholder for delegate switching
    }

    /**
     * L2 normalize an embedding vector to unit length
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
     * Tokenize text using manual WordPiece algorithm
     * This properly handles subword tokenization required by all-MiniLM-L6-v2
     */
    private fun tokenize(text: String): IntArray {
        if (vocab == null) {
            Log.e(TAG, "Vocabulary not loaded!")
            return intArrayOf(101, 100, 102) + IntArray(MAX_LENGTH - 3) { PAD_TOKEN_ID }
        }

        val tokens = mutableListOf<Int>()
        tokens.add(101) // [CLS]

        // Basic text preprocessing (same as BERT)
        val normalizedText = text.lowercase()
            .replace(Regex("[\\p{Punct}]")) { " ${it.value} " } // Add spaces around punctuation
            .trim()

        val words = normalizedText.split(Regex("\\s+")).filter { it.isNotEmpty() }

        for (word in words) {
            if (tokens.size >= MAX_LENGTH - 1) break

            // Try whole word first
            val wholeWordToken = vocab!![word]
            if (wholeWordToken != null) {
                tokens.add(wholeWordToken)
            } else {
                // Apply WordPiece algorithm
                val subwordTokens = wordPieceTokenize(word)
                tokens.addAll(subwordTokens)
            }
        }

        tokens.add(102) // [SEP]

        // Pad to MAX_LENGTH
        while (tokens.size < MAX_LENGTH) {
            tokens.add(PAD_TOKEN_ID)
        }

        val finalTokens = tokens.take(MAX_LENGTH).toIntArray()

        // Diagnostic logging
        val unkCount = finalTokens.count { it == 100 }
        val unkPercentage = (unkCount.toFloat() / finalTokens.size) * 100
        Log.d(TAG, "Tokenization: ${finalTokens.size} tokens, $unkCount UNK (${String.format("%.1f", unkPercentage)}%)")
        if (unkPercentage > 30) {
            Log.w(TAG, "⚠️ High UNK percentage - text may not embed well")
        }

        return finalTokens
    }

    /**
     * WordPiece tokenization algorithm for subword splitting
     * Example: "playing" → ["play", "##ing"]
     */
    private fun wordPieceTokenize(word: String): List<Int> {
        val tokens = mutableListOf<Int>()
        var start = 0

        while (start < word.length) {
            var end = word.length
            var foundToken: Int? = null

            // Greedy longest-match-first: try progressively shorter substrings
            while (start < end) {
                val substr = if (start > 0) {
                    "##${word.substring(start, end)}" // Continuation subword
                } else {
                    word.substring(start, end) // First subword
                }

                val tokenId = vocab!![substr]
                if (tokenId != null) {
                    foundToken = tokenId
                    break
                }
                end--
            }

            if (foundToken != null) {
                tokens.add(foundToken)
                start = end
            } else {
                // No match found - use [UNK] and skip one character
                tokens.add(100) // [UNK]
                start++
            }
        }

        return tokens
    }

    /**
     * Load WordPiece vocabulary from tokenizer.json
     * Extracts the vocab mapping from the HuggingFace tokenizer format
     */
    private fun loadVocabulary() {
        try {
            Log.d(TAG, "Loading vocabulary from tokenizer.json...")

            val json = context.assets.open(TOKENIZER_PATH).bufferedReader().use { it.readText() }

            // Parse the HuggingFace tokenizer.json format
            // Structure: { "model": { "vocab": { "word": id, ... } } }
            val gson = Gson()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val tokenizer: Map<String, Any> = gson.fromJson(json, type)

            @Suppress("UNCHECKED_CAST")
            val model = tokenizer["model"] as? Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val vocabMap = model?.get("vocab") as? Map<String, Any>

            if (vocabMap != null) {
                // Convert Double values to Int (Gson parses numbers as Double)
                vocab = vocabMap.mapValues { (it.value as? Double)?.toInt() ?: 0 }
                Log.d(TAG, "✓ Vocabulary loaded: ${vocab!!.size} tokens")

                // Verify special tokens exist
                val clsToken = vocab!!["[CLS]"] ?: vocab!!["[cls]"]
                val sepToken = vocab!!["[SEP]"] ?: vocab!!["[sep]"]
                val unkToken = vocab!!["[UNK]"] ?: vocab!!["[unk]"]
                Log.d(TAG, "  Special tokens: CLS=$clsToken, SEP=$sepToken, UNK=$unkToken")

                // Test tokenization
                val testTokens = tokenize("test tokenization")
                Log.d(TAG, "  Test: 'test tokenization' → ${testTokens.take(10).toList()}")
            } else {
                Log.e(TAG, "✗ Failed to extract vocab from tokenizer.json")
                vocab = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error loading vocabulary: ${e.message}", e)
            e.printStackTrace()
            vocab = null
        }
    }

    fun close() {
        session?.close()
    }
}
