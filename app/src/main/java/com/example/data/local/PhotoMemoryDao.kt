package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PhotoMemory
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoMemoryDao {
    @Query("""
        SELECT * FROM photo_memories 
        WHERE person_id = :personId 
        ORDER BY display_order ASC, 
        CASE WHEN taken_timestamp IS NOT NULL THEN taken_timestamp ELSE added_timestamp END DESC
    """)
    fun getPhotosForPerson(personId: String): Flow<List<PhotoMemory>>

    @Query("""
        SELECT * FROM photo_memories 
        WHERE person_id = :personId 
        ORDER BY display_order ASC, 
        CASE WHEN taken_timestamp IS NOT NULL THEN taken_timestamp ELSE added_timestamp END DESC
    """)
    suspend fun getPhotosForPersonSync(personId: String): List<PhotoMemory>

    @Query("SELECT * FROM photo_memories WHERE photo_id = :photoId LIMIT 1")
    suspend fun getPhotoById(photoId: String): PhotoMemory?

    @Query("SELECT * FROM photo_memories ORDER BY added_timestamp DESC")
    fun getAllPhotos(): Flow<List<PhotoMemory>>

    @Query("SELECT * FROM photo_memories ORDER BY added_timestamp DESC")
    suspend fun getAllPhotosSync(): List<PhotoMemory>

    @Query("SELECT COUNT(*) FROM photo_memories WHERE person_id = :personId AND content_hash = :contentHash")
    suspend fun countDuplicate(personId: String, contentHash: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoMemory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<PhotoMemory>)

    @Update
    suspend fun update(photo: PhotoMemory)

    @Delete
    suspend fun delete(photo: PhotoMemory)

    @Query("DELETE FROM photo_memories WHERE photo_id = :photoId")
    suspend fun deleteById(photoId: String)

    @Query("DELETE FROM photo_memories WHERE person_id = :personId")
    suspend fun deleteAllForPerson(personId: String)

    @Query("DELETE FROM photo_memories")
    suspend fun clear()
}
