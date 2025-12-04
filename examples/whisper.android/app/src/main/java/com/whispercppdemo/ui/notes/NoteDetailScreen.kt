package com.whispercppdemo.ui.notes

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.whispercppdemo.data.database.Note
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    note: Note,
    onBackClick: () -> Unit,
    onSummaryClick: () -> Unit = {},
    onQAClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()) }

    // Audio player state
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }

    // Initialize MediaPlayer
    DisposableEffect(note.audioFilePath) {
        val audioFile = File(note.audioFilePath ?: "")
        if (audioFile.exists()) {
            try {
                val player = MediaPlayer.create(context, audioFile.absolutePath.toUri())
                mediaPlayer = player
                duration = player?.duration ?: 0

                player?.setOnCompletionListener {
                    isPlaying = false
                    currentPosition = 0
                }
            } catch (e: Exception) {
                mediaPlayer = null
            }
        }

        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    // Update current position while playing
    LaunchedEffect(isPlaying) {
        while (isActive && isPlaying) {
            mediaPlayer?.let {
                currentPosition = it.currentPosition
            }
            delay(100)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Note Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Audio Player Section - Always show
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Audio Player",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    if (mediaPlayer != null) {
                        // Progress slider
                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { newPosition ->
                                mediaPlayer?.seekTo(newPosition.toInt())
                                currentPosition = newPosition.toInt()
                            },
                            valueRange = 0f..duration.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Time display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = formatTime(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        // Play/Pause button
                        FilledTonalButton(
                            onClick = {
                                mediaPlayer?.let { player ->
                                    if (isPlaying) {
                                        player.pause()
                                        isPlaying = false
                                    } else {
                                        player.start()
                                        isPlaying = true
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isPlaying) "Pause" else "Play Audio")
                        }
                    } else {
                        Text(
                            text = "Audio file not available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Actions Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "AI Actions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    // Summary Button
                    FilledTonalButton(
                        onClick = onSummaryClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📝 Generate Summary")
                    }

                    // Q&A Button
                    FilledTonalButton(
                        onClick = onQAClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("💬 Ask Questions (Q&A)")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Two-column layout for note details
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Timestamp Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Created:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(100.dp)
                        )
                        Text(
                            text = dateFormatter.format(Date(note.timestamp)),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Divider()

                    // Duration Row
                    note.duration?.let { duration ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "Duration:",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(100.dp)
                            )
                            Text(
                                text = "${duration / 1000}s",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Divider()
                    }

                    // Transcription with timestamps
                    val transcriptLines = parseTranscriptWithTimestamps(note.transcribedText)

                    transcriptLines.forEachIndexed { index, line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = line.timestamp,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(100.dp)
                            )
                            SelectionContainer {
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (index < transcriptLines.size - 1) {
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Int): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

data class TranscriptLine(
    val timestamp: String,
    val text: String
)

private fun parseTranscriptWithTimestamps(transcribedText: String): List<TranscriptLine> {
    val lines = transcribedText.split("\n")
    val result = mutableListOf<TranscriptLine>()

    // Regex to match timestamps like [00:00:00.000 --> 00:00:05.000]: text
    val timestampRegex = """\[(\d{2}:\d{2}:\d{2}\.\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}\.\d{3})\]:\s*(.*)""".toRegex()

    for (line in lines) {
        val match = timestampRegex.find(line.trim())
        if (match != null) {
            val startTime = match.groupValues[1]
            val text = match.groupValues[3].trim()
            if (text.isNotEmpty()) {
                result.add(TranscriptLine(startTime, text))
            }
        } else if (line.trim().isNotEmpty()) {
            // Line without timestamp, add it with empty timestamp
            result.add(TranscriptLine("", line.trim()))
        }
    }

    // If no timestamps were found, return the whole text as one line
    if (result.isEmpty()) {
        result.add(TranscriptLine("", transcribedText))
    }

    return result
}
