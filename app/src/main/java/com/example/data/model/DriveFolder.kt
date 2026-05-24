package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drive_folders")
data class DriveFolder(
    @PrimaryKey val id: String,
    val name: String,
    val dateSelected: Long = System.currentTimeMillis()
)
