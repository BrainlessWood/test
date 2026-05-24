package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey val id: String,
    val title: String,
    val folderId: String,
    val localPath: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val isCached: Boolean = false,
    val downloadProgress: Float = 0f,
    val dateAdded: Long = System.currentTimeMillis()
)
