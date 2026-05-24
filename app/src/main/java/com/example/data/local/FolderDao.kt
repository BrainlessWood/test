package com.example.data.local

import androidx.room.*
import com.example.data.model.DriveFolder
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM drive_folders ORDER BY dateSelected DESC")
    fun getAllFolders(): Flow<List<DriveFolder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: DriveFolder)

    @Delete
    suspend fun deleteFolder(folder: DriveFolder)
}
