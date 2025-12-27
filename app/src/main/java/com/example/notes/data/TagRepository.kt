package com.example.notes.data

import io.objectbox.Box
import io.objectbox.kotlin.boxFor

class TagRepository {
    private val tagBox: Box<TagEntity> = ObjectBoxStore.store.boxFor()
    private val noteTagJoinBox: Box<NoteTagJoin> = ObjectBoxStore.store.boxFor()

    // Get all tags
    fun getAllTags(): List<TagEntity> {
        return tagBox.all.sortedByDescending { it.usageCount }
    }

    // Get tag by ID
    fun getTagById(id: Long): TagEntity? {
        return tagBox.get(id)
    }

    // Create or update tag
    fun saveTag(tag: TagEntity): Long {
        return tagBox.put(tag)
    }

    // Delete tag and all its associations
    fun deleteTag(tagId: Long) {
        // Remove all note-tag associations
        val joins = noteTagJoinBox.query(NoteTagJoin_.tagId.equal(tagId))
            .build()
            .find()
        noteTagJoinBox.remove(joins)

        // Remove the tag
        tagBox.remove(tagId)
    }

    // Get tags for a specific note
    fun getTagsForNote(noteId: Long): List<TagEntity> {
        val joins = noteTagJoinBox.query(NoteTagJoin_.noteId.equal(noteId))
            .build()
            .find()

        val tagIds = joins.map { it.tagId }
        return tagIds.mapNotNull { tagBox.get(it) }
    }

    // Get notes for a specific tag
    fun getNotesForTag(tagId: Long): List<Long> {
        val joins = noteTagJoinBox.query(NoteTagJoin_.tagId.equal(tagId))
            .build()
            .find()

        return joins.map { it.noteId }
    }

    // Add tag to note
    fun addTagToNote(noteId: Long, tagId: Long) {
        // Check if association already exists
        val existing = noteTagJoinBox.query(
            NoteTagJoin_.noteId.equal(noteId)
                .and(NoteTagJoin_.tagId.equal(tagId))
        ).build().findFirst()

        if (existing == null) {
            noteTagJoinBox.put(NoteTagJoin(noteId = noteId, tagId = tagId))

            // Update tag usage count
            val tag = getTagById(tagId)
            tag?.let {
                it.usageCount++
                saveTag(it)
            }
        }
    }

    // Remove tag from note
    fun removeTagFromNote(noteId: Long, tagId: Long) {
        val joins = noteTagJoinBox.query(
            NoteTagJoin_.noteId.equal(noteId)
                .and(NoteTagJoin_.tagId.equal(tagId))
        ).build().find()

        if (joins.isNotEmpty()) {
            noteTagJoinBox.remove(joins)

            // Update tag usage count
            val tag = getTagById(tagId)
            tag?.let {
                it.usageCount = maxOf(0, it.usageCount - 1)
                saveTag(it)
            }
        }
    }

    // Create tag if doesn't exist, return ID
    fun getOrCreateTag(name: String, color: Int): Long {
        val existing = tagBox.query(TagEntity_.name.equal(name))
            .build()
            .findFirst()

        return existing?.id ?: run {
            val tag = TagEntity(name = name, color = color)
            saveTag(tag)
        }
    }

    // Get popular tags (by usage count)
    fun getPopularTags(limit: Int = 10): List<TagEntity> {
        return getAllTags().take(limit)
    }

    // Search tags by name
    fun searchTags(query: String): List<TagEntity> {
        return tagBox.query(TagEntity_.name.contains(query))
            .build()
            .find()
            .sortedByDescending { it.usageCount }
    }

    // Helper to get a suggested color for a new tag
    fun getSuggestedColor(): Int {
        val colors = listOf(
            "#E57373",  // Red
            "#F06292",  // Pink
            "#BA68C8",  // Purple
            "#9575CD",  // Deep Purple
            "#7986CB",  // Indigo
            "#64B5F6",  // Blue
            "#4DD0E1",  // Cyan
            "#4DB6AC",  // Teal
            "#81C784",  // Green
            "#AED581",  // Light Green
            "#FFD54F",  // Amber
            "#FFB74D",  // Orange
            "#FF8A65",  // Deep Orange
            "#A1887F"   // Brown
        )
        val usedColors = getAllTags().map { it.color }.toSet()
        val availableColor = colors.find { colorString ->
            android.graphics.Color.parseColor(colorString) !in usedColors
        }
        return android.graphics.Color.parseColor(availableColor ?: colors.random())
    }
}
