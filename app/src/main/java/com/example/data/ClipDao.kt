package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {
    @Query("SELECT * FROM saved_clips ORDER BY timestamp DESC")
    fun getAllClips(): Flow<List<SavedClipEntity>>

    @Query("SELECT * FROM saved_clips WHERE id = :clipId LIMIT 1")
    suspend fun getClipById(clipId: Long): SavedClipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: SavedClipEntity): Long

    @Delete
    suspend fun deleteClip(clip: SavedClipEntity)

    @Query("DELETE FROM saved_clips WHERE id = :clipId")
    suspend fun deleteById(clipId: Long)

    @Query("DELETE FROM saved_clips")
    suspend fun clearAll()
}
