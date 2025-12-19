# PP-OCRv4 Architecture & Pipeline

## Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Paper Notes App                          │
│                                                             │
│  ┌───────────────┐                                         │
│  │   Scanner     │  (ML Kit Document Scanner)              │
│  └───────┬───────┘                                         │
│          │                                                  │
│          ▼                                                  │
│  ┌──────────────────────────────────────────────┐         │
│  │        Image Preprocessing                   │         │
│  │  • Grayscale conversion                      │         │
│  │  • Binary thresholding (Otsu)                │         │
│  │  • Noise reduction                           │         │
│  │  • Contrast adjustment                       │         │
│  └──────────────┬───────────────────────────────┘         │
│                 │                                          │
│                 ▼                                          │
│  ┌──────────────────────────────────────────────┐         │
│  │        PP-OCRv4 Pipeline                     │         │
│  │                                              │         │
│  │  Step 1: Detection (DB)                     │         │
│  │  ┌──────────────────────┐                   │         │
│  │  │  ppocr_det_v4.nb     │                   │         │
│  │  │  • Locates text boxes │                   │         │
│  │  │  • 960x960 input      │                   │         │
│  │  └──────┬───────────────┘                   │         │
│  │         │                                    │         │
│  │         ▼                                    │         │
│  │  Step 2: Classification                     │         │
│  │  ┌──────────────────────┐                   │         │
│  │  │  ppocr_cls.nb        │                   │         │
│  │  │  • Detects rotation   │                   │         │
│  │  │  • 0°, 90°, 180°, 270°│                   │         │
│  │  └──────┬───────────────┘                   │         │
│  │         │                                    │         │
│  │         ▼                                    │         │
│  │  Step 3: Recognition (CRNN)                 │         │
│  │  ┌──────────────────────┐                   │         │
│  │  │  ppocr_rec_v4.nb     │                   │         │
│  │  │  • Recognizes chars   │                   │         │
│  │  │  • CTC decoding       │                   │         │
│  │  │  • 48x320 input       │                   │         │
│  │  └──────┬───────────────┘                   │         │
│  │         │                                    │         │
│  └─────────┼────────────────────────────────────┘         │
│            │                                              │
│            ▼                                              │
│  ┌──────────────────────────────────────────────┐         │
│  │        OCR Result                            │         │
│  │  • Recognized text                           │         │
│  │  • Confidence scores                         │         │
│  │  • Bounding boxes                            │         │
│  └──────────────┬───────────────────────────────┘         │
│                 │                                          │
│                 ▼                                          │
│  ┌──────────────────────────────────────────────┐         │
│  │        Text Embedding                        │         │
│  │  (all-MiniLM-L6-v2)                          │         │
│  └──────────────┬───────────────────────────────┘         │
│                 │                                          │
│                 ▼                                          │
│  ┌──────────────────────────────────────────────┐         │
│  │        ObjectBox Storage                     │         │
│  │  • Text content                              │         │
│  │  • Text embedding (384D vector)              │         │
│  │  • Image path                                │         │
│  │  • Metadata                                  │         │
│  └──────────────────────────────────────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

## Data Flow

### 1. Input Processing

```
Raw Image (from scanner)
    ↓
Resize to optimal dimensions (if needed)
    ↓
Convert to Bitmap (ARGB_8888)
    ↓
ImagePreprocessor
```

### 2. Preprocessing Pipeline

```
Original Bitmap
    ↓
┌─────────────────────┐
│  Grayscale          │  R*0.299 + G*0.587 + B*0.114
└──────────┬──────────┘
           ↓
┌─────────────────────┐
│  Binarization       │  Otsu's threshold or fixed
└──────────┬──────────┘
           ↓
┌─────────────────────┐
│  Noise Reduction    │  Median filter (optional)
└──────────┬──────────┘
           ↓
┌─────────────────────┐
│  Deskew             │  Rotation correction (optional)
└──────────┬──────────┘
           ↓
Preprocessed Bitmap
```

### 3. PP-OCRv4 Inference

```
Preprocessed Image
    ↓
┌──────────────────────────────────────┐
│  Text Detection (DB Network)         │
│  Input: [1, 3, H, W] (CHW format)   │
│  Output: Binary mask of text regions │
└──────────────┬───────────────────────┘
               ↓
    Extract bounding boxes
    (Find contours in mask)
               ↓
    ┌────────────────────────┐
    │  For each text box:    │
    │                        │
    │  1. Crop region        │
    │  2. Classify angle     │
    │  3. Rotate if needed   │
    │  4. Recognize text     │
    └────────┬───────────────┘
             ↓
┌──────────────────────────────────────┐
│  Text Recognition (CRNN)             │
│  Input: [1, 3, 48, W]                │
│  Output: Character probabilities     │
│  Decode: CTC decoding                │
└──────────────┬───────────────────────┘
               ↓
    Combine all text boxes
    (Sort by position)
               ↓
    Final OCR Result
```

