package com.whispercppdemo

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whispercppdemo.data.database.Note
import com.whispercppdemo.ui.main.MainScreen
import com.whispercppdemo.ui.main.MainScreenViewModel
import com.whispercppdemo.llm.LLMManager
import com.whispercppdemo.ui.notes.NoteDetailScreen
import com.whispercppdemo.ui.notes.NotesListScreen
import com.whispercppdemo.ui.notes.QAScreen
import com.whispercppdemo.ui.notes.SummaryScreen
import com.whispercppdemo.ui.theme.WhisperCppDemoTheme
import java.io.File

sealed class Screen {
    object Main : Screen()
    object NotesList : Screen()
    data class NoteDetail(val note: Note) : Screen()
    data class Summary(val note: Note) : Screen()
    data class QA(val note: Note) : Screen()
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainScreenViewModel by viewModels { MainScreenViewModel.factory() }
    private lateinit var llmManager: LLMManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        llmManager = LLMManager(applicationContext)
        setContent {
            WhisperCppDemoTheme {
                AppNavigation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        llmManager.release()
    }

    @Composable
    fun AppNavigation() {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
        val notes by viewModel.allNotes.collectAsStateWithLifecycle(initialValue = emptyList())

        // Navigate to NoteDetail when transcription completes
        LaunchedEffect(viewModel.transcribedNote) {
            viewModel.transcribedNote?.let { note ->
                currentScreen = Screen.NoteDetail(note)
            }
        }

        when (val screen = currentScreen) {
            is Screen.Main -> {
                MainScreen(
                    viewModel = viewModel,
                    onFileSelected = { uri ->
                        handleFileSelection(uri)
                    },
                    onViewNotesClick = {
                        currentScreen = Screen.NotesList
                    }
                )
            }
            is Screen.NotesList -> {
                NotesListScreen(
                    notes = notes,
                    onBackClick = { currentScreen = Screen.Main },
                    onNoteClick = { note ->
                        currentScreen = Screen.NoteDetail(note)
                    },
                    onDeleteNote = { note ->
                        viewModel.deleteNote(note)
                    }
                )
            }
            is Screen.NoteDetail -> {
                NoteDetailScreen(
                    note = screen.note,
                    onBackClick = {
                        currentScreen = Screen.Main
                    },
                    onSummaryClick = { currentScreen = Screen.Summary(screen.note) },
                    onQAClick = { currentScreen = Screen.QA(screen.note) }
                )
            }
            is Screen.Summary -> {
                SummaryScreen(
                    note = screen.note,
                    onBackClick = { currentScreen = Screen.NoteDetail(screen.note) },
                    onGenerateSummary = { text ->
                        llmManager.initialize()
                        llmManager.generateSummary(text)
                    }
                )
            }
            is Screen.QA -> {
                QAScreen(
                    note = screen.note,
                    onBackClick = { currentScreen = Screen.NoteDetail(screen.note) },
                    onAskQuestion = { context, question ->
                        llmManager.initialize()
                        llmManager.answerQuestion(context, question)
                    }
                )
            }
        }
    }

    private fun handleFileSelection(uri: Uri) {
        // Copy the selected file to a temporary location
        val inputStream = contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("selected_audio", ".wav", cacheDir)

        inputStream?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        viewModel.transcribeFile(tempFile)
    }
}