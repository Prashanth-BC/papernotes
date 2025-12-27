package com.example.notes.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.VectorDistanceType

@Entity
data class NoteEntity(
    @Id var id: Long = 0,
    var title: String = "",
    var imagePath: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    
    // OCR Text content from ML Kit
    var mlKitText: String? = null,
    
    // OCR Text content from ColorBased OCR
    var colorBasedText: String? = null,
    
    // Legacy field for backward compatibility (combined text)
    var ocrText: String? = null,

    // 1280 dimensions for Image (MobileNetV3 Large - actual output verified)
    @HnswIndex(dimensions = 1280, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray? = null,

    // 512 dimensions for Image (CLIP ViT-B/32 - robust to variations)
    // CLIP is much more robust than MobileNetV3 for matching similar images with:
    // - Lighting/brightness/contrast changes
    // - Cropping (up to 30-40%)
    // - Rotation (up to 45°)
    @HnswIndex(dimensions = 512, distanceType = VectorDistanceType.COSINE)
    var clipEmbedding: FloatArray? = null,

    // 384 dimensions for Text from ML Kit OCR
    // Uses same all-MiniLM-L6-v2 model instance as colorBasedTextEmbedding
    @HnswIndex(dimensions = 384, distanceType = VectorDistanceType.COSINE)
    var mlKitTextEmbedding: FloatArray? = null,
    
    // 384 dimensions for Text from ColorBased OCR
    // Uses same all-MiniLM-L6-v2 model instance as mlKitTextEmbedding
    @HnswIndex(dimensions = 384, distanceType = VectorDistanceType.COSINE)
    var colorBasedTextEmbedding: FloatArray? = null,
    
    // Legacy field for backward compatibility
    @HnswIndex(dimensions = 384, distanceType = VectorDistanceType.COSINE)
    var textEmbedding: FloatArray? = null,

    // 768 dimensions for TrOCR Visual Encoder (BEiT/DeiT - OCR-specific image understanding)
    @HnswIndex(dimensions = 768, distanceType = VectorDistanceType.COSINE)
    var trocrEmbedding: FloatArray? = null,

    var collection: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NoteEntity

        if (id != other.id) return false
        if (title != other.title) return false
        if (imagePath != other.imagePath) return false
        if (timestamp != other.timestamp) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false
        if (collection != other.collection) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + imagePath.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + (collection?.hashCode() ?: 0)
        return result
    }
}
