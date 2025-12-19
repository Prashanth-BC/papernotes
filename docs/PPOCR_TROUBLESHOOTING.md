# PP-OCRv4 Troubleshooting Guide

## Common Issues and Solutions

### 1. Models Not Loading

#### Error: "Model file not found"
```
Error: Cannot find model file: models/ppocr_det_v4.nb
```

**Causes:**
- Models not downloaded
- Models not copied to assets
- Incorrect file paths

**Solutions:**

```bash
# Option 1: Run setup script
cd /Users/PBANGAL/workspace/papernotes
./scripts/setup_ppocr.sh

# Option 2: Manual setup
cd scripts
python3 optimize_ppocr_models.py
cp optimized_models/*.nb ../app/src/main/assets/models/
cp optimized_models/ppocr_keys_v1.txt ../app/src/main/assets/

# Option 3: Verify files exist
ls -lh app/src/main/assets/models/
# Should see: ppocr_det_v4.nb, ppocr_rec_v4.nb, ppocr_cls.nb
```

#### Error: "Failed to load model"
```
PaddleException: Load model failed
```

**Causes:**
- Corrupted model files
- Incompatible Paddle Lite version
- Wrong model format

**Solutions:**

```bash
# Re-download models
rm -rf scripts/optimized_models
python3 scripts/optimize_ppocr_models.py

# Check model file sizes
ls -lh app/src/main/assets/models/*.nb
# ppocr_det_v4.nb should be ~3-4 MB
# ppocr_rec_v4.nb should be ~10-12 MB
# ppocr_cls.nb should be ~1-2 MB

# Verify Paddle Lite version in build.gradle.kts
# Should be: implementation("com.github.PaddlePaddle.Paddle-Lite:lite_java:2.11.0")
```

---

### 2. Low OCR Accuracy

#### Issue: Confidence < 0.5 or incorrect text

**For handwritten notes:**

```kotlin
// Try yellow paper preset
val preprocessed = ImagePreprocessor.preprocessYellowPaper(bitmap)

// Or adjust threshold manually
val preprocessed = ImagePreprocessor.preprocessForOCR(
    bitmap,
    applyGrayscale = true,
    applyBinarization = true,
    threshold = 0.55f,  // Try values between 0.5-0.7
    applyNoiseReduction = true
)
```

**For light pencil:**

```kotlin
// Increase contrast first
val contrasted = ImagePreprocessor.adjustContrast(bitmap, 2.0f)
val preprocessed = ImagePreprocessor.preprocessLightPencil(contrasted)
```

**For dark backgrounds:**

```kotlin
// Invert colors first
// (implement custom preprocessor or use OpenCV)
```

**Debug preprocessing:**

```kotlin
// Save preprocessed image to inspect
val preprocessed = ImagePreprocessor.preprocessForOCR(bitmap)
val file = File(context.cacheDir, "debug_preprocessed.jpg")
preprocessed.compress(Bitmap.CompressFormat.JPEG, 95, FileOutputStream(file))
Log.d("Debug", "Saved to: ${file.absolutePath}")
// Open file to see if text is clear
```

#### Issue: Text partially recognized

**Causes:**
- Image too small
- Low resolution
- Blurry image

**Solutions:**

```kotlin
// Check image size
if (bitmap.width < 720 || bitmap.height < 720) {
    Log.w("OCR", "Image too small: ${bitmap.width}x${bitmap.height}")
    // Recommend re-scanning
}

// Sharpen before OCR
val sharpened = ImagePreprocessor.sharpen(bitmap)
val result = ocrHelper.recognizeText(sharpened)
```

---

### 3. Performance Issues

#### Issue: OCR takes > 1 second

**Solutions:**

```kotlin
// 1. Resize large images
val MAX_DIMENSION = 1920
val resized = ImagePreprocessor.resizeMaxDimension(bitmap, MAX_DIMENSION)

// 2. Check device capabilities
val cores = Runtime.getRuntime().availableProcessors()
Log.d("Performance", "Device has $cores cores")

// 3. Profile different stages
val start = System.currentTimeMillis()
val preprocessed = ImagePreprocessor.preprocessForOCR(bitmap)
Log.d("Perf", "Preprocessing: ${System.currentTimeMillis() - start}ms")

val ocrStart = System.currentTimeMillis()
val result = ocrHelper.recognizeText(preprocessed)
Log.d("Perf", "OCR: ${System.currentTimeMillis() - ocrStart}ms")
```

#### Issue: App crashes with OutOfMemoryError

**Causes:**
- Large images
- Not releasing resources
- Multiple OCR instances

