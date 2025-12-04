package com.whispercppdemo.embedding

import android.content.Context
import ai.onnxruntime.*
import org.json.JSONArray
import java.nio.LongBuffer
import kotlin.math.sqrt

class TextEmbedding(context: Context) {
    private var ortSession: OrtSession? = null
    private val ortEnv = OrtEnvironment.getEnvironment()

    // Simple tokenizer - split by whitespace and convert to IDs
    // Note: This is a simplified version. For production, use a proper tokenizer
    private val vocab = mutableMapOf<String, Long>()
    private var nextId = 1L

    companion object {
        private const val MAX_SEQ_LENGTH = 128
        private const val EMBEDDING_DIM = 384  // for all-MiniLM-L6-v2
    }

    init {
        try {
            // Try to load the ONNX model from assets
            val modelBytes = context.assets.open("models/embedding_model.onnx").readBytes()
            ortSession = ortEnv.createSession(modelBytes)
        } catch (e: Exception) {
            // Model not found - will use fallback
            ortSession = null
        }
    }

    /**
     * Generate embedding for text
     * Returns a FloatArray of embeddings, or null if model not available
     */
    fun embed(text: String): FloatArray? {
        if (ortSession == null) {
            // Fallback: return simple hash-based embedding for demo purposes
            return generateFallbackEmbedding(text)
        }

        try {
            // Tokenize text
            val tokens = tokenize(text)

            // Create input tensors
            val inputIds = LongArray(MAX_SEQ_LENGTH) { if (it < tokens.size) tokens[it] else 0L }
            val attentionMask = LongArray(MAX_SEQ_LENGTH) { if (it < tokens.size) 1L else 0L }

            val inputIdsTensor = OnnxTensor.createTensor(
                ortEnv,
                LongBuffer.wrap(inputIds),
                longArrayOf(1, MAX_SEQ_LENGTH.toLong())
            )

            val attentionMaskTensor = OnnxTensor.createTensor(
                ortEnv,
                LongBuffer.wrap(attentionMask),
                longArrayOf(1, MAX_SEQ_LENGTH.toLong())
            )

            // Run inference
            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor
            )

            val output = ortSession!!.run(inputs)
            val embedding = (output[0].value as Array<FloatArray>)[0]

            // Normalize the embedding
            return normalize(embedding)
        } catch (e: Exception) {
            e.printStackTrace()
            return generateFallbackEmbedding(text)
        }
    }

    /**
     * Simple tokenizer - converts text to token IDs
     * Note: This is simplified. For production, use a proper BPE tokenizer
     */
    private fun tokenize(text: String): List<Long> {
        val words = text.lowercase().split("\\s+".toRegex()).take(MAX_SEQ_LENGTH)
        return words.map { word ->
            vocab.getOrPut(word) {
                val id = nextId
                nextId++
                id
            }
        }
    }

    /**
     * Normalize embedding vector to unit length
     */
    private fun normalize(vector: FloatArray): FloatArray {
        val magnitude = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        return if (magnitude > 0) {
            vector.map { it / magnitude }.toFloatArray()
        } else {
            vector
        }
    }

    /**
     * Fallback embedding when ONNX model is not available
     * Generates a simple hash-based embedding for demo purposes
     */
    private fun generateFallbackEmbedding(text: String): FloatArray {
        val embedding = FloatArray(EMBEDDING_DIM)
        val normalized = text.lowercase().trim()

        // Use hash-based features
        for (i in embedding.indices) {
            val seed = i + normalized.hashCode()
            embedding[i] = (seed.hashCode() % 1000) / 1000f - 0.5f
        }

        return normalize(embedding)
    }

    /**
     * Calculate cosine similarity between two embeddings
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embeddings must have same dimension" }
        return a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }.toFloat()
    }

    /**
     * Convert embedding FloatArray to JSON string for storage
     */
    fun embeddingToJson(embedding: FloatArray): String {
        val json = JSONArray()
        embedding.forEach { json.put(it) }
        return json.toString()
    }

    /**
     * Convert JSON string back to FloatArray
     */
    fun jsonToEmbedding(json: String): FloatArray {
        val jsonArray = JSONArray(json)
        return FloatArray(jsonArray.length()) { i ->
            jsonArray.getDouble(i).toFloat()
        }
    }

    fun close() {
        ortSession?.close()
    }
}
