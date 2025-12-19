# PP-OCRv4 Integration Guide for Paper Notes App

This guide explains how to integrate PP-OCRv4 with Paddle Lite for high-accuracy handwriting recognition on Android.

## Overview

PP-OCRv4 provides ~10% improvement in English text recognition compared to v3, making it ideal for handwritten notes. This implementation uses Paddle Lite for efficient on-device inference.

## Prerequisites

1. **Python Environment** (for model optimization)
   ```bash
   pip install paddlelite paddlepaddle
   ```

2. **Android Studio** with NDK installed
   - NDK version: 21.0+ recommended
   - CMake 3.10+

## Step 1: Optimize Models

Run the optimization script to convert PP-OCRv4 models to Paddle Lite format:

```bash
cd /Users/PBANGAL/workspace/papernotes/scripts
python3 optimize_ppocr_models.py
```

This will:
- Download PP-OCRv4 English models (detection, recognition, classifier)
- Convert them to `.nb` (Naive Buffer) format for Paddle Lite
- Download the English dictionary file
- Save everything to `optimized_models/` directory

Expected output:
```
optimized_models/
├── ppocr_det_v4.nb      (~3.5 MB)  - Text detection model
├── ppocr_rec_v4.nb      (~10 MB)   - Text recognition model  
├── ppocr_cls.nb         (~2 MB)    - Text angle classifier
└── ppocr_keys_v1.txt    (~5 KB)    - Character dictionary
```

## Step 2: Copy Models to Android Assets

```bash
# Create models directory
mkdir -p app/src/main/assets/models

# Copy optimized models
cp scripts/optimized_models/ppocr_det_v4.nb app/src/main/assets/models/
cp scripts/optimized_models/ppocr_rec_v4.nb app/src/main/assets/models/
cp scripts/optimized_models/ppocr_cls.nb app/src/main/assets/models/
cp scripts/optimized_models/ppocr_keys_v1.txt app/src/main/assets/
```

## Step 3: Add Paddle Lite Dependencies

The build.gradle.kts has been updated with Paddle Lite dependencies. If you need to add them manually:

```kotlin
dependencies {
    // Paddle Lite for PP-OCRv4
    implementation("org.paddlepaddle:paddle-lite-java:3.0.0")
    implementation("org.paddlepaddle:paddle-lite-android:3.0.0")
}
```

## Step 4: Use the PP-OCRv4 Helper

The `PPOCRv4Helper` class provides a simple interface:

```kotlin
// Initialize (do this once in your Application or Activity)
val ocrHelper = PPOCRv4Helper(context)

// Recognize text from bitmap
val result = ocrHelper.recognizeText(bitmap)

println("Detected text: ${result.text}")
println("Confidence: ${result.confidence}")
println("Boxes: ${result.boxes.size}")

// For handwritten notes on colored paper
val preprocessedBitmap = ImagePreprocessor.preprocessForOCR(
    bitmap = originalBitmap,
    applyGrayscale = true,
    applyBinarization = true,
    threshold = 0.6f
)
val result = ocrHelper.recognizeText(preprocessedBitmap)

// Don't forget to release resources when done
ocrHelper.release()
```

## Architecture

The PP-OCRv4 pipeline consists of three stages:

1. **Detection (DB)**: Locates text regions in the image
2. **Classification**: Determines text orientation (0°, 90°, 180°, 270°)
3. **Recognition (CRNN)**: Recognizes characters in each text region

```
Input Image
    ↓
[Detection Model] → Text Boxes
    ↓
[Classifier Model] → Rotation Angles
    ↓
[Recognition Model] → Text + Confidence
    ↓
Output: OCRResult
```

## Performance Expectations

| Metric | PP-OCRv4 Mobile | Notes |
|--------|----------------|-------|
| Model Size | ~16 MB total | Very lightweight |
| Inference Time | 200-600ms | Depends on image size |
| RAM Usage | ~150 MB | Works on budget devices |
| Accuracy (English) | 85-95% | ~10% better than v3 |
| Handwriting | 70-85% | Improved with preprocessing |

## Image Preprocessing Tips

For handwritten notes, preprocessing significantly improves accuracy:

1. **Grayscale Conversion**: Removes color distractions
   - Improvement: ~5-10%

2. **Binary Thresholding**: Creates high contrast black/white
   - Improvement: ~10-15% for colored backgrounds

3. **Noise Reduction**: Removes paper texture
   - Improvement: ~3-5%

4. **Deskewing**: Corrects rotated images
   - Improvement: ~5-10%

Example for yellow legal pad notes:
```kotlin
val processed = ImagePreprocessor.preprocessForOCR(
    bitmap = scannedNote,
    applyGrayscale = true,
    applyBinarization = true,
    threshold = 0.6f,
    applyNoiseReduction = true
)
```

## Troubleshooting

### Models Not Loading
- Ensure .nb files are in `app/src/main/assets/models/`
- Check file sizes match expected values
- Verify dictionary file is in `app/src/main/assets/`

### Low Accuracy
- Try preprocessing with binary thresholding
- Ensure image resolution is at least 720p
- Check that text is right-side-up

### Performance Issues
- Reduce input image size to 1024x1024 max
- Use ARM64 ABI only (already configured)
- Enable GPU acceleration if available

### Build Errors
- Ensure NDK is installed
- Check that CMake version is 3.10+
- Clean and rebuild project

## Alternative: Using Community Libraries

If you prefer a pre-built solution, consider:

1. **paddleocr4android** by equationl
   - GitHub: [equationl/paddleocr4android](https://github.com/equationl/paddleocr4android)
   - Pre-compiled PP-OCRv4 support
   - Simpler integration

2. **ncnn_paddleocr** by FeiGeChuanShu  
   - GitHub: [FeiGeChuanShu/ncnn_paddleocr](https://github.com/FeiGeChuanShu/ncnn_paddleocr)
   - Uses ncnn engine (faster on some chips)
   - Requires model conversion to ncnn format

## References

- [PaddleOCR Official Docs](https://github.com/PaddlePaddle/PaddleOCR)
- [Paddle Lite Android Guide](https://paddle-lite.readthedocs.io/zh/latest/demo_guides/android_app_demo.html)
- [PP-OCRv4 Technical Report](https://arxiv.org/abs/2305.09520)

## Next Steps

1. Run the optimization script
2. Copy models to assets
3. Test with sample images
4. Tune preprocessing parameters for your use case
5. Integrate into scanner workflow

For production deployment, consider:
- Caching models in device storage
- Background processing for large batches
- Progressive loading for multi-page documents