**Solutions:**

```kotlin
// 1. Always release OCR helper
try {
    val result = ocrHelper.recognizeText(bitmap)
    // Use result
} finally {
    ocrHelper.release()
}

// 2. Recycle bitmaps
val preprocessed = ImagePreprocessor.preprocessForOCR(bitmap)
val result = ocrHelper.recognizeText(preprocessed)
preprocessed.recycle()  // Free memory

// 3. Limit image size
val MAX_SIZE = 2048
if (bitmap.width > MAX_SIZE || bitmap.height > MAX_SIZE) {
    val resized = ImagePreprocessor.resizeMaxDimension(bitmap, MAX_SIZE)
    bitmap.recycle()
    bitmap = resized
}

// 4. Process in background
CoroutineScope(Dispatchers.Default).launch {
    val result = ocrHelper.recognizeText(bitmap)
    withContext(Dispatchers.Main) {
        // Update UI
    }
}
```

---

### 4. Build Issues

#### Error: "Could not resolve Paddle Lite dependency"

```
Could not resolve: com.github.PaddlePaddle.Paddle-Lite:lite_java:2.11.0
```

**Solution:**

Check `settings.gradle.kts`:
```kotlin
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }  // Must be present
}
```

Sync Gradle:
```bash
./gradlew clean
./gradlew --refresh-dependencies
```

#### Error: "NDK not found"

```
No version of NDK matched the requested version
```

**Solution:**

Install NDK in Android Studio:
1. Tools → SDK Manager
2. SDK Tools tab
3. Check "NDK (Side by side)"
4. Apply

Or set in `local.properties`:
```properties
ndk.dir=/Users/PBANGAL/Library/Android/sdk/ndk/25.1.8937393
```

---

### 5. Runtime Issues

#### Issue: Blank results (empty text)

**Causes:**
- No text detected
- Detection threshold too high
- Image preprocessing too aggressive

**Solutions:**

```kotlin
// Lower detection threshold (in PPOCRv4Helper.kt)
private const val DET_THRESHOLD = 0.2f  // Try 0.2 instead of 0.3

// Try without binarization
val preprocessed = ImagePreprocessor.preprocessForOCR(
    bitmap,
    applyGrayscale = true,
    applyBinarization = false  // Skip binarization
)

// Check detection results
val result = ocrHelper.recognizeText(bitmap)
Log.d("OCR", "Found ${result.boxes.size} text boxes")
if (result.boxes.isEmpty()) {
    Log.w("OCR", "No text detected - try different preprocessing")
}
```

#### Issue: Garbled characters

**Causes:**
- Wrong character dictionary
- Model corruption
- Incorrect CTC decoding

**Solutions:**

```bash
# Re-download dictionary
cd app/src/main/assets
curl -O https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/en_dict.txt
mv en_dict.txt ppocr_keys_v1.txt

# Verify it contains English characters
head ppocr_keys_v1.txt
# Should show: a b c d e ...
```

---

### 6. Integration Issues

#### Issue: Scanner not calling OCR

**Check ScannerManager.kt:**

```kotlin
// Ensure PPOCRv4Helper is initialized
private val ocrHelper = PPOCRv4Helper(context)

// Ensure OCR code is not commented out
val preprocessed = ImagePreprocessor.preprocessForOCR(bitmap)
val result = ocrHelper.recognizeText(preprocessed)
val text = result.text  // Should not be empty string placeholder
```

#### Issue: OCR results not saved to database

**Check that text is passed to NoteEntity:**

```kotlin
val note = NoteEntity(
    ocrText = text,  // Ensure this is the OCR result, not ""
    textEmbedding = embedding,
    // ... other fields
)
box.put(note)
```

---

## Diagnostic Checklist

### Before Running OCR

- [ ] Models exist in `app/src/main/assets/models/`
- [ ] Dictionary exists in `app/src/main/assets/ppocr_keys_v1.txt`
- [ ] Paddle Lite dependency in `build.gradle.kts`
- [ ] JitPack repository in `settings.gradle.kts`
- [ ] Image resolution >= 720p
- [ ] Image is clear and in focus

### During OCR

- [ ] Preprocessing applied correctly
- [ ] No exceptions in Logcat
- [ ] Models load successfully
- [ ] Detection finds text boxes
- [ ] Recognition returns text (not empty)
- [ ] Confidence score > 0.5

### After OCR

- [ ] Resources released (ocrHelper.release())
- [ ] Bitmaps recycled
- [ ] Text saved to database
- [ ] No memory leaks

---

