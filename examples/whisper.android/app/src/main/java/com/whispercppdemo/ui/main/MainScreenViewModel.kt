package com.whispercppdemo.ui.main

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.whispercppdemo.data.database.AppDatabase
import com.whispercppdemo.data.database.Note
import com.whispercppdemo.embedding.TextEmbedding
import com.whispercppdemo.media.decodeWaveFile
import com.whispercppdemo.recorder.Recorder
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LOG_TAG = "MainScreenViewModel"

class MainScreenViewModel(private val application: Application) : ViewModel() {
    var canTranscribe by mutableStateOf(false)
        private set
    var isRecording by mutableStateOf(false)
        private set
    var isProcessing by mutableStateOf(false)
        private set
    var recordingAmplitude by mutableStateOf(0f)
        private set
    var recordingDuration by mutableStateOf(0L)
        private set
    var transcribedNote by mutableStateOf<Note?>(null)
        private set

    private val modelsPath = File(application.filesDir, "models")
    private val samplesPath = File(application.filesDir, "samples")
    private var recorder: Recorder = Recorder()
    private var whisperContext: com.whispercpp.whisper.WhisperContext? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordedFile: File? = null
    private val database = AppDatabase.getDatabase(application)
    private val noteDao = database.noteDao()
    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    private val textEmbedding = TextEmbedding(application)

    val allNotes = noteDao.getAllNotes()

    init {
        viewModelScope.launch {
            printSystemInfo()
            loadData()
        }
    }

    private suspend fun printSystemInfo() {
        // Removed verbose output
    }

    private suspend fun loadData() {
        try {
            copyAssets()
            loadBaseModel()
            canTranscribe = true
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
        }
    }

    private suspend fun copyAssets() = withContext(Dispatchers.IO) {
        modelsPath.mkdirs()
        samplesPath.mkdirs()
    }

    private suspend fun loadBaseModel() = withContext(Dispatchers.IO) {
        val models = application.assets.list("models/")
        if (models != null) {
            // Filter for Whisper model files (.bin extension)
            val whisperModel = models.firstOrNull { it.endsWith(".bin") }
            if (whisperModel != null) {
                whisperContext = com.whispercpp.whisper.WhisperContext.createContextFromAsset(application.assets, "models/$whisperModel")
            }
        }
    }

    fun benchmark() = viewModelScope.launch {
        runBenchmark(6)
    }

    fun transcribeSample() = viewModelScope.launch {
        transcribeAudio(getFirstSample())
    }

    private suspend fun runBenchmark(nthreads: Int) {
        if (!canTranscribe) {
            return
        }

        canTranscribe = false

        whisperContext?.benchMemory(nthreads)
        whisperContext?.benchGgmlMulMat(nthreads)

        canTranscribe = true
    }

    private suspend fun getFirstSample(): File = withContext(Dispatchers.IO) {
        samplesPath.listFiles()!!.first()
    }

    private suspend fun readAudioSamples(file: File): FloatArray = withContext(Dispatchers.IO) {
        stopPlayback()
        return@withContext decodeWaveFile(file)
    }

    private suspend fun stopPlayback() = withContext(Dispatchers.Main) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private suspend fun startPlayback(file: File) = withContext(Dispatchers.Main) {
        mediaPlayer = MediaPlayer.create(application, file.absolutePath.toUri())
        mediaPlayer?.start()
    }

    private suspend fun transcribeAudio(file: File) {
        if (!canTranscribe) {
            return
        }

        canTranscribe = false
        isProcessing = true

        try {
            // Reading audio
            val data = readAudioSamples(file)
            val audioDuration = data.size / (16000 / 1000)

            // Transcribing
            val text = whisperContext?.transcribeData(data)

            // Save note to database and set it to transcribedNote
            if (!text.isNullOrBlank()) {
                val note = saveNote(text, file.absolutePath, audioDuration.toLong())
                withContext(Dispatchers.Main) {
                    transcribedNote = note
                }
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
        }

        canTranscribe = true
        isProcessing = false
    }

    private suspend fun saveNote(transcribedText: String, audioPath: String, duration: Long): Note? = withContext(Dispatchers.IO) {
        try {
            // Generate embedding for semantic search
            val embedding = textEmbedding.embed(transcribedText)
            val embeddingJson = embedding?.let { textEmbedding.embeddingToJson(it) }

            val note = Note(
                transcribedText = transcribedText,
                timestamp = System.currentTimeMillis(),
                audioFilePath = audioPath,
                duration = duration,
                embedding = embeddingJson
            )
            noteDao.insertNote(note)
            return@withContext note
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error saving note", e)
            return@withContext null
        }
    }

    fun toggleRecord() = viewModelScope.launch {
        try {
            if (isRecording) {
                recorder.stopRecording()
                isRecording = false
                recordedFile?.let { transcribeAudio(it) }
            } else {
                stopPlayback()
                transcribedNote = null
                recordingDuration = 0L
                recordingAmplitude = 0f

                val file = getTempFileForRecording()
                isRecording = true
                recordedFile = file

                val recordingStartTime = System.currentTimeMillis()

                // Start a coroutine to update recording duration
                launch {
                    while (isRecording) {
                        recordingDuration = System.currentTimeMillis() - recordingStartTime
                        kotlinx.coroutines.delay(100)
                    }
                }

                recorder.startRecording(
                    file,
                    onError = { e ->
                        viewModelScope.launch {
                            withContext(Dispatchers.Main) {
                                Log.e(LOG_TAG, "Recording error", e)
                                isRecording = false
                            }
                        }
                    },
                    onAmplitude = { amplitude ->
                        recordingAmplitude = amplitude
                    }
                )
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            isRecording = false
        }
    }

    fun transcribeFile(file: File) = viewModelScope.launch {
        transcribeAudio(file)
    }

    fun deleteNote(note: Note) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            noteDao.deleteNote(note)
        }
    }

    /**
     * Semantic search: Find notes similar to the query text
     * Returns a list of notes sorted by similarity score (most similar first)
     */
    suspend fun searchNotes(query: String): List<Pair<Note, Float>> = withContext(Dispatchers.IO) {
        try {
            // Generate embedding for query
            val queryEmbedding = textEmbedding.embed(query) ?: return@withContext emptyList()

            // Get all notes with embeddings
            val allNotesList = noteDao.getAllNotes().first()
            val notesWithScores = mutableListOf<Pair<Note, Float>>()

            // Calculate similarity for each note
            for (note in allNotesList) {
                note.embedding?.let { embeddingJson ->
                    try {
                        val noteEmbedding = textEmbedding.jsonToEmbedding(embeddingJson)
                        val similarity = textEmbedding.cosineSimilarity(queryEmbedding, noteEmbedding)
                        notesWithScores.add(note to similarity)
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, "Failed to parse embedding for note ${note.id}")
                    }
                }
            }

            // Sort by similarity (highest first) and return
            notesWithScores.sortedByDescending { it.second }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error searching notes", e)
            emptyList()
        }
    }

    private suspend fun getTempFileForRecording() = withContext(Dispatchers.IO) {
        val recordingsDir = File(application.filesDir, "recordings")
        recordingsDir.mkdirs()
        val timestamp = System.currentTimeMillis()
        File(recordingsDir, "recording_$timestamp.wav")
    }

    override fun onCleared() {
        runBlocking {
            whisperContext?.release()
            whisperContext = null
            stopPlayback()
            textEmbedding.close()
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                MainScreenViewModel(application)
            }
        }
    }
}

