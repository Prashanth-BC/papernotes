# PaperNotes Model Setup Scripts

This directory contains scripts for downloading and setting up the machine learning models used by the PaperNotes app.

## Quick Start

Run the download script to get all required models:

```bash
./scripts/download_all_models.sh
```

This will download all models (~281 MB total) to the correct locations in `app/src/main/assets/`.

## Overview

The PaperNotes app uses multiple ML models for different purposes:
- **Image Embeddings**: CLIP and MobileNetV3 for visual similarity search
- **Text Embeddings**: all-MiniLM-L6-v2 for semantic text search
- **OCR**: ML Kit (Google) and PaddleOCR for text recognition
- **Visual Features**: TrOCR encoder for OCR-specific visual embeddings

## Models Used

### 1. Text Embedding (all-MiniLM-L6-v2)
- **File**: `all_minilm_l6_v2.onnx` (86 MB)
- **Purpose**: Semantic text embeddings for similarity search
- **Dimensions**: 384
- **Used by**: `TextEmbedderHelperOnnx.kt`
- **Source**: Hugging Face (sentence-transformers/all-MiniLM-L6-v2)
- **Format**: ONNX (quantized)

### 2. CLIP Image Embedding
- **File**: `clip_vit_b32_quantized.onnx` (85 MB)
- **Purpose**: Robust image embeddings for visual search
- **Dimensions**: 512
- **Used by**: `CLIPImageEmbedder.kt`
- **Source**: Hugging Face (Xenova/clip-vit-base-patch32)
- **Format**: ONNX (quantized)
- **Advantages**: Robust to variations, good for general images

### 3. MobileNetV3 Image Embedding
- **File**: `mobilenet_v3_image_embedder.tflite` (10 MB)
- **Purpose**: Fast image embeddings
- **Dimensions**: 1280
- **Used by**: `ImageEmbedderHelper.kt`
- **Source**: TensorFlow Hub (Google)
- **Format**: TensorFlow Lite
- **Advantages**: Fast inference, mobile-optimized

### 4. TrOCR Visual Encoder
- **File**: `trocr_encoder_quantized.onnx` (84 MB)
- **Purpose**: Visual embeddings specialized for OCR
- **Dimensions**: 768
- **Used by**: `TrOCREncoderHelper.kt`
- **Source**: Hugging Face (microsoft/trocr-base-handwritten)
- **Format**: ONNX (quantized)
- **Advantages**: Captures visual patterns relevant for text

### 5. PaddleOCR Recognition (EN v5)
- **File**: `models/en_PP-OCRv5/rec.onnx` (16 MB)
- **Purpose**: Text recognition for detected regions
- **Used by**: `ColorBasedOCR.kt`
- **Source**: PaddleOCR
- **Format**: ONNX
- **Note**: Uses custom HSV color-based detection (no detection model needed)

### 6. Text Tokenizer
- **File**: `tokenizer.json` (455 KB)
- **Purpose**: WordPiece tokenization for text embeddings
- **Used by**: `TextEmbedderHelperOnnx.kt`
- **Source**: Hugging Face (sentence-transformers/all-MiniLM-L6-v2)

### 7. Character Dictionary
- **File**: `labels/ppocrv5_dict.txt` (74 KB)
- **Purpose**: Character vocabulary for OCR recognition
- **Used by**: `ColorBasedOCR.kt`
- **Source**: PaddleOCR

### 8. ML Kit Text Recognition
- **Purpose**: Fast and accurate printed text OCR
- **Used by**: `MLKitOCREngine.kt`
- **Note**: No model file needed (built into Google ML Kit library)
- **Advantages**: No download required, fast, accurate for printed text

## Directory Structure

After running the setup script:

```
app/src/main/assets/
├── models/
│   └── en_PP-OCRv5/
│       └── rec.onnx                      (16 MB - recognition model)
├── labels/
│   └── ppocrv5_dict.txt                  (74 KB - character dictionary)
├── all_minilm_l6_v2.onnx                 (86 MB - text embeddings)
├── clip_vit_b32_quantized.onnx           (85 MB - image embeddings)
├── mobilenet_v3_image_embedder.tflite    (10 MB - fast image embeddings)
├── trocr_encoder_quantized.onnx          (84 MB - visual OCR embeddings)
└── tokenizer.json                        (455 KB - text tokenizer)
```

**Total size**: ~281 MB

## Download Script

The `download_all_models.sh` script:
- Downloads all required models from official sources
- Places them in the correct locations
- Skips already-downloaded files
- Shows progress and file sizes
- Verifies successful downloads

## ML Framework Architecture

The app uses different frameworks optimized for each task:

- **ONNX Runtime**:
  - CLIP image embeddings (512-dim)
  - all-MiniLM-L6-v2 text embeddings (384-dim)
  - TrOCR visual encoder (768-dim)
  - PaddleOCR recognition
  - Benefits: Quantization, cross-platform

