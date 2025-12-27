# Google ML Kit Text Recognition Integration

## Why ML Kit?
- ✅ TFLite-based (native Android optimization)
- ✅ No model download needed (bundled)
- ✅ Automatic updates from Google
- ✅ Excellent accuracy
- ✅ Free for moderate usage
- ✅ Easy integration (15 minutes)

## Integration Steps

### 1. Add Dependency

Add to `app/build.gradle.kts`:

```kotlin
dependencies {
    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // Keep existing dependencies...
}
```

### 2. Create ML Kit OCR Engine

Create `MLKitOCREngine.kt`:

```kotlin
package com.example.notes.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class MLKitOCREngine(private val context: Context) : OCREngine {

    private val TAG = "MLKitOCREngine"
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeText(bitmap: Bitmap): OCRResult {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()

            val texts = mutableListOf<String>()
            val boxes = mutableListOf<Array<FloatArray>>()
            val scores = mutableListOf<Float>()

            for (block in result.textBlocks) {
                for (line in block.lines) {
                    texts.add(line.text)

                    // Convert bounding box to our format
                    val rect = line.boundingBox
                    if (rect != null) {
                        val box = arrayOf(
                            floatArrayOf(rect.left.toFloat(), rect.top.toFloat()),
                            floatArrayOf(rect.right.toFloat(), rect.top.toFloat()),
                            floatArrayOf(rect.right.toFloat(), rect.bottom.toFloat()),
                            floatArrayOf(rect.left.toFloat(), rect.bottom.toFloat())
                        )
                        boxes.add(box)
                        scores.add(line.confidence ?: 0.9f)
                    }
                }
            }

            Log.d(TAG, "ML Kit recognized ${texts.size} text lines")

            OCRResult(
                text = texts.joinToString("\n"),
                confidence = scores.average().toFloat(),
                boxes = boxes.toTypedArray(),
                texts = texts,
                scores = scores
            )

        } catch (e: Exception) {
            Log.e(TAG, "ML Kit OCR failed", e)
            OCRResult()
        }
    }

    override fun release() {
        recognizer.close()
    }
}
```

### 3. Add to OCREngineFactory

Update `OCREngine.kt`:

```kotlin
enum class EngineType {
    RAPID_OCR,
    PADDLE_OCR,
    ML_KIT  // Add this
}

fun create(context: Context, type: EngineType = EngineType.ML_KIT): OCREngine {
    return when (type) {
        EngineType.RAPID_OCR -> RapidOCREngine(context)
        EngineType.PADDLE_OCR -> PaddleOCREngine(context)
        EngineType.ML_KIT -> MLKitOCREngine(context)  // Add this
    }
}
```

### 4. Update ScannerManager

Change default in `ScannerManager.kt`:

```kotlin
class ScannerManager(
    private val context: Context,
    ocrEngineType: OCREngineFactory.EngineType = OCREngineFactory.EngineType.ML_KIT
)
```

### 5. Sync and Run

```bash
./gradlew clean build
./gradlew installDebug
```

## Comparison

| Feature | PaddleOCR ONNX | ML Kit |
|---------|----------------|--------|
| Accuracy | Good (when working) | Excellent |
| Speed | Medium | Fast |
| Setup | Complex | Easy |
| Model Size | 91.5 MB | Auto-managed |
| Maintenance | Manual | Google updates |
| Current Status | ❌ Broken | ✅ Works |

## Expected Results

ML Kit should recognize text from your currency screenshot with:
- 20-30 text lines detected
- 95%+ confidence
- Proper English text recognition
- Fast inference (~100-300ms)

## Advantages Over Current ONNX

1. **No preprocessing bugs** - ML Kit handles everything
2. **No CTC decoding issues** - Built-in text decoder
3. **No model corruption** - Google-maintained
4. **Better Android optimization** - Native TFLite
5. **Automatic improvements** - Google updates models

## Next Steps

After integration:
1. Test on your image
2. Compare results with expected text
3. If good → remove PaddleOCR ONNX
4. If not good enough → can try other options

## Alternative: Download Pre-trained TFLite Models

If you prefer custom models, check:

1. **TensorFlow Hub**: https://tfhub.dev/s?deployment-format=lite&q=text
2. **MediaPipe**: https://developers.google.com/mediapipe/solutions/vision/text_recognizer
3. **TF Model Garden**: https://github.com/tensorflow/models/tree/master/official/projects/ocr

But ML Kit is **recommended** for production use.
