# Model Files

Download the required model files and place them in this directory.

## Download Links

Get all models from: [Google Drive Link - Add your link here]

## Required Files

Place these files in this `models/` directory:

1. **gemma3-1b-it-int4.task** (529 MB)
   - Gemma 3 1B quantized model for on-device LLM inference
   - Used for Q&A and summarization

2. **embedding_model.onnx** (87 MB)
   - Text embedding model (all-MiniLM-L6-v2, 384 dimensions)
   - Used for semantic search and RAG

3. **ggml-tiny-q8_0.bin** (42 MB)
   - Whisper Tiny quantized model
   - Used for speech-to-text transcription

## File Structure

After downloading, your directory should look like:
```
app/src/main/assets/models/
├── README.md (this file)
├── gemma3-1b-it-int4.task
├── embedding_model.onnx
└── ggml-tiny-q8_0.bin
```
