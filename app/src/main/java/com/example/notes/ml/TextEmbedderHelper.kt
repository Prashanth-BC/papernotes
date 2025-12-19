package com.example.notes.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import kotlin.jvm.Volatile

class TextEmbedderHelper(
    private val context: Context,
    var currentDelegate: Int = DELEGATE_CPU
) {
    private var interpreter: Interpreter? = null
    private var tokenizer: BertTokenizer? = null
    @Volatile
    private var isInitialized = false

    companion object {
        private const val TAG = "TextEmbedderHelper"
        private const val MODEL_NAME = "all_minilm_l6_v2_quant.tflite" // all-MiniLM-L6-V2 Quantized TFLite
        private const val VOCAB_NAME = "vocab.txt"
        private const val SEQ_LEN = 512
        private const val OUTPUT_EMBEDDING_SIZE = 384
        
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
    }

    init {
        setupInterpreter()
        setupTokenizer()
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun setupInterpreter() {
        if (isInitialized && interpreter != null) {
            Log.d(TAG, "Text embedder already initialized and loaded, skipping re-initialization")
            return
        }
        
        try {
            val options = Interpreter.Options()
            val compatibilityList = CompatibilityList()
            
            if (currentDelegate == DELEGATE_GPU) {
                val compatibilityList = CompatibilityList()
                if (compatibilityList.isDelegateSupportedOnThisDevice) {
                    val delegateOptions = compatibilityList.bestOptionsForThisDevice
                    val gpuDelegate = GpuDelegate(delegateOptions)
                    options.addDelegate(gpuDelegate)
                    Log.d(TAG, "GPU Delegate enabled for Text Embedding")
                } else {
                    Log.w(TAG, "GPU Delegate requested but not supported, falling back to CPU")
                    currentDelegate = DELEGATE_CPU
                }
            } else {
                Log.d(TAG, "Using CPU Delegate for Text Embedding")
                // Try disabling XNNPack to resolve native crash
                options.setNumThreads(4)
            }
            
            val modelBuffer = loadModelFile(context, MODEL_NAME)
            interpreter = Interpreter(modelBuffer, options)
            isInitialized = true
            Log.d(TAG, "TFLite Interpreter successfully initialized and ready")
        } catch (e: Exception) {
            Log.e(TAG, "Interpreter init error: ${e.message}", e)
            interpreter = null
            isInitialized = false
        }
    }
    
    private fun setupTokenizer() {
        tokenizer = BertTokenizer(context, VOCAB_NAME)
    }

    @Synchronized
    fun embed(text: String): FloatArray? {
        val tflite = interpreter ?: return null
        val bertTokenizer = tokenizer ?: return null
        
        return try {
            // Tokenize
            val (inputIds, attentionMask) = bertTokenizer.tokenize(text, SEQ_LEN)
            
            // Prepare inputs: [1, SEQ_LEN]
            val inputIdsTensor = Array(1) { inputIds }
            val attentionMaskTensor = Array(1) { attentionMask }
            
            // But Map inputs require signature runner. For Interpreter runForMultipleInputs:
            val inputsArray = arrayOf(inputIdsTensor, attentionMaskTensor)

            // Output: [1, 384]
            val outputBuffer = Array(1) { FloatArray(OUTPUT_EMBEDDING_SIZE) }
            val outputs = mapOf(0 to outputBuffer)
            
            tflite.runForMultipleInputsOutputs(inputsArray, outputs)
            
            val vector = outputBuffer[0]
            Log.d(TAG, "Generated embedding size: ${vector.size}")
            vector

        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}", e)
            null
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
        Log.d(TAG, "Text embedder closed")
    }

    fun setDelegate(delegate: Int) {
        if (currentDelegate != delegate) {
            currentDelegate = delegate
            close()
            setupInterpreter()
        }
    }
    
    /**
     * Check if embedder is ready for inference
     */
    fun isReady(): Boolean = isInitialized && interpreter != null
}
