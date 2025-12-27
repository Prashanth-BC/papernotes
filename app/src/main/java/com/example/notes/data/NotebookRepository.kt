package com.example.notes.data

import io.objectbox.Box
import io.objectbox.kotlin.boxFor

class NotebookRepository {
    private val notebookBox: Box<NotebookEntity> = ObjectBoxStore.store.boxFor()
    private val noteBox: Box<NoteEntity> = ObjectBoxStore.store.boxFor()

    // Get all notebooks
    fun getAllNotebooks(): List<NotebookEntity> {
        return notebookBox.all.sortedWith(
            compareBy<NotebookEntity> { !it.isDefault }  // Default first
                .thenBy { it.parentNotebookId ?: -1 }     // Root notebooks first
                .thenBy { it.displayOrder }                // Then by order
                .thenBy { it.name }                        // Then alphabetically
        )
    }

    // Get root-level notebooks only
    fun getRootNotebooks(): List<NotebookEntity> {
        return notebookBox.query(NotebookEntity_.parentNotebookId.isNull())
            .build()
            .find()
            .sortedWith(
                compareBy<NotebookEntity> { !it.isDefault }
                    .thenBy { it.displayOrder }
                    .thenBy { it.name }
            )
    }

    // Get sections (child notebooks) for a parent
    fun getSections(parentId: Long): List<NotebookEntity> {
        return notebookBox.query(NotebookEntity_.parentNotebookId.equal(parentId))
            .build()
            .find()
            .sortedWith(
                compareBy<NotebookEntity> { it.displayOrder }
                    .thenBy { it.name }
            )
    }

    // Get notebook by ID
    fun getNotebookById(id: Long): NotebookEntity? {
        return notebookBox.get(id)
    }

    // Create or update notebook
    fun saveNotebook(notebook: NotebookEntity): Long {
        notebook.updatedAt = System.currentTimeMillis()
        return notebookBox.put(notebook)
    }

    // Delete notebook (and optionally its sections and notes)
    fun deleteNotebook(notebookId: Long, deleteNotes: Boolean = false) {
        val notebook = getNotebookById(notebookId) ?: return

        // Delete all sections if this is a root notebook
        if (notebook.isRootLevel) {
            val sections = getSections(notebookId)
            sections.forEach { section ->
                deleteNotebook(section.id, deleteNotes)
            }
        }

        // Handle notes in this notebook
        if (deleteNotes) {
            // Delete all notes in this notebook
            val notes = noteBox.query(NoteEntity_.notebookId.equal(notebookId))
                .build()
                .find()
            noteBox.remove(notes)
        } else {
            // Move notes to parent notebook or null
            val targetNotebookId = notebook.parentNotebookId
            val notes = noteBox.query(NoteEntity_.notebookId.equal(notebookId))
                .build()
                .find()
            notes.forEach { note ->
                note.notebookId = targetNotebookId
            }
            noteBox.put(notes)
        }

        // Delete the notebook
        notebookBox.remove(notebookId)
    }

    // Get note count for a notebook
    fun getNoteCount(notebookId: Long): Long {
        return noteBox.query(NoteEntity_.notebookId.equal(notebookId))
            .build()
            .count()
    }

    // Create default "Scratchpad" notebook if it doesn't exist
    fun ensureDefaultNotebook(): NotebookEntity {
        val existing = notebookBox.query(NotebookEntity_.isDefault.equal(true))
            .build()
            .findFirst()

        return existing ?: run {
            val scratchpad = NotebookEntity(
                name = "Scratchpad",
                color = android.graphics.Color.parseColor("#FFB74D"),  // Warm orange
                icon = "✏️",
                isDefault = true,
                displayOrder = 0
            )
            scratchpad.id = saveNotebook(scratchpad)
            scratchpad
        }
    }

    // Migrate from old collection strings to notebooks
    fun migrateCollectionsToNotebooks() {
        // Get all unique collection names from notes
        val collections = noteBox.all
            .mapNotNull { it.collection }
            .distinct()

        if (collections.isEmpty()) return

        // Create notebooks for each collection
        collections.forEachIndexed { index, collectionName ->
            val color = getColorForIndex(index)
            val notebook = NotebookEntity(
                name = collectionName,
                color = color,
                icon = if (collectionName == "Scratchpad") "✏️" else "📔",
                displayOrder = if (collectionName == "Scratchpad") 0 else index + 1,
                isDefault = collectionName == "Scratchpad"
            )
            val notebookId = saveNotebook(notebook)

            // Update notes with this collection to use the new notebook ID
            val notes = noteBox.query(NoteEntity_.collection.equal(collectionName))
                .build()
                .find()
            notes.forEach { note ->
                note.notebookId = notebookId
                // Keep collection for now for backwards compatibility
            }
            noteBox.put(notes)
        }
    }

    // Helper to get a color for a notebook based on index
    private fun getColorForIndex(index: Int): Int {
        val colors = listOf(
            "#FFB74D",  // Orange
            "#64B5F6",  // Blue
            "#81C784",  // Green
            "#E57373",  // Red
            "#BA68C8",  // Purple
            "#4DD0E1",  // Cyan
            "#FFF176",  // Yellow
            "#F06292",  // Pink
            "#A1887F",  // Brown
            "#90A4AE"   // Gray
        )
        return android.graphics.Color.parseColor(colors[index % colors.size])
    }
}
