package com.whispercppdemo.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.whispercppdemo.embedding.TextEmbedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val LOG_TAG = "LLMManager"

/**
 * Manages local LLM inference using Google AI Edge (MediaPipe)
 * Uses Gemma 3 1B model for on-device Q&A and summarization with RAG
 */
class LLMManager(private val context: Context) {
    private var llmInference: LlmInference? = null
    private var isInitialized = false
    private val textEmbedding = TextEmbedding(context)

    companion object {
        private const val MODEL_FILENAME = "gemma3-1b-it-int4.task"
        private const val MAX_TOKENS = 512
        private const val TEMPERATURE = 0.7f
        private const val TOP_K = 40
        private const val TOP_P = 0.95f
        // Max input characters (roughly 2000-2500 tokens, safe for mobile memory constraints)
        private const val MAX_INPUT_CHARS = 8000
        // Chunk size for splitting long texts
        private const val CHUNK_SIZE = 1500
        // Max chunks to retrieve for context
        private const val MAX_CHUNKS = 4
    }

    /**
     * Initialize the LLM model
     * This should be called once when the app starts or when LLM features are first needed
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.d(LOG_TAG, "LLM already initialized")
            return@withContext true
        }

        try {
            Log.d(LOG_TAG, "Initializing Google AI Edge LLM...")

            val modelFile = getModelFile()
            if (!modelFile.exists()) {
                Log.e(LOG_TAG, "Model file not found: ${modelFile.absolutePath}")
                return@withContext false
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)

            isInitialized = true
            Log.d(LOG_TAG, "Google AI Edge LLM initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to initialize LLM", e)
            false
        }
    }

    /**
     * Generate a summary of the transcribed text using RAG for long texts
     */
    suspend fun generateSummary(text: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized || llmInference == null) {
            return@withContext Result.failure(IllegalStateException("LLM not initialized"))
        }

        try {
            Log.d(LOG_TAG, "Generating summary for text length: ${text.length}")

            // For summaries, use first chunks if text is too long
            val contextText = if (text.length > MAX_INPUT_CHARS) {
                Log.d(LOG_TAG, "Text too long, using first ${MAX_INPUT_CHARS} characters")
                text.take(MAX_INPUT_CHARS)
            } else {
                text
            }

            val prompt = buildSummaryPrompt(contextText)
            val response = llmInference!!.generateResponse(prompt)

            Log.d(LOG_TAG, "Summary generated successfully")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error generating summary", e)
            Result.failure(e)
        }
    }

    /**
     * Answer a question based on the transcribed text using RAG
     */
    suspend fun answerQuestion(context: String, question: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized || llmInference == null) {
            return@withContext Result.failure(IllegalStateException("LLM not initialized"))
        }

        try {
            Log.d(LOG_TAG, "Answering question: $question")

            // Use RAG to find relevant chunks
            val relevantContext = if (context.length > MAX_INPUT_CHARS) {
                Log.d(LOG_TAG, "Using RAG to retrieve relevant chunks")
                retrieveRelevantChunks(context, question)
            } else {
                context
            }

            val prompt = buildQAPrompt(relevantContext, question)
            val response = llmInference!!.generateResponse(prompt)

            Log.d(LOG_TAG, "Answer generated successfully")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error answering question", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieve relevant chunks using RAG
     */
    private suspend fun retrieveRelevantChunks(text: String, query: String): String {
        try {
            // Split text into chunks
            val chunks = splitIntoChunks(text)
            Log.d(LOG_TAG, "Split text into ${chunks.size} chunks")

            // Get query embedding
            val queryEmbedding = textEmbedding.embed(query) ?: return text.take(MAX_INPUT_CHARS)

            // Calculate similarity for each chunk
            val chunksWithScores = mutableListOf<Pair<String, Float>>()
            for (chunk in chunks) {
                val chunkEmbedding = textEmbedding.embed(chunk)
                if (chunkEmbedding != null) {
                    val similarity = textEmbedding.cosineSimilarity(queryEmbedding, chunkEmbedding)
                    chunksWithScores.add(chunk to similarity)
                }
            }

            // Sort by similarity and take top chunks
            val topChunks = chunksWithScores
                .sortedByDescending { it.second }
                .take(MAX_CHUNKS)
                .map { it.first }

            Log.d(LOG_TAG, "Retrieved top ${topChunks.size} relevant chunks")

            // Combine chunks
            val combined = topChunks.joinToString("\n\n")
            return if (combined.length > MAX_INPUT_CHARS) {
                combined.take(MAX_INPUT_CHARS)
            } else {
                combined
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error in RAG retrieval, falling back to truncation", e)
            return text.take(MAX_INPUT_CHARS)
        }
    }

    /**
     * Split text into chunks for RAG
     */
    private fun splitIntoChunks(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var currentIndex = 0

        while (currentIndex < text.length) {
            val endIndex = minOf(currentIndex + CHUNK_SIZE, text.length)
            chunks.add(text.substring(currentIndex, endIndex))
            currentIndex += CHUNK_SIZE
        }

        return chunks
    }

    /**
     * Build prompt for summarization using Gemma 3 chat template
     */
    private fun buildSummaryPrompt(text: String): String {
        return """<start_of_turn>user
You are a helpful assistant that creates concise summaries.

Please provide a brief summary of the following text in 2-3 sentences:

$text<end_of_turn>
<start_of_turn>model
"""
    }

    /**
     * Build prompt for Q&A using Gemma 3 chat template
     */
    private fun buildQAPrompt(context: String, question: String): String {
        return """<start_of_turn>user
You are a helpful assistant that answers questions based on the given context. Only use information from the context provided.

Context:
$context

Question: $question<end_of_turn>
<start_of_turn>model
"""
    }

    /**
     * Get model file from cache directory
     * Copies from assets to cache on first access
     */
    private fun getModelFile(): File {
        val cacheFile = File(context.cacheDir, MODEL_FILENAME)

        // If model is already cached, return it
        if (cacheFile.exists()) {
            Log.d(LOG_TAG, "Using cached model")
            return cacheFile
        }

        // Try to copy from assets
        try {
            Log.d(LOG_TAG, "Copying model from assets to cache... (this may take a minute)")
            context.assets.open("models/$MODEL_FILENAME").use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(LOG_TAG, "Model copied to cache successfully")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to copy model from assets", e)
        }

        return cacheFile
    }

    /**
     * Check if model file exists in assets or cache
     */
    fun isModelAvailable(): Boolean {
        return try {
            context.assets.open("models/$MODEL_FILENAME").use { true }
        } catch (e: Exception) {
            getModelFile().exists()
        }
    }

    /**
     * Clean up resources
     */
    fun release() {
        try {
            llmInference?.close()
            llmInference = null
            textEmbedding.close()
            isInitialized = false
            Log.d(LOG_TAG, "LLM resources released")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error releasing LLM resources", e)
        }
    }
}
