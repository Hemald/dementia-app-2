package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SelectionCluster
import kotlinx.coroutines.flow.Flow

@Dao
interface SelectionClusterDao {
    @Query("SELECT * FROM selection_clusters WHERE session_id = :sessionId LIMIT 1")
    suspend fun getClusterBySessionId(sessionId: Long): SelectionCluster?

    @Query("SELECT * FROM selection_clusters WHERE cluster_id = :clusterId LIMIT 1")
    suspend fun getClusterById(clusterId: String): SelectionCluster?

    @Query("""
        UPDATE selection_clusters 
        SET user_note = :userNote, voice_note_uri = :voiceNoteUri, user_reflection = :userReflection
        WHERE cluster_id = :clusterId
    """)
    suspend fun updateClusterMemory(
        clusterId: String,
        userNote: String?,
        voiceNoteUri: String?,
        userReflection: String?
    )

    @Query("SELECT * FROM selection_clusters ORDER BY created_at DESC")
    fun getAllClusters(): Flow<List<SelectionCluster>>

    @Query("SELECT * FROM selection_clusters ORDER BY created_at DESC")
    suspend fun getAllClustersSync(): List<SelectionCluster>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cluster: SelectionCluster)

    @Query("DELETE FROM selection_clusters WHERE cluster_id = :clusterId")
    suspend fun deleteById(clusterId: String)

    @Query("DELETE FROM selection_clusters")
    suspend fun clear()
}