## Performance Benchmarks

### Expected Performance (by device)

| Device Tier | Inference Time | Notes |
|-------------|----------------|-------|
| High-end (Snapdragon 888+) | 150-300ms | Optimal |
| Mid-range (Snapdragon 778) | 300-500ms | Good |
| Budget (Snapdragon 680) | 500-800ms | Acceptable |
| Older devices | 800ms+ | May need optimization |

### If Performance is Below Expected

1. **Check device temperature**: Thermal throttling slows CPU
2. **Close background apps**: Free up memory/CPU
3. **Use smaller images**: Resize to 1024x1024 max
4. **Reduce preprocessing**: Skip noise reduction if not needed
5. **Profile with Android Profiler**: Find bottlenecks

---

## Logging & Debugging

### Enable Verbose Logging

In `PPOCRv4Helper.kt`:
```kotlin
companion object {
    private const val TAG = "PPOCRv4Helper"
    private const val DEBUG = true  // Enable debug logs
}

if (DEBUG) {
    Log.d(TAG, "Detection found ${boxes.size} boxes")
    boxes.forEachIndexed { i, box ->
        Log.d(TAG, "Box $i: ${box.contentToString()}")
    }
}
```

### View Logcat Filters

```bash
# OCR-related logs only
adb logcat -s PPOCRv4Helper ImagePreprocessor ScannerManager

# Performance logs
adb logcat | grep "ms"

# Errors only
adb logcat *:E
```

---

## Getting Help

### Information to Include in Bug Reports

1. **Device info**: Model, Android version, RAM
2. **Sample image**: (if possible) or describe content
3. **Logcat output**: Full error messages
4. **Model files**: Confirm sizes and locations
5. **Preprocessing used**: Which preset or custom settings
6. **Expected vs actual**: What you expected vs what happened

### Useful Commands

```bash
# Check if models exist
adb shell ls -lh /data/data/com.example.notes/files/

# Pull preprocessed image from device
adb pull /data/data/com.example.notes/cache/preprocessed_*.jpg

# Check app memory usage
adb shell dumpsys meminfo com.example.notes

# Monitor performance
adb shell top | grep com.example.notes
```

---

## Quick Fixes

### "Just make it work!"

```bash
# Nuclear option: Start fresh
cd /Users/PBANGAL/workspace/papernotes
./gradlew clean
rm -rf app/src/main/assets/models/*
./scripts/setup_ppocr.sh
./gradlew assembleDebug
```

### "It was working, now it's not!"

```bash
# Check what changed
git status
git diff

# Revert recent changes
git log --oneline
git checkout <previous-commit>
```

### "I just want it to work offline!"

The setup already works 100% offline! Just ensure:
1. Models are in assets (run setup script once)
2. No network calls in OCR code
3. Test in airplane mode

---

## Advanced Troubleshooting

### Custom Model Version

If you need a different model variant:

```bash
# List available models
curl https://paddleocr.bj.bcebos.com/ | grep PP-OCR

# Download Chinese model
wget https://paddleocr.bj.bcebos.com/PP-OCRv4/chinese/ch_PP-OCRv4_rec_infer.tar

# Optimize
paddle_lite_opt --model_file=... --param_file=... --optimize_out=...
```

### Profile with Android Studio

1. Run app with Profiler attached
2. Perform OCR operation
3. Check:
   - CPU usage (should spike during inference)
   - Memory (should not continuously increase)
   - Thread activity (4 threads active during inference)

### Compare with Original Image

```kotlin
// Process same image multiple ways
val results = mutableListOf<String>()

// No preprocessing
results.add(ocrHelper.recognizeText(bitmap).text)

// Grayscale only
val gray = ImagePreprocessor.preprocessForOCR(bitmap, applyBinarization = false)
results.add(ocrHelper.recognizeText(gray).text)

// Full preprocessing
val full = ImagePreprocessor.preprocessForOCR(bitmap)
results.add(ocrHelper.recognizeText(full).text)

// Log all results
results.forEachIndexed { i, text ->
    Log.d("Compare", "Method $i: $text")
}
```

---

## Still Having Issues?

1. Check the [setup guide](PPOCR_SETUP.md) again
2. Review [architecture docs](PPOCR_ARCHITECTURE.md)
3. Look at [usage examples](../app/src/main/java/com/example/notes/ml/PPOCRExamples.kt)
4. Search PaddleOCR issues: https://github.com/PaddlePaddle/PaddleOCR/issues
5. Open an issue with full details

Remember: Most issues are from missing models or incorrect preprocessing! 🔍
