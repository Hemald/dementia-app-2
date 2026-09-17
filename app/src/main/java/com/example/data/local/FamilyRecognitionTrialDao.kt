package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.FamilyRecognitionTrial
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyRecognitionTrialDao {
    @Query("SELECT * FROM family_recognition_trials WHERE session_id = :sessionId ORDER BY trial_started_at ASC")
    fun getTrialsForSession(sessionId: Long): Flow<List<FamilyRecognitionTrial>>

    @Query("SELECT * FROM family_recognition_trials WHERE session_id = :sessionId ORDER BY trial_started_at ASC")
    suspend fun getTrialsForSessionSync(sessionId: Long): List<FamilyRecognitionTrial>

    @Query("SELECT * FROM family_recognition_trials WHERE person_id = :personId ORDER BY trial_started_at DESC")
    suspend fun getTrialsForPersonSync(personId: String): List<FamilyRecognitionTrial>

    @Query("SELECT * FROM family_recognition_trials ORDER BY trial_started_at DESC")
    suspend fun getAllTrialsSync(): List<FamilyRecognitionTrial>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trial: FamilyRecognitionTrial)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trials: List<FamilyRecognitionTrial>)

    @Query("DELETE FROM family_recognition_trials WHERE session_id = :sessionId")
    suspend fun deleteTrialsForSession(sessionId: Long)

    @Query("DELETE FROM family_recognition_trials")
    suspend fun clear()
}
