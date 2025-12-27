package com.example.notes.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class NoteTagJoin(
    @Id var id: Long = 0,
    var noteId: Long = 0,
    var tagId: Long = 0,
    var createdAt: Long = System.currentTimeMillis()
)
