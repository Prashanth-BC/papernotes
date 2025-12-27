package com.example.notes.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class TagEntity(
    @Id var id: Long = 0,
    var name: String = "",
    var color: Int = 0,  // Android color int
    var createdAt: Long = System.currentTimeMillis(),
    var usageCount: Int = 0  // Track how many notes use this tag
)
