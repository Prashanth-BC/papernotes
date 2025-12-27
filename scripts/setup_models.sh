#!/bin/bash
# Setup all models for PaperNotes Android app
# Downloads all required ML models from official sources

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "========================================="
echo "PaperNotes Model Setup"
echo "========================================="
echo ""
echo "This will download all required ML models (~281 MB)"
echo ""

# Run the download script
bash "$SCRIPT_DIR/download_all_models.sh"

echo ""
echo "========================================="
echo "Setup complete!"
echo "========================================="
echo ""
echo "Next steps:"
echo "1. Build the app:"
echo "   ./gradlew clean assembleDebug"
echo ""
echo "2. Install on device and test:"
echo "   - Scan documents (OCR test)"
echo "   - Add multiple notes (embedding test)"
echo "   - Search by image or text (search test)"
echo ""
