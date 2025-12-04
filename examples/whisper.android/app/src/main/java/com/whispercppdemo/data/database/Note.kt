package com.whispercppdemo.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transcribedText: String,
    val timestamp: Long,
    val audioFilePath: String? = null,
    val duration: Long? = null,  // Duration in milliseconds
    val embedding: String? = null  // JSON array of float values for semantic search
)
