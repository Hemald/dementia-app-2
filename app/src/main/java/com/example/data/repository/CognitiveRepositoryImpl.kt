package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.model.FamilyRecognitionTrial
import com.example.data.model.HydrationRecord
import com.example.data.model.Person
import com.example.data.model.PersonalBaseline
import com.example.data.model.PersonalCognitiveStateRecord
import com.example.data.model.PhotoAssociation
import com.example.data.model.PhotoMemory
import com.example.data.model.SelectionCluster
import com.example.data.model.User
import com.example.data.model.UserPreferences
import com.example.data.model.UserProfile
import com.example.data.model.UserSession
import com.example.experiment.family.FamilyMemoryExperimentEngine
import com.example.experiment.family.FamilyMemorySessionMetrics
import com.example.processor.PersonalCognitiveState
import com.example.processor.PersonalCognitiveStateProcessor
import com.example.util.AudioMemoryHelper
import com.example.util.PhotoStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class CognitiveRepositoryImpl(
    private val database: AppDatabase
) : CognitiveRepository {

    private val profileDao = database.userProfileDao()
    private val baselineDao = database.personalBaselineDao()
    private val stateDao = database.personalCognitiveStateDao()
    private val hydrationDao = database.hydrationDao()
    private val sessionDao = database.userSessionDao()
    private val preferencesDao = database.userPreferencesDao()
    private val personDao = database.personDao()
    private val photoMemoryDao = database.photoMemoryDao()
    private val familyTrialDao = database.familyRecognitionTrialDao()
    private val selectionClusterDao = database.selectionClusterDao()
    private val photoAssociationDao = database.photoAssociationDao()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override val userProfile: Flow<UserProfile?> = profileDao.getUserProfile().distinctUntilChanged()

    override val userPreferences: Flow<UserPreferences> = preferencesDao.getPreferences()
        .map { it ?: UserPreferences() }
        .distinctUntilChanged()

    override val personalBaseline: Flow<PersonalBaseline?> = baselineDao.getBaseline().distinctUntilChanged()

    override val sessions: Flow<List<UserSession>> = sessionDao.getAllSessions()

    override val allPeople: Flow<List<Person>> = personDao.getAllPeople()

    override val allClusters: Flow<List<com.example.data.model.SelectionCluster>> = selectionClusterDao.getAllClusters()

    override val allAssociations: Flow<List<com.example.data.model.PhotoAssociation>> = photoAssociationDao.getAllAssociations()

    override val confirmedAssociations: Flow<List<com.example.data.model.PhotoAssociation>> = photoAssociationDao.getConfirmedAssociations()

    override val personalCognitiveState: Flow<PersonalCognitiveState> = combine(
        sessions,
        personalBaseline,
        hydrationDao.getAllRecords()
    ) { allSessions, baseline, hydrationList ->
        val (state, updatedBaseline) = PersonalCognitiveStateProcessor.processCognitiveState(
            allSessions = allSessions,
            existingBaseline = baseline,
            hydrationRecords = hydrationList
        )
        state
    }.distinctUntilChanged()

    private fun getTodayDateString(timestamp: Long = System.currentTimeMillis()): String {
        return dateFormat.format(Date(timestamp))
    }

    override fun getTodayHydrationRecords(): Flow<List<HydrationRecord>> {
        val today = getTodayDateString()
        return hydrationDao.getRecordsForDate(today)
    }

    override fun getAllHydrationRecords(): Flow<List<HydrationRecord>> {
        return hydrationDao.getAllRecords()
    }

    override suspend fun saveUserProfile(name: String, dateOfBirth: String) {
        val existing = profileDao.getUserProfileSync()
        val profile = UserProfile(
            id = 1,
            name = name.trim(),
            dateOfBirth = dateOfBirth.trim(),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        profileDao.insertOrUpdate(profile)

        // Ensure baseline architecture record is initialized if not present
        // (NOTE: This remains internal data structure only and is never shown in UI)
        baselineDao.insertOrUpdate(
            PersonalBaseline(
                id = 1,
                established = false,
                createdAt = null
            )
        )
    }

    override suspend fun updatePreferences(preferences: UserPreferences) {
        preferencesDao.insertOrUpdate(preferences)
    }

    override suspend fun setDarkMode(isDark: Boolean) {
        val current = preferencesDao.getPreferencesSync() ?: UserPreferences()
        preferencesDao.insertOrUpdate(current.copy(isDarkMode = isDark))
    }

    override suspend fun setReminderIntervalHours(intervalHours: Int) {
        val current = preferencesDao.getPreferencesSync() ?: UserPreferences()
        preferencesDao.insertOrUpdate(current.copy(reminderIntervalHours = intervalHours.coerceIn(1, 6)))
    }

    override suspend fun recordHydrationEvent(timestamp: Long): Long {
        val dateString = getTodayDateString(timestamp)
        val record = HydrationRecord(
            timestamp = timestamp,
            dateString = dateString,
            type = HydrationRecord.TYPE_HYDRATION,
            status = HydrationRecord.STATUS_COMPLETED
        )
        return hydrationDao.insert(record)
    }

    override suspend fun recordMissedReminderEvent(timestamp: Long): Long {
        val dateString = getTodayDateString(timestamp)
        val record = HydrationRecord(
            timestamp = timestamp,
            dateString = dateString,
            type = HydrationRecord.TYPE_HYDRATION,
            status = HydrationRecord.STATUS_MISSED
        )
        return hydrationDao.insert(record)
    }

    /**
     * Inspects scheduled reminder slots for today. If an expected reminder time has elapsed
     * without any confirmed drinking event within the slot's active window, records it as 'missed'.
     * Guarantees that completed drinking and missed reminders remain distinguishable.
     */
    override suspend fun checkAndProcessMissedReminders(currentTimestamp: Long) {
        val prefs = preferencesDao.getPreferencesSync() ?: UserPreferences()
        val todayStr = getTodayDateString(currentTimestamp)
        val existingRecords = hydrationDao.getRecordsForDateSync(todayStr)

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTimestamp
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val startHour = prefs.reminderStartHour
        val endHour = prefs.reminderEndHour
        val interval = prefs.reminderIntervalHours.coerceAtLeast(1)

        val slotToleranceMs = 45 * 60 * 1000L // 45 minutes window

        for (hour in startHour..endHour step interval) {
            val slotCal = Calendar.getInstance()
            slotCal.timeInMillis = currentTimestamp
            slotCal.set(Calendar.HOUR_OF_DAY, hour)
            slotCal.set(Calendar.MINUTE, 0)
            slotCal.set(Calendar.SECOND, 0)
            slotCal.set(Calendar.MILLISECOND, 0)
            val slotTimestamp = slotCal.timeInMillis

            // Only check slots that are strictly in the past (past grace period)
            if (currentTimestamp > slotTimestamp + (30 * 60 * 1000L)) {
                // Check if any record (completed or missed) already covers this slot
                val hasRecordInSlot = existingRecords.any { record ->
                    Math.abs(record.timestamp - slotTimestamp) < slotToleranceMs
                }

                if (!hasRecordInSlot) {
                    val missedRecord = HydrationRecord(
                        timestamp = slotTimestamp,
                        dateString = todayStr,
                        type = HydrationRecord.TYPE_HYDRATION,
                        status = HydrationRecord.STATUS_MISSED
                    )
                    hydrationDao.insert(missedRecord)
                }
            }
        }
    }

    override suspend fun recordSession(
        sessionType: String,
        startedAt: Long,
        completedAt: Long,
        cyclesCompleted: Int,
        isCompleted: Boolean,
        metadata: String
    ): Long {
        val session = UserSession(
            sessionType = sessionType,
            startedAt = startedAt,
            completedAt = completedAt,
            cyclesCompleted = cyclesCompleted,
            isCompleted = isCompleted,
            metadata = metadata
        )
        val sessionId = sessionDao.insert(session)

        // After every completed session:
        // 1. Raw trial data is already saved.
        // 2. Calculate current-session metrics & compare with baseline & recent.
        // 3. Update personal cognitive state in database.
        try {
            val allSessions = sessionDao.getAllSessionsSync()
            val existingBaseline = baselineDao.getBaselineSync()
            val hydrationRecords = hydrationDao.getAllRecordsSync()

            val (state, updatedBaseline) = PersonalCognitiveStateProcessor.processCognitiveState(
                allSessions = allSessions,
                existingBaseline = existingBaseline,
                hydrationRecords = hydrationRecords
            )

            // If baseline was newly established, persist it
            if (updatedBaseline.established && (existingBaseline == null || !existingBaseline.established)) {
                baselineDao.insertOrUpdate(updatedBaseline)
            }

            // Persist the updated state record
            stateDao.insertOrUpdate(
                PersonalCognitiveStateRecord(
                    id = 1,
                    updatedAt = System.currentTimeMillis(),
                    totalSessionsProcessed = state.totalCompletedSessions,
                    baselineEstablished = state.baselineEstablished,
                    stateJson = state.toGeminiContextJson()
                )
            )
        } catch (_: Exception) {
            // Processing should be robust and never fail the raw session save
        }

        return sessionId
    }

    override suspend fun recalculateCognitiveState(): PersonalCognitiveState {
        val allSessions = sessionDao.getAllSessionsSync()
        val existingBaseline = baselineDao.getBaselineSync()
        val hydrationRecords = hydrationDao.getAllRecordsSync()

        val (state, updatedBaseline) = PersonalCognitiveStateProcessor.processCognitiveState(
            allSessions = allSessions,
            existingBaseline = existingBaseline,
            hydrationRecords = hydrationRecords
        )

        if (updatedBaseline.established && (existingBaseline == null || !existingBaseline.established)) {
            baselineDao.insertOrUpdate(updatedBaseline)
        }

        stateDao.insertOrUpdate(
            PersonalCognitiveStateRecord(
                id = 1,
                updatedAt = System.currentTimeMillis(),
                totalSessionsProcessed = state.totalCompletedSessions,
                baselineEstablished = state.baselineEstablished,
                stateJson = state.toGeminiContextJson()
            )
        )

        return state
    }

    override suspend fun getFullUserAggregate(): User {
        val profile = profileDao.getUserProfileSync()
        val prefs = preferencesDao.getPreferencesSync() ?: UserPreferences()
        val todayRecords = hydrationDao.getRecordsForDateSync(getTodayDateString())
        return User(
            profile = profile,
            baseline = PersonalBaseline(established = false, createdAt = null),
            sessions = emptyList(),
            hydration = todayRecords,
            preferences = prefs
        )
    }

    override fun getPhotosForPerson(personId: String): Flow<List<PhotoMemory>> {
        return photoMemoryDao.getPhotosForPerson(personId)
    }

    override fun getAllPhotos(): Flow<List<PhotoMemory>> {
        return photoMemoryDao.getAllPhotos()
    }

    override suspend fun getAllPhotosSync(): List<PhotoMemory> = withContext(Dispatchers.IO) {
        photoMemoryDao.getAllPhotosSync()
    }

    override suspend fun getPhotosForPersonSync(personId: String): List<PhotoMemory> = withContext(Dispatchers.IO) {
        photoMemoryDao.getPhotosForPersonSync(personId)
    }

    override suspend fun saveFamilyMemoryExperimentSession(
        startedAt: Long,
        completedAt: Long,
        trials: List<FamilyRecognitionTrial>,
        metrics: FamilyMemorySessionMetrics,
        peopleMap: Map<String, Person>
    ): Long = withContext(Dispatchers.IO) {
        val selectedTrials = trials.filter { it.selected }
        val clusterId = if (selectedTrials.isNotEmpty()) UUID.randomUUID().toString() else null

        // 1. Build session metadata JSON
        val metadataJson = JSONObject().apply {
            put("type", "family_memory_recognition")
            put("total_trials", trials.size)
            put("completed_trials", trials.count { !it.skipped })
            if (clusterId != null) {
                put("selection_cluster_id", clusterId)
            }
            put("metrics", metrics.toJsonObject())
            val trialsArray = JSONArray()
            for (trial in trials) {
                trialsArray.put(JSONObject().apply {
                    put("photo_id", trial.photoId)
                    put("person_id", trial.personId)
                    put("response_type", trial.recognitionResponse)
                    put("response_time_ms", trial.responseTimeMs)
                    put("selected", trial.selected)
                    put("selection_order", trial.selectionOrder ?: JSONObject.NULL)
                })
            }
            put("trials", trialsArray)
        }.toString()

        // 2. Insert UserSession
        val userSession = UserSession(
            sessionType = UserSession.TYPE_FAMILY_MEMORY_RECOGNITION,
            startedAt = startedAt,
            completedAt = completedAt,
            cyclesCompleted = trials.size,
            isCompleted = true,
            metadata = metadataJson
        )
        val sessionId = sessionDao.insert(userSession)

        // 3. Save trials linked to sessionId
        val trialsWithSession = trials.map { it.copy(sessionId = sessionId) }
        familyTrialDao.insertAll(trialsWithSession)

        // 4. If selections were made, record SelectionCluster and candidate PhotoAssociations
        if (clusterId != null && selectedTrials.isNotEmpty()) {
            val photoIdsJson = JSONArray(selectedTrials.map { it.photoId }).toString()
            val timestampsJson = JSONArray(selectedTrials.map { it.selectionTimestamp ?: it.responseAt }).toString()
            val ordersJson = JSONArray(selectedTrials.map { it.selectionOrder ?: 0 }).toString()

            val cluster = SelectionCluster(
                clusterId = clusterId,
                sessionId = sessionId,
                createdAt = completedAt,
                selectedPhotoIds = photoIdsJson,
                selectionTimestamps = timestampsJson,
                selectionOrders = ordersJson
            )
            selectionClusterDao.insert(cluster)

            // Compute and store candidate associations
            val allPhotosMap = photoMemoryDao.getAllPhotosSync().associateBy { it.photoId }
            val candidateAssociations = FamilyMemoryExperimentEngine.computeCandidateAssociations(
                clusterId = clusterId,
                selectedTrials = selectedTrials,
                photosMap = allPhotosMap
            )
            if (candidateAssociations.isNotEmpty()) {
                photoAssociationDao.insertAll(candidateAssociations)
            }
        }

        // 5. Recalculate cognitive state
        recalculateCognitiveState()

        sessionId
    }

    override suspend fun getHistoricalClusters(): List<com.example.data.model.SelectionCluster> = withContext(Dispatchers.IO) {
        selectionClusterDao.getAllClustersSync()
    }

    override suspend fun saveExplorationCluster(
        directoryPersonId: String,
        selections: List<com.example.experiment.family.ExplorationSelection>,
        pairwiseResults: List<com.example.experiment.family.PairwiseAssociationResult>,
        userResponse: String?,
        isSavedConnection: Boolean
    ): String = withContext(Dispatchers.IO) {
        val clusterId = UUID.randomUUID().toString()
        val photoIdsJson = JSONArray(selections.map { it.photo.photoId }).toString()
        val timestampsJson = JSONArray(selections.map { it.selectedAt }).toString()
        val ordersJson = JSONArray(selections.map { it.selectionOrder }).toString()

        val cluster = com.example.data.model.SelectionCluster(
            clusterId = clusterId,
            sessionId = System.currentTimeMillis(),
            selectedPhotoIds = photoIdsJson,
            selectionTimestamps = timestampsJson,
            selectionOrders = ordersJson,
            createdAt = System.currentTimeMillis(),
            directoryPersonId = directoryPersonId,
            userResponse = userResponse,
            isSavedConnection = isSavedConnection
        )
        selectionClusterDao.insert(cluster)

        val associations = com.example.experiment.family.FamilyAssociationEngine.createPhotoAssociations(
            clusterId = clusterId,
            pairwiseResults = pairwiseResults,
            userConfirmed = isSavedConnection,
            userResponse = userResponse
        )
        if (associations.isNotEmpty()) {
            photoAssociationDao.insertAll(associations)
        }

        clusterId
    }

    override suspend fun confirmAssociation(associationId: String) = withContext(Dispatchers.IO) {
        photoAssociationDao.updateConfirmation(
            associationId = associationId,
            confirmed = true,
            confirmedAt = System.currentTimeMillis(),
            source = "user_confirmed",
            userResponse = "confirmed"
        )
    }

    override suspend fun rejectAssociation(associationId: String) = withContext(Dispatchers.IO) {
        photoAssociationDao.updateConfirmation(
            associationId = associationId,
            confirmed = false,
            confirmedAt = null,
            source = "candidate",
            userResponse = "rejected"
        )
    }

    override suspend fun removeAssociation(associationId: String) = withContext(Dispatchers.IO) {
        val existing = photoAssociationDao.getAssociationById(associationId)
        if (existing != null) {
            AudioMemoryHelper.deleteVoiceNote(existing.voiceNoteUri)
            photoAssociationDao.deleteById(associationId)
        }
    }

    override suspend fun createManualConnection(
        photoA: PhotoMemory,
        photoB: PhotoMemory,
        personA: Person,
        personB: Person,
        userNote: String?
    ): PhotoAssociation = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val clusterId = UUID.randomUUID().toString()
        val photoIdsJson = JSONArray().put(photoA.photoId).put(photoB.photoId).toString()
        val timestampsJson = JSONArray().put(now).put(now).toString()
        val ordersJson = JSONArray().put(1).put(2).toString()

        val cluster = SelectionCluster(
            clusterId = clusterId,
            sessionId = now,
            selectedPhotoIds = photoIdsJson,
            selectionTimestamps = timestampsJson,
            selectionOrders = ordersJson,
            createdAt = now,
            directoryPersonId = personA.personId,
            userResponse = "manual_connection",
            isSavedConnection = true,
            userNote = userNote
        )
        selectionClusterDao.insert(cluster)

        val evidenceJson = JSONObject().apply {
            put("reasons", JSONArray().put("Manually connected by you"))
            put("score", 1.0)
            put("source", "user_created")
        }.toString()

        val association = PhotoAssociation(
            associationId = UUID.randomUUID().toString(),
            clusterId = clusterId,
            photoAId = photoA.photoId,
            photoBId = photoB.photoId,
            personAId = personA.personId,
            personBId = personB.personId,
            evidenceFeatures = evidenceJson,
            associationScore = 1.0,
            algorithmVersion = "manual",
            createdAt = now,
            source = "user_created",
            userConfirmed = true,
            userResponse = "confirmed",
            confirmedAt = now,
            userNote = userNote
        )
        photoAssociationDao.insert(association)
        association
    }

    override suspend fun updateAssociationMemoryNote(
        associationId: String,
        textNote: String?,
        voiceNoteUri: String?
    ) = withContext(Dispatchers.IO) {
        photoAssociationDao.updateMemoryNote(associationId, textNote, voiceNoteUri)
    }

    override suspend fun updateClusterMemory(
        clusterId: String,
        textNote: String?,
        voiceNoteUri: String?,
        userReflection: String?
    ) = withContext(Dispatchers.IO) {
        selectionClusterDao.updateClusterMemory(clusterId, textNote, voiceNoteUri, userReflection)
    }

    override suspend fun deleteCluster(clusterId: String) = withContext(Dispatchers.IO) {
        val cluster = selectionClusterDao.getClusterById(clusterId)
        if (cluster != null) {
            AudioMemoryHelper.deleteVoiceNote(cluster.voiceNoteUri)
        }
        val associations = photoAssociationDao.getAssociationsForCluster(clusterId)
        for (a in associations) {
            AudioMemoryHelper.deleteVoiceNote(a.voiceNoteUri)
        }
        photoAssociationDao.deleteForCluster(clusterId)
        selectionClusterDao.deleteById(clusterId)
    }

    override suspend fun importPhotosForPerson(
        context: Context,
        personId: String,
        uris: List<Uri>
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var addedCount = 0
        var duplicateCount = 0
        val newMemories = mutableListOf<PhotoMemory>()

        for (uri in uris) {
            val importResult = PhotoStorageHelper.savePhotoMemoryFile(context, uri, personId)
            if (importResult != null) {
                // Reliable duplicate detection based on content hash for this specific person
                val isDup = photoMemoryDao.countDuplicate(personId, importResult.contentHash) > 0
                if (isDup) {
                    duplicateCount++
                    // Avoid disk waste for duplicate
                    PhotoStorageHelper.deletePhoto(importResult.localUri)
                } else {
                    val memory = PhotoMemory(
                        personId = personId,
                        localUri = importResult.localUri,
                        contentHash = importResult.contentHash,
                        takenTimestamp = importResult.takenTimestamp,
                        addedTimestamp = System.currentTimeMillis(),
                        source = "photo_picker",
                        optionalLocation = importResult.location,
                        displayOrder = 0,
                        width = importResult.width,
                        height = importResult.height
                    )
                    newMemories.add(memory)
                    addedCount++
                }
            }
        }

        if (newMemories.isNotEmpty()) {
            photoMemoryDao.insertAll(newMemories)
        }

        Pair(addedCount, duplicateCount)
    }

    override suspend fun deletePhotoMemory(photo: PhotoMemory) = withContext(Dispatchers.IO) {
        photoMemoryDao.delete(photo)
        // Check if person's cover photo references this URI before deleting local file
        val person = personDao.getPersonByIdSync(photo.personId)
        if (person?.coverPhotoUri != photo.localUri) {
            PhotoStorageHelper.deletePhoto(photo.localUri)
        }
    }

    override suspend fun setCoverPhotoFromMemory(person: Person, photo: PhotoMemory) = withContext(Dispatchers.IO) {
        val updated = person.copy(
            coverPhotoUri = photo.localUri,
            updatedAt = System.currentTimeMillis()
        )
        personDao.update(updated)
    }

    override suspend fun insertPerson(person: Person) {
        personDao.insert(person)
    }

    override suspend fun updatePerson(person: Person) {
        personDao.update(person)
    }

    override suspend fun deletePerson(person: Person) = withContext(Dispatchers.IO) {
        val photos = photoMemoryDao.getPhotosForPersonSync(person.personId)
        for (p in photos) {
            PhotoStorageHelper.deletePhoto(p.localUri)
        }
        PhotoStorageHelper.deletePhoto(person.coverPhotoUri)
        personDao.delete(person)
    }

    override suspend fun getPersonById(personId: String): Person? {
        return personDao.getPersonByIdSync(personId)
    }

    override suspend fun resetAllData() {
        profileDao.clear()
        baselineDao.clear()
        stateDao.clear()
        hydrationDao.clear()
        sessionDao.clear()
        preferencesDao.clear()
        photoMemoryDao.clear()
        personDao.clear()
        familyTrialDao.clear()
        selectionClusterDao.clear()
        photoAssociationDao.clear()
    }
}
