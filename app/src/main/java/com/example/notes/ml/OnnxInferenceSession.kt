package com.example.notes.ml

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * ONNX Runtime Inference Session wrapper
 * Handles model loading and inference
 */
class OnnxInferenceSession(
    context: Context,
    modelPath: String,
    useGPU: Boolean = false
) {
    private val TAG = "OnnxInferenceSession"
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val sessionOptions = OrtSession.SessionOptions()

        if (useGPU) {
            try {
                sessionOptions.addNnapi()
                Log.d(TAG, "GPU acceleration enabled (NNAPI)")
            } catch (e: Exception) {
                Log.w(TAG, "GPU acceleration not available, using CPU", e)
            }
        }

        val modelBytes = context.assets.open(modelPath).use { it.readBytes() }
        session = env.createSession(modelBytes, sessionOptions)

        Log.d(TAG, "Model loaded: $modelPath")
        Log.d(TAG, "Input: ${session.inputNames}")
        Log.d(TAG, "Output: ${session.outputNames}")
    }

    /**
     * Run inference on input data
     *
     * @param inputData Float array of input data
     * @param inputShape Shape of input [batch, channels, height, width]
     * @return Pair of (output data, output shape)
     */
    fun run(inputData: FloatArray, inputShape: LongArray): Pair<FloatArray, LongArray> {
        val inputName = session.inputNames.iterator().next()

        val inputBuffer = FloatBuffer.wrap(inputData)
        val inputTensor = OnnxTensor.createTensor(env, inputBuffer, inputShape)

        val results = session.run(mapOf(inputName to inputTensor))

        val outputTensor = results[0].value as Array<*>
        val flatOutput = flattenOutput(outputTensor)

        val outputShape = getOutputShape(outputTensor)

        inputTensor.close()
        results.close()

        return Pair(flatOutput, outputShape)
    }

    /**
     * Flatten nested array output to FloatArray
     */
    private fun flattenOutput(array: Any): FloatArray {
        val result = mutableListOf<Float>()

        fun flatten(obj: Any) {
            when (obj) {
                is Array<*> -> obj.forEach { if (it != null) flatten(it) }
                is FloatArray -> result.addAll(obj.toList())
                is Float -> result.add(obj)
            }
        }

        flatten(array)
        return result.toFloatArray()
    }

    /**
     * Get shape of nested array
     */
    private fun getOutputShape(array: Any): LongArray {
        val shape = mutableListOf<Long>()

        var current = array
        while (current is Array<*>) {
            shape.add(current.size.toLong())
            current = current[0] ?: break
        }

        if (current is FloatArray) {
            shape.add(current.size.toLong())
        }

        return shape.toLongArray()
    }

    fun close() {
        session.close()
    }
}
