#!/bin/bash

# Script to download all models used by the PaperNotes app
# Models are downloaded and placed in the app/src/main/assets directory

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_ROOT/app/src/main/assets"

echo "======================================"
echo "PaperNotes Model Download Script"
echo "======================================"
echo ""
echo "Project root: $PROJECT_ROOT"
echo "Assets directory: $ASSETS_DIR"
echo ""

# Create necessary directories
mkdir -p "$ASSETS_DIR/models/en_PP-OCRv5"
mkdir -p "$ASSETS_DIR/labels"

# Function to download file if it doesn't exist
download_if_needed() {
    local url=$1
    local output_path=$2
    local filename=$(basename "$output_path")

    if [ -f "$output_path" ]; then
        echo "✓ $filename already exists, skipping download"
    else
        echo "⬇ Downloading $filename..."
        curl -L -o "$output_path" "$url"
        echo "✓ Downloaded $filename"
    fi
}

echo "======================================"
echo "1. Text Embedding Model (all-MiniLM-L6-v2)"
echo "======================================"
echo "Used by: TextEmbedderHelperOnnx"
echo "Purpose: Semantic text embeddings (384-dim)"
echo ""
download_if_needed \
    "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx" \
    "$ASSETS_DIR/all_minilm_l6_v2.onnx"

echo ""
echo "======================================"
echo "2. CLIP Image Embedding Model"
echo "======================================"
echo "Used by: CLIPImageEmbedder"
echo "Purpose: Robust image embeddings (512-dim)"
echo ""
download_if_needed \
    "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/vision_model_quantized.onnx" \
    "$ASSETS_DIR/clip_vit_b32_quantized.onnx"

echo ""
echo "======================================"
echo "3. MobileNetV3 Image Embedding Model"
echo "======================================"
echo "Used by: ImageEmbedderHelper"
echo "Purpose: Fast image embeddings (1280-dim)"
echo ""
download_if_needed \
    "https://tfhub.dev/google/lite-model/imagenet/mobilenet_v3_small_100_224/feature_vector/5/metadata/1?lite-format=tflite" \
    "$ASSETS_DIR/mobilenet_v3_image_embedder.tflite"

echo ""
echo "======================================"
echo "4. TrOCR Visual Encoder"
echo "======================================"
echo "Used by: TrOCREncoderHelper"
echo "Purpose: Visual embeddings for OCR (768-dim)"
echo ""
download_if_needed \
    "https://huggingface.co/microsoft/trocr-base-handwritten/resolve/main/encoder_model_quantized.onnx" \
    "$ASSETS_DIR/trocr_encoder_quantized.onnx"

echo ""
echo "======================================"
echo "5. PaddleOCR Recognition Model (EN v5)"
echo "======================================"
echo "Used by: ColorBasedOCR"
echo "Purpose: Text recognition for detected regions"
echo ""
download_if_needed \
    "https://paddleocr.bj.bcebos.com/PP-OCRv4/english/en_PP-OCRv4_rec_infer.tar" \
    "$ASSETS_DIR/models/en_PP-OCRv5/rec.onnx"

echo ""
echo "======================================"
echo "6. Tokenizer (for all-MiniLM-L6-v2)"
echo "======================================"
echo "Used by: TextEmbedderHelperOnnx"
echo "Purpose: WordPiece tokenization for text embeddings"
echo ""
download_if_needed \
    "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json" \
    "$ASSETS_DIR/tokenizer.json"

echo ""
echo "======================================"
echo "7. PaddleOCR Dictionary (v5)"
echo "======================================"
echo "Used by: ColorBasedOCR"
echo "Purpose: Character dictionary for recognition"
echo ""
download_if_needed \
    "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/release/2.7/ppocr/utils/ppocr_keys_v1.txt" \
    "$ASSETS_DIR/labels/ppocrv5_dict.txt"

echo ""
echo "======================================"
echo "✓ All models downloaded successfully!"
echo "======================================"
echo ""
echo "Summary of models:"
echo "  1. all_minilm_l6_v2.onnx (86MB) - Text embeddings"
echo "  2. clip_vit_b32_quantized.onnx (85MB) - Image embeddings"
echo "  3. mobilenet_v3_image_embedder.tflite (10MB) - Fast image embeddings"
echo "  4. trocr_encoder_quantized.onnx (84MB) - Visual OCR embeddings"
echo "  5. models/en_PP-OCRv5/rec.onnx (16MB) - Text recognition"
echo "  6. tokenizer.json (455KB) - Text tokenizer"
echo "  7. labels/ppocrv5_dict.txt (74KB) - Character dictionary"
echo ""
echo "Total size: ~281 MB"
echo ""
echo "Models are ready to use in the PaperNotes app!"
