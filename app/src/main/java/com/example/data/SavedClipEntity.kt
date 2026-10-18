package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_clips")
data class SavedClipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val characterCount: Int = text.length,
    val wordCount: Int = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size,
    val sourceNote: String = "Smart OCR Capture"
)
