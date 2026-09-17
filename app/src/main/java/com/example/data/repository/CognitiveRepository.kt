package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.model.HydrationRecord
import com.example.data.model.Person
import com.example.data.model.PersonalBaseline
import com.example.data.model.PhotoAssociation
import com.example.data.model.PhotoMemory
import com.example.data.model.SelectionCluster
import com.example.data.model.User
import com.example.data.model.UserPreferences
import com.example.data.model.UserProfile
import com.example.data.model.UserSession
import com.example.processor.PersonalCognitiveState
import kotlinx.coroutines.flow.Flow

interface CognitiveRepository {
    val userProfile: Flow<UserProfile?>
    val userPreferences: Flow<UserPreferences>
    val personalBaseline: Flow<PersonalBaseline?>
    val sessions: Flow<List<UserSession>>
    val personalCognitiveState: Flow<PersonalCognitiveState>
    val allPeople: Flow<List<Person>>
    val allClusters: Flow<List<com.example.data.model.SelectionCluster>>
    val allAssociations: Flow<List<com.example.data.model.PhotoAssociation>>
    val confirmedAssociations: Flow<List<com.example.data.model.PhotoAssociation>>

    fun getTodayHydrationRecords(): Flow<List<HydrationRecord>>
    fun getAllHydrationRecords(): Flow<List<HydrationRecord>>

    fun getPhotosForPerson(personId: String): Flow<List<PhotoMemory>>
    fun getAllPhotos(): Flow<List<PhotoMemory>>
    suspend fun getAllPhotosSync(): List<PhotoMemory>
    suspend fun getPhotosForPersonSync(personId: String): List<PhotoMemory>
    suspend fun importPhotosForPerson(context: Context, personId: String, uris: List<Uri>): Pair<Int, Int>
    suspend fun deletePhotoMemory(photo: PhotoMemory)
    suspend fun setCoverPhotoFromMemory(person: Person, photo: PhotoMemory)

    suspend fun saveFamilyMemoryExperimentSession(
        startedAt: Long,
        completedAt: Long,
        trials: List<com.example.data.model.FamilyRecognitionTrial>,
        metrics: com.example.experiment.family.FamilyMemorySessionMetrics,
        peopleMap: Map<String, Person>
    ): Long

    suspend fun getHistoricalClusters(): List<com.example.data.model.SelectionCluster>
    suspend fun saveExplorationCluster(
        directoryPersonId: String,
        selections: List<com.example.experiment.family.ExplorationSelection>,
        pairwiseResults: List<com.example.experiment.family.PairwiseAssociationResult>,
        userResponse: String?,
        isSavedConnection: Boolean
    ): String

    suspend fun confirmAssociation(associationId: String)
    suspend fun rejectAssociation(associationId: String)
    suspend fun removeAssociation(associationId: String)
    suspend fun createManualConnection(
        photoA: PhotoMemory,
        photoB: PhotoMemory,
        personA: Person,
        personB: Person,
        userNote: String? = null
    ): PhotoAssociation
    suspend fun updateAssociationMemoryNote(associationId: String, textNote: String?, voiceNoteUri: String?)
    suspend fun updateClusterMemory(clusterId: String, textNote: String?, voiceNoteUri: String?, userReflection: String?)
    suspend fun deleteCluster(clusterId: String)

    suspend fun saveUserProfile(name: String, dateOfBirth: String)
    suspend fun updatePreferences(preferences: UserPreferences)
    suspend fun setDarkMode(isDark: Boolean)
    suspend fun setReminderIntervalHours(intervalHours: Int)

    suspend fun recordHydrationEvent(timestamp: Long = System.currentTimeMillis()): Long
    suspend fun recordMissedReminderEvent(timestamp: Long): Long

    suspend fun checkAndProcessMissedReminders(currentTimestamp: Long = System.currentTimeMillis())
    suspend fun getFullUserAggregate(): User

    suspend fun recordSession(
        sessionType: String,
        startedAt: Long,
        completedAt: Long,
        cyclesCompleted: Int,
        isCompleted: Boolean,
        metadata: String = ""
    ): Long

    suspend fun recalculateCognitiveState(): PersonalCognitiveState

    suspend fun insertPerson(person: Person)
    suspend fun updatePerson(person: Person)
    suspend fun deletePerson(person: Person)
    suspend fun getPersonById(personId: String): Person?

    suspend fun resetAllData()
}