- **TensorFlow Lite**:
  - MobileNetV3 image embeddings (1280-dim)
  - Benefits: Mobile-optimized, GPU acceleration

- **ML Kit**:
  - Text recognition (OCR)
  - Benefits: No model download, maintained by Google

## OCR System Architecture

### Dual OCR Engine System

The app uses two complementary OCR engines:

#### 1. ML Kit OCR (Primary for Printed Text)
- **Fast and accurate** for printed documents
- **No model required** (built-in)
- **Used for**: Documents, typed notes, screenshots

#### 2. ColorBased OCR (For Handwritten Text)
- **Detection**: Custom HSV color-based algorithm (no neural network)
  - Detects dark text on light backgrounds
  - Finds individual characters
  - No model file needed
- **Recognition**: PaddleOCR v5 (`rec.onnx`)
  - Recognizes detected character regions
  - Good for handwritten text
- **Grouping**: Post-recognition character grouping
  - Combines individual characters into words
- **Used for**: Handwritten notes, mixed text

**Why no detection model?**
- Custom color-based detection is faster and doesn't require a model
- Works well for typical note-taking scenarios (dark text on light paper)
- Reduces app size by ~85 MB

## Troubleshooting

### Download Fails
- Check internet connection
- Verify URLs are accessible (Hugging Face, TensorFlow Hub)
- Try manual download (see sections below)

### App Crashes After Model Update
1. Clean and rebuild: `./gradlew clean assembleDebug`
2. Verify all models exist in `app/src/main/assets`
3. Check model file sizes match expected sizes

### Out of Memory Errors
- Models use quantized versions to reduce memory
- Disable GPU in app settings if issues persist
- Check device has sufficient RAM

### OCR Not Working
- Verify `rec.onnx` and `ppocrv5_dict.txt` exist
- Check logs for model loading errors
- Try both ML Kit and ColorBased OCR engines

## Manual Download

If automatic download fails, download manually from these sources:

### Hugging Face Models

```bash
# Text embeddings
curl -L -o app/src/main/assets/all_minilm_l6_v2.onnx \
  "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx"

# CLIP image embeddings
curl -L -o app/src/main/assets/clip_vit_b32_quantized.onnx \
  "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/vision_model_quantized.onnx"

# TrOCR encoder
curl -L -o app/src/main/assets/trocr_encoder_quantized.onnx \
  "https://huggingface.co/microsoft/trocr-base-handwritten/resolve/main/encoder_model_quantized.onnx"

# Tokenizer
curl -L -o app/src/main/assets/tokenizer.json \
  "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json"
```

### TensorFlow Hub Model

```bash
# MobileNetV3 image embedder
curl -L -o app/src/main/assets/mobilenet_v3_image_embedder.tflite \
  "https://tfhub.dev/google/lite-model/imagenet/mobilenet_v3_small_100_224/feature_vector/5/metadata/1?lite-format=tflite"
```

### PaddleOCR Models

Visit: https://github.com/PaddlePaddle/PaddleOCR

Or use pre-converted ONNX versions from community sources.

## Verification

After download, verify your setup:

```bash
# Check all models
find app/src/main/assets -type f \( -name "*.onnx" -o -name "*.tflite" -o -name "*.json" -o -name "*.txt" \) -exec ls -lh {} \;

# Expected files:
# - all_minilm_l6_v2.onnx (~86 MB)
# - clip_vit_b32_quantized.onnx (~85 MB)
# - mobilenet_v3_image_embedder.tflite (~10 MB)
# - trocr_encoder_quantized.onnx (~84 MB)
# - models/en_PP-OCRv5/rec.onnx (~16 MB)
# - tokenizer.json (~455 KB)
# - labels/ppocrv5_dict.txt (~74 KB)
```

## Build and Test

After models are downloaded:

1. **Build the app**:
   ```bash
   ./gradlew assembleDebug
   ```

2. **Install and test**:
   - Install APK on Android device
   - Scan a document (tests OCR)
   - Add multiple notes (tests image embeddings)
   - Search by image (tests CLIP and MobileNet)
   - Search by text (tests text embeddings)

## References

- **Hugging Face**: https://huggingface.co/
  - all-MiniLM-L6-v2: sentence-transformers/all-MiniLM-L6-v2
  - CLIP: Xenova/clip-vit-base-patch32
  - TrOCR: microsoft/trocr-base-handwritten
- **TensorFlow Hub**: https://tfhub.dev/
- **PaddleOCR**: https://github.com/PaddlePaddle/PaddleOCR
- **Google ML Kit**: https://developers.google.com/ml-kit
- **ONNX Runtime**: https://onnxruntime.ai/

## License

Each model has its own license:
- **all-MiniLM-L6-v2**: Apache 2.0
- **CLIP**: MIT License
- **MobileNetV3**: Apache 2.0
- **TrOCR**: MIT License
- **PaddleOCR**: Apache 2.0
