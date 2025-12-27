package com.example.notes.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class NotebookEntity(
    @Id var id: Long = 0,
    var name: String = "",
    var color: Int = 0,  // Android color int
    var icon: String? = null,  // Material icon name or emoji
    var parentNotebookId: Long? = null,  // For 2-level hierarchy (null = root level)
    var displayOrder: Int = 0,  // Manual ordering
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var isDefault: Boolean = false  // True for "Scratchpad"
) {
    // Helper to check if this is a root-level notebook
    val isRootLevel: Boolean
        get() = parentNotebookId == null

    // Helper to check if this is a section (child notebook)
    val isSection: Boolean
        get() = parentNotebookId != null
}
