#!/bin/bash

# Quick setup script for PP-OCRv4 integration
# This script downloads models and sets up your project

set -e  # Exit on error

echo "╔═══════════════════════════════════════════════════════════╗"
echo "║  PP-OCRv4 Setup for Paper Notes App                      ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPTS_DIR="$PROJECT_ROOT/scripts"
ASSETS_DIR="$PROJECT_ROOT/app/src/main/assets"
MODELS_DIR="$ASSETS_DIR/models"
VENV_DIR="$PROJECT_ROOT/.venv"

# Check Python
echo "→ Checking Python installation..."
if ! command -v python3 &> /dev/null; then
    echo "✗ Python 3 is not installed. Please install it first."
    exit 1
fi
echo "✓ Python 3 found"

# Setup virtual environment
echo ""
echo "→ Setting up virtual environment..."
if [ ! -d "$VENV_DIR" ]; then
    echo "Creating virtual environment..."
    python3 -m venv "$VENV_DIR"
fi
echo "✓ Virtual environment ready"

# Activate virtual environment
source "$VENV_DIR/bin/activate"

# Check pip packages
echo ""
echo "→ Checking required packages..."
if ! python3 -c "import paddlelite" 2>/dev/null; then
    echo "Installing paddlelite..."
    pip install paddlelite paddlepaddle
fi
echo "✓ PaddleLite installed"

# Create directories
echo ""
echo "→ Creating directories..."
mkdir -p "$MODELS_DIR"
echo "✓ Created $MODELS_DIR"

# Run optimization script
echo ""
echo "→ Optimizing PP-OCRv4 models..."
echo "  (This will download ~50MB and may take a few minutes)"
cd "$SCRIPTS_DIR"
python3 optimize_ppocr_models.py

# Copy models to assets
echo ""
echo "→ Copying models to Android assets..."
cp "$SCRIPTS_DIR/optimized_models/ppocr_det_v3.nb" "$MODELS_DIR/"
cp "$SCRIPTS_DIR/optimized_models/ppocr_rec_v3.nb" "$MODELS_DIR/"
cp "$SCRIPTS_DIR/optimized_models/ppocr_cls.nb" "$MODELS_DIR/"
cp "$SCRIPTS_DIR/optimized_models/ppocr_keys_v1.txt" "$ASSETS_DIR/"

echo "✓ Models copied to assets"

# Check file sizes
echo ""
echo "→ Verifying model files..."
for file in "$MODELS_DIR"/*.nb "$ASSETS_DIR/ppocr_keys_v1.txt"; do
    if [ -f "$file" ]; then
        size=$(ls -lh "$file" | awk '{print $5}')
        echo "  ✓ $(basename $file) - $size"
    else
        echo "  ✗ Missing: $(basename $file)"
        exit 1
    fi
done

# Clean up
echo ""
echo "→ Cleaning up temporary files..."
rm -rf "$SCRIPTS_DIR/optimized_models"
echo "✓ Cleanup complete"

echo ""
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║  Setup Complete! 🎉                                       ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""
echo "Next steps:"
echo "  1. Open the project in Android Studio"
echo "  2. Sync Gradle (it will download Paddle Lite SDK)"
echo "  3. Build and run the app"
echo "  4. Test OCR with sample handwritten notes"
echo ""
echo "Tips for better accuracy:"
echo "  • Use good lighting when scanning"
echo "  • Ensure text is in focus"
echo "  • For colored paper, preprocessing is applied automatically"
echo "  • Yellow/legal pad notes work best with binary thresholding"
echo ""
echo "Documentation: docs/PPOCR_SETUP.md"
