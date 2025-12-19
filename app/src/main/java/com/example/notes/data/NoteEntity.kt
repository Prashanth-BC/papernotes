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
    
    // OCR Text content
    var ocrText: String? = null,

    // 1024 dimensions for Image (MobileNetV3 Small/Large - verifying actual output)
    @HnswIndex(dimensions = 1024, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray? = null,

    // 384 dimensions for Text (all-MiniLM-L6-V2)
    @HnswIndex(dimensions = 384, distanceType = VectorDistanceType.COSINE)
    var textEmbedding: FloatArray? = null,

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