### 4. Model Details

#### Detection Model (ppocr_det_v4.nb)
- **Architecture**: DB (Differentiable Binarization)
- **Backbone**: MobileNetV3
- **Input**: 960x960 (padded to 32x)
- **Output**: Binary segmentation map
- **Size**: ~3.5 MB
- **Task**: Find where text is located

#### Classification Model (ppocr_cls.nb)
- **Architecture**: Simple CNN
- **Input**: 48x192
- **Output**: Rotation angle (0°, 180°)
- **Size**: ~2 MB
- **Task**: Detect if text is upside-down

#### Recognition Model (ppocr_rec_v4.nb)
- **Architecture**: CRNN (CNN + RNN + CTC)
- **Backbone**: MobileNetV3 + LSTM
- **Input**: 48x320 (variable width)
- **Output**: Character sequence
- **Size**: ~10 MB
- **Task**: Convert image to text
- **Dictionary**: 5,000+ characters (English + symbols)

## Optimization Techniques

### 1. Model Quantization
```
Original FP32 Model (50 MB)
    ↓
Quantization (INT8)
    ↓
Optimized Model (16 MB)
```
- 3x smaller size
- 2-3x faster inference
- Minimal accuracy loss (<1%)

### 2. Mobile Optimizations
- **NEON**: ARM SIMD instructions
- **Winograd**: Fast convolution algorithm
- **Channel Pruning**: Remove redundant filters
- **Knowledge Distillation**: Teacher-student training

### 3. Runtime Optimizations
```kotlin
// Thread configuration
config.setThreads(4)  // Use 4 cores

// Power mode
config.setPowerMode(PowerMode.LITE_POWER_HIGH)

// Model caching
// Models loaded once at startup
```

## Memory Layout

### Input Tensor Format
```
Shape: [batch, channels, height, width]
Example: [1, 3, 960, 960]

Data layout (CHW):
┌─────────────────────────────────┐
│ Red channel (H×W floats)        │
├─────────────────────────────────┤
│ Green channel (H×W floats)      │
├─────────────────────────────────┤
│ Blue channel (H×W floats)       │
└─────────────────────────────────┘

Normalization:
value = (pixel/255 - mean) / std
mean = [0.485, 0.456, 0.406]
std = [0.229, 0.224, 0.225]
```

### Output Tensor Format

#### Detection Output
```
Shape: [1, 1, H/4, W/4]
Values: 0.0 to 1.0 (probability)
Threshold: 0.3 (configurable)
```

#### Recognition Output
```
Shape: [1, T, C]
T = time steps (width/8)
C = num_classes (5000+)

Per timestep:
[prob_blank, prob_'a', prob_'b', ..., prob_'z', ...]
```

## Performance Profiling

### Typical Inference Breakdown (1080p image)
```
Total: 400ms
├─ Image preprocessing:     50ms  (12%)
├─ Detection model:        180ms  (45%)
├─ Classification model:    20ms  (5%)
├─ Recognition model:      130ms  (33%)
└─ Post-processing:         20ms  (5%)
```

### Memory Usage
```
Model loading:      ~80 MB
Inference buffers:  ~50 MB
Input/output:       ~20 MB
─────────────────────────
Total:             ~150 MB
```

## Integration Points

### ScannerManager.kt
```kotlin
// Old: ML Kit OCR
val recognizer = TextRecognition.getClient()
val result = recognizer.process(image)

// New: PP-OCRv4
val ocrHelper = PPOCRv4Helper(context)
val preprocessed = ImagePreprocessor.preprocessForOCR(bitmap)
val result = ocrHelper.recognizeText(preprocessed)
```

### Key Changes
1. **Preprocessing**: Added before OCR
2. **API**: Different result format
3. **Lifecycle**: Manual resource management
4. **Performance**: More accurate, slightly slower

## Error Handling

```
Try PP-OCRv4
    ↓
Success? ──No──> Log error + Use empty text
    │
   Yes
    ↓
Check confidence
    ↓
> 0.5? ──No──> Log warning + Use result
    │
   Yes
    ↓
Use result with high confidence
```

## Testing Strategy

1. **Unit Tests**: Individual components
   - Image preprocessing
   - Tensor conversions
   - CTC decoding

2. **Integration Tests**: Full pipeline
   - Load models
   - Process test images
   - Verify results

3. **Performance Tests**: Speed & memory
   - Measure inference time
   - Monitor memory usage
   - Profile on target devices

4. **Accuracy Tests**: Real-world data
   - Handwritten samples
   - Different paper types
   - Various lighting conditions
