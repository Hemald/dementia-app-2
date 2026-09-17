package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.PhotoAssociation
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoAssociationDao {
    @Query("SELECT * FROM photo_associations WHERE cluster_id = :clusterId ORDER BY association_score DESC")
    suspend fun getAssociationsForCluster(clusterId: String): List<PhotoAssociation>

    @Query("SELECT * FROM photo_associations ORDER BY association_score DESC")
    fun getAllAssociations(): Flow<List<PhotoAssociation>>

    @Query("SELECT * FROM photo_associations ORDER BY association_score DESC")
    suspend fun getAllAssociationsSync(): List<PhotoAssociation>

    @Query("SELECT * FROM photo_associations WHERE user_confirmed = 1 ORDER BY created_at DESC")
    fun getConfirmedAssociations(): Flow<List<PhotoAssociation>>

    @Query("SELECT * FROM photo_associations WHERE user_confirmed = 1 ORDER BY created_at DESC")
    suspend fun getConfirmedAssociationsSync(): List<PhotoAssociation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(association: PhotoAssociation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(associations: List<PhotoAssociation>)

    @Query("SELECT * FROM photo_associations WHERE association_id = :associationId LIMIT 1")
    suspend fun getAssociationById(associationId: String): PhotoAssociation?

    @Query("""
        SELECT * FROM photo_associations 
        WHERE (photo_a_id = :photoAId AND photo_b_id = :photoBId)
           OR (photo_a_id = :photoBId AND photo_b_id = :photoAId)
        LIMIT 1
    """)
    suspend fun findAssociationBetweenPhotos(photoAId: String, photoBId: String): PhotoAssociation?

    @Query("""
        UPDATE photo_associations 
        SET user_confirmed = :confirmed, confirmed_at = :confirmedAt, source = :source, user_response = :userResponse
        WHERE association_id = :associationId
    """)
    suspend fun updateConfirmation(
        associationId: String,
        confirmed: Boolean,
        confirmedAt: Long?,
        source: String,
        userResponse: String?
    )

    @Query("""
        UPDATE photo_associations 
        SET user_note = :userNote, voice_note_uri = :voiceNoteUri 
        WHERE association_id = :associationId
    """)
    suspend fun updateMemoryNote(
        associationId: String,
        userNote: String?,
        voiceNoteUri: String?
    )

    @Query("DELETE FROM photo_associations WHERE association_id = :associationId")
    suspend fun deleteById(associationId: String)

    @Query("DELETE FROM photo_associations WHERE cluster_id = :clusterId")
    suspend fun deleteForCluster(clusterId: String)

    @Query("DELETE FROM photo_associations")
    suspend fun clear()
}
