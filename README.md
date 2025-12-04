# Voice Notes - AI-Powered Note-Taking App

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)

**A fully-featured Android voice note-taking app with on-device AI, built on [whisper.cpp](https://github.com/ggml-org/whisper.cpp)**

## 🎥 Demo

[![Watch Demo](https://img.youtube.com/vi/wVuslMTra90/maxresdefault.jpg)](https://youtube.com/shorts/wVuslMTra90?si=r9UT8NwER3nNPjX-)

*Click the image above to watch the demo on YouTube*

## ✨ Features

This app extends the whisper.cpp Android example into a **fully functional note-taking application** with advanced AI capabilities:

### 🎙️ Voice Recording & Transcription
- **Real-time waveform visualization** during recording
- **On-device speech-to-text** using Whisper.cpp (Tiny Q8 model)
- **Timestamp-based transcription** with seekable audio playback
- **Offline transcription** - no internet required

### 🤖 On-Device AI
- **Local LLM inference** using Google AI Edge (Gemma 3 1B INT4)
- **Text embeddings** using all-MiniLM-L6-v2 (384 dimensions)
- **RAG (Retrieval Augmented Generation)** for intelligent Q&A on long transcriptions
- **Automatic summarization** of voice notes
- **Semantic search** across all notes using embeddings

### 📱 Modern UI
- **Material 3 Design** with dynamic theming
- **Circular record button** with intuitive controls
- **Processing animations** during transcription
- **Note list** with search and delete functionality
- **Audio player** with seek controls and time display

### 🔒 Privacy-First
- **100% offline** - all AI processing happens on-device
- **No internet required** after model download
- **No data sent to cloud** - your notes stay private
- **Local storage** - complete control over your data

## 🏗️ Architecture

### Tech Stack
- **Kotlin** + **Jetpack Compose** for modern Android UI
- **whisper.cpp** for efficient on-device speech recognition
- **Google AI Edge (MediaPipe)** for LLM inference
- **ONNX Runtime** for text embeddings
- **Room Database** for local storage
- **Coroutines** for async operations

### AI Models
1. **Whisper Tiny Q8** (42 MB) - Speech-to-text transcription
2. **Gemma 3 1B INT4** (529 MB) - Question answering and summarization
3. **all-MiniLM-L6-v2** (87 MB) - Text embeddings for semantic search

### RAG Implementation
For long transcriptions, the app uses:
- **Chunking**: Splits text into 1500-character chunks
- **Embedding**: Generates embeddings for each chunk
- **Retrieval**: Finds top 4 most relevant chunks using cosine similarity
- **Generation**: Sends relevant context to LLM for accurate answers

## 📦 Installation

### Prerequisites
- Android Studio Arctic Fox or newer
- Android device with Android 8.0+ (API 26+)
- ~700 MB of storage for model files

### Setup Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/goalhunter/voice-notes-android.git
   cd voice-notes-android
   ```

2. **Download AI models**

   Get all models from [Google Drive](https://drive.google.com/drive/folders/1dO2Wqe6b-FBWRtLeiktaCbH-PBy-wca0?usp=sharing)

   Place them in: `examples/whisper.android/app/src/main/assets/models/`

   Required files:
   - `gemma3-1b-it-int4.task` (529 MB)
   - `embedding_model.onnx` (87 MB)
   - `ggml-tiny-q8_0.bin` (42 MB)

3. **Open in Android Studio**
   ```bash
   cd examples/whisper.android
   ```
   Open this directory in Android Studio

4. **Build and Run**
   - Sync Gradle
   - Build the project
   - Run on device or emulator (device recommended for performance)

## 🎯 Usage

### Recording a Voice Note
1. Tap the circular **Record** button
2. Speak your note (waveform shows audio levels)
3. Tap **Stop** when finished
4. App processes and transcribes automatically
5. View transcription with timestamps and audio player

### AI Features
- **Generate Summary**: Get a 2-3 sentence summary of your note
- **Ask Questions**: Query your transcription using natural language
  - Uses RAG to find relevant sections for accurate answers
  - Works great for long recordings (meetings, lectures, etc.)

### Managing Notes
- **Browse**: View all notes in chronological order
- **Search**: Find notes by semantic similarity
- **Playback**: Listen to recordings with seek controls
- **Delete**: Swipe to delete notes

## 🔧 Configuration

### Memory Optimization
For long transcriptions, RAG limits are configured in `LLMManager.kt`:
```kotlin
MAX_INPUT_CHARS = 8000    // ~2000-2500 tokens
CHUNK_SIZE = 1500          // Characters per chunk
MAX_CHUNKS = 4             // Top chunks retrieved
```

### Model Selection
You can swap models by changing files in `assets/models/`:
- **Whisper**: Any GGML-format Whisper model (tiny, base, small, etc.)
- **LLM**: Any Gemma INT4 model compatible with MediaPipe
- **Embeddings**: Any ONNX sentence transformer model

## 📊 Performance

### Device Requirements
- **RAM**: 2-4 GB recommended
- **Storage**: 1 GB free space
- **Processor**: ARM64 (most modern Android devices)

### Benchmarks (on Samsung Galaxy S20)
- **Transcription**: ~1-2x realtime (1 min audio = 1-2 min processing)
- **Summarization**: ~5-10 seconds for typical note
- **Q&A**: ~3-5 seconds per question with RAG

## 🤝 Contributing

Contributions are welcome! This project was built on top of:
- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) by Georgi Gerganov
- [Google AI Edge](https://ai.google.dev/edge) for on-device LLM

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙏 Acknowledgments

- **whisper.cpp** - High-performance Whisper inference
- **Google AI Edge** - On-device LLM capabilities
- **Hugging Face** - all-MiniLM-L6-v2 embedding model
- Original whisper.cpp Android example that this app extends

## 📮 Contact

For questions or feedback, please open an issue on GitHub.

---

**Note**: This is an extension of the whisper.cpp Android example, transformed into a fully functional note-taking app with RAG capabilities. All AI processing runs **100% offline and locally** on your device.
