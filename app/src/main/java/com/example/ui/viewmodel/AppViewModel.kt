package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.HydrationRecord
import com.example.data.model.Person
import com.example.data.model.PhotoMemory
import com.example.data.model.UserProfile
import com.example.data.model.UserPreferences
import com.example.data.model.UserSession
import com.example.data.model.PhotoAssociation
import com.example.data.model.SelectionCluster
import com.example.experiment.family.CandidateConnection
import com.example.experiment.family.ExplorationSelection
import com.example.experiment.family.FamilyAssociationEngine
import com.example.experiment.family.PairwiseAssociationResult
import com.example.experiment.memory.MemoryExperimentSessionResult
import com.example.experiment.reaction.ReactionExperimentSessionResult
import com.example.data.repository.CognitiveRepository
import com.example.data.repository.CognitiveRepositoryImpl
import com.example.util.PhotoStorageHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AppDestination {
    SETUP,
    BREATHING,
    HOME,
    PRACTICE,
    PRACTICE_BREATHING,
    MEMORY_EXPERIMENT,
    REACTION_EXPERIMENT,
    PRACTICE_PLACEHOLDER,
    SETTINGS,
    CAREGIVER_DASHBOARD,
    FAMILY_MEMORY,
    FAMILY_MEMORY_BREATHING,
    FAMILY_MEMORY_EXPERIMENT,
    PERSON_DETAIL,
    ADD_PERSON,
    MEMORY_MAP
}

data class UiNotification(
    val message: String,
    val isError: Boolean = false
)

class AppViewModel(
    application: Application,
    private val repository: CognitiveRepository
) : AndroidViewModel(application) {

    val userProfile: StateFlow<UserProfile?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userPreferences: StateFlow<UserPreferences> = repository.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val todayRecords: StateFlow<List<HydrationRecord>> = repository.getTodayHydrationRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSessions: StateFlow<List<UserSession>> = repository.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cognitiveState: StateFlow<com.example.processor.PersonalCognitiveState> = repository.personalCognitiveState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.processor.PersonalCognitiveState())

    val allPeople: StateFlow<List<Person>> = repository.allPeople
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPhotos: StateFlow<List<PhotoMemory>> = repository.getAllPhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPerson = MutableStateFlow<Person?>(null)
    val selectedPerson: StateFlow<Person?> = _selectedPerson.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val personPhotos: StateFlow<List<PhotoMemory>> = _selectedPerson
        .flatMapLatest { person ->
            if (person != null) {
                repository.getPhotosForPerson(person.personId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allClusters: StateFlow<List<SelectionCluster>> = repository.allClusters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAssociations: StateFlow<List<PhotoAssociation>> = repository.allAssociations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val confirmedAssociations: StateFlow<List<PhotoAssociation>> = repository.confirmedAssociations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeMapInspectionAssociation = MutableStateFlow<PhotoAssociation?>(null)
    val activeMapInspectionAssociation: StateFlow<PhotoAssociation?> = _activeMapInspectionAssociation.asStateFlow()

    private val _activeMapInspectionCluster = MutableStateFlow<SelectionCluster?>(null)
    val activeMapInspectionCluster: StateFlow<SelectionCluster?> = _activeMapInspectionCluster.asStateFlow()

    private val _manualConnectSourcePhoto = MutableStateFlow<PhotoMemory?>(null)
    val manualConnectSourcePhoto: StateFlow<PhotoMemory?> = _manualConnectSourcePhoto.asStateFlow()

    private val _mapTimelineMode = MutableStateFlow(false) // false = Map, true = Timeline
    val mapTimelineMode: StateFlow<Boolean> = _mapTimelineMode.asStateFlow()

    private val _selectedExplorationPhotos = MutableStateFlow<List<ExplorationSelection>>(emptyList())
    val selectedExplorationPhotos: StateFlow<List<ExplorationSelection>> = _selectedExplorationPhotos.asStateFlow()

    private val _activeCandidateToInspect = MutableStateFlow<CandidateConnection?>(null)
    val activeCandidateToInspect: StateFlow<CandidateConnection?> = _activeCandidateToInspect.asStateFlow()

    private val _isAnalyzingConnections = MutableStateFlow(false)
    val isAnalyzingConnections: StateFlow<Boolean> = _isAnalyzingConnections.asStateFlow()

    private val _calculatedPairwiseResults = MutableStateFlow<List<PairwiseAssociationResult>>(emptyList())
    val calculatedPairwiseResults: StateFlow<List<PairwiseAssociationResult>> = _calculatedPairwiseResults.asStateFlow()

    // Deterministic candidate connections from other family members for current directory photos
    @OptIn(ExperimentalCoroutinesApi::class)
    val crossPersonCandidates: StateFlow<List<CandidateConnection>> = combine(
        _selectedPerson,
        personPhotos,
        allPhotos,
        allPeople,
        allClusters
    ) { person, photos, everyPhoto, people, clusters ->
        if (person == null || photos.isEmpty()) {
            emptyList()
        } else {
            val peopleMap = people.associateBy { it.personId }
            val otherPhotos = everyPhoto.filter { it.personId != person.personId }
            val candidates = mutableListOf<CandidateConnection>()
            for (photo in photos) {
                val matches = FamilyAssociationEngine.findCrossPersonCandidates(
                    targetPhoto = photo,
                    targetPerson = person,
                    allOtherPhotos = otherPhotos,
                    peopleMap = peopleMap,
                    historicalClusters = clusters,
                    minScoreThreshold = 0.20
                )
                candidates.addAll(matches)
            }
            candidates.distinctBy { "${it.targetPhoto.photoId}_${it.candidatePhoto.photoId}" }
                .sortedByDescending { it.associationScore }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _viewingPhotoIndex = MutableStateFlow<Int?>(null)
    val viewingPhotoIndex: StateFlow<Int?> = _viewingPhotoIndex.asStateFlow()

    private val _personToEdit = MutableStateFlow<Person?>(null)
    val personToEdit: StateFlow<Person?> = _personToEdit.asStateFlow()

    private val _currentScreen = MutableStateFlow(AppDestination.SETUP)
    val currentScreen: StateFlow<AppDestination> = _currentScreen.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _uiNotification = MutableStateFlow<UiNotification?>(null)
    val uiNotification: StateFlow<UiNotification?> = _uiNotification.asStateFlow()

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    init {
        viewModelScope.launch {
            // Check if user has already completed setup
            val profile = repository.userProfile.first()
            if (profile != null) {
                // Profile exists: show calming 3-breath preparation routine before entering Home
                _currentScreen.value = AppDestination.BREATHING
            } else {
                _currentScreen.value = AppDestination.SETUP
            }
            _isInitialized.value = true

            // Check and mark any missed reminder intervals today
            repository.checkAndProcessMissedReminders()
        }
    }

    fun navigateTo(destination: AppDestination) {
        _currentScreen.value = destination
    }

    fun saveProfile(name: String, dateOfBirth: String) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _uiNotification.value = UiNotification("Please enter your name", isError = true)
                return@launch
            }
            if (dateOfBirth.isBlank()) {
                _uiNotification.value = UiNotification("Please enter your date of birth", isError = true)
                return@launch
            }
            repository.saveUserProfile(name, dateOfBirth)
            // After setup, proceed to calming 3-breath preparation
            _currentScreen.value = AppDestination.BREATHING
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val currentMode = userPreferences.value.isDarkMode
            repository.setDarkMode(!currentMode)
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDarkMode(enabled)
        }
    }

    fun updateReminderInterval(intervalHours: Int) {
        viewModelScope.launch {
            repository.setReminderIntervalHours(intervalHours)
            _uiNotification.value = UiNotification("Reminders set to every $intervalHours hours")
        }
    }

    fun recordDrankWater() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.recordHydrationEvent(now)
            _uiNotification.value = UiNotification("Recorded water intake at ${timeFormat.format(Date(now))}")
        }
    }

    /**
     * Allows explicit verification and testing of the missed reminder mechanism.
     * Marks an expected reminder interval as missed.
     */
    fun simulateMissedReminder() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.recordMissedReminderEvent(now)
            _uiNotification.value = UiNotification("Reminder marked as missed")
        }
    }

    fun onBreathingCompleted() {
        viewModelScope.launch {
            repository.recordSession(
                sessionType = UserSession.TYPE_PREPARATION,
                startedAt = System.currentTimeMillis() - 18000,
                completedAt = System.currentTimeMillis(),
                cyclesCompleted = 3,
                isCompleted = true
            )
            _currentScreen.value = AppDestination.HOME
        }
    }

    fun startPractice() {
        _currentScreen.value = AppDestination.PRACTICE_BREATHING
    }

    fun onPracticeBreathingCompleted() {
        viewModelScope.launch {
            repository.recordSession(
                sessionType = UserSession.TYPE_PREPARATION,
                startedAt = System.currentTimeMillis() - 18000,
                completedAt = System.currentTimeMillis(),
                cyclesCompleted = 3,
                isCompleted = true
            )
            _currentScreen.value = AppDestination.MEMORY_EXPERIMENT
        }
    }

    fun saveMemoryExperimentSession(result: MemoryExperimentSessionResult) {
        viewModelScope.launch {
            repository.recordSession(
                sessionType = UserSession.TYPE_MEMORY_RECOGNITION,
                startedAt = result.startedAt,
                completedAt = result.completedAt,
                cyclesCompleted = result.measurements.completedTrials,
                isCompleted = true,
                metadata = result.metadataJson
            )
        }
    }

    fun startReactionExperiment() {
        _currentScreen.value = AppDestination.REACTION_EXPERIMENT
    }

    fun saveReactionExperimentSession(result: ReactionExperimentSessionResult) {
        viewModelScope.launch {
            repository.recordSession(
                sessionType = UserSession.TYPE_REACTION_COORDINATION,
                startedAt = result.startedAt,
                completedAt = result.completedAt,
                cyclesCompleted = result.measurements.completedTrials,
                isCompleted = true,
                metadata = result.metadataJson
            )
        }
    }

    fun getReactionAdaptiveDifficulty(): Double {
        val lastReactionSession = allSessions.value
            .filter { it.sessionType == UserSession.TYPE_REACTION_COORDINATION && it.isCompleted }
            .maxByOrNull { it.startedAt }

        if (lastReactionSession != null && !lastReactionSession.metadata.isNullOrBlank()) {
            try {
                val json = org.json.JSONObject(lastReactionSession.metadata)
                if (json.has("metrics")) {
                    val m = json.getJSONObject("metrics")
                    val diff = m.optDouble("adaptive_difficulty_seconds", 2.0)
                    if (!diff.isNaN() && diff > 0.0) return diff
                }
            } catch (_: Exception) {
                // Ignore parsing errors and return default
            }
        }
        return 2.0
    }

    fun onExperimentFinished() {
        _currentScreen.value = AppDestination.HOME
    }

    fun openCaregiverDashboard() {
        _currentScreen.value = AppDestination.CAREGIVER_DASHBOARD
    }

    fun recalculateCognitiveState() {
        viewModelScope.launch {
            repository.recalculateCognitiveState()
        }
    }

    fun openFamilyMemory() {
        _currentScreen.value = AppDestination.FAMILY_MEMORY
    }

    fun startFamilyMemoryExperiment() {
        val photos = allPhotos.value
        if (photos.size < 3) {
            _uiNotification.value = UiNotification("Add at least 3 family photos to begin this experiment")
            return
        }
        _currentScreen.value = AppDestination.FAMILY_MEMORY_BREATHING
    }

    fun onFamilyMemoryBreathingCompleted() {
        viewModelScope.launch {
            repository.recordSession(
                sessionType = UserSession.TYPE_PREPARATION,
                startedAt = System.currentTimeMillis() - 18000,
                completedAt = System.currentTimeMillis(),
                cyclesCompleted = 3,
                isCompleted = true
            )
            _currentScreen.value = AppDestination.FAMILY_MEMORY_EXPERIMENT
        }
    }

    fun saveFamilyMemoryExperimentSession(
        startedAt: Long,
        completedAt: Long,
        trials: List<com.example.data.model.FamilyRecognitionTrial>,
        metrics: com.example.experiment.family.FamilyMemorySessionMetrics
    ) {
        viewModelScope.launch {
            val peopleMap = allPeople.value.associateBy { it.personId }
            repository.saveFamilyMemoryExperimentSession(
                startedAt = startedAt,
                completedAt = completedAt,
                trials = trials,
                metrics = metrics,
                peopleMap = peopleMap
            )
            _uiNotification.value = UiNotification("Family memory experiment recorded")
        }
    }

    fun onFamilyMemoryExperimentFinished() {
        _currentScreen.value = AppDestination.FAMILY_MEMORY
    }

    fun openPersonDetail(person: Person) {
        _selectedPerson.value = person
        _viewingPhotoIndex.value = null
        _selectedExplorationPhotos.value = emptyList()
        _activeCandidateToInspect.value = null
        _isAnalyzingConnections.value = false
        _calculatedPairwiseResults.value = emptyList()
        _currentScreen.value = AppDestination.PERSON_DETAIL
    }

    fun togglePhotoSelection(photo: PhotoMemory) {
        val person = _selectedPerson.value ?: return
        val current = _selectedExplorationPhotos.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.photo.photoId == photo.photoId }
        if (existingIndex >= 0) {
            current.removeAt(existingIndex)
            val reordered = current.mapIndexed { idx, sel ->
                sel.copy(selectionOrder = idx + 1)
            }
            _selectedExplorationPhotos.value = reordered
        } else {
            val newSelection = ExplorationSelection(
                photo = photo,
                person = person,
                selectedAt = System.currentTimeMillis(),
                selectionOrder = current.size + 1
            )
            current.add(newSelection)
            _selectedExplorationPhotos.value = current
        }
    }

    fun addCandidatePhotoToSelection(candidate: CandidateConnection) {
        val current = _selectedExplorationPhotos.value.toMutableList()
        val exists = current.any { it.photo.photoId == candidate.candidatePhoto.photoId }
        if (!exists) {
            current.add(
                ExplorationSelection(
                    photo = candidate.candidatePhoto,
                    person = candidate.candidatePerson,
                    selectedAt = System.currentTimeMillis(),
                    selectionOrder = current.size + 1
                )
            )
            _selectedExplorationPhotos.value = current
            _uiNotification.value = UiNotification("Added ${candidate.candidatePerson.name}'s photo to selection")
        }
        _activeCandidateToInspect.value = null
    }

    fun clearExplorationSelections() {
        _selectedExplorationPhotos.value = emptyList()
        _calculatedPairwiseResults.value = emptyList()
        _isAnalyzingConnections.value = false
    }

    fun openCandidateInspection(candidate: CandidateConnection) {
        _activeCandidateToInspect.value = candidate
    }

    fun closeCandidateInspection() {
        _activeCandidateToInspect.value = null
    }

    fun startClusterAnalysis() {
        val selections = _selectedExplorationPhotos.value
        if (selections.isEmpty()) return
        val clusters = allClusters.value
        val pairwise = FamilyAssociationEngine.computeClusterAssociations(selections, clusters)
        _calculatedPairwiseResults.value = pairwise
        _isAnalyzingConnections.value = true
    }

    fun closeClusterAnalysis() {
        _isAnalyzingConnections.value = false
    }

    fun saveExplorationCluster(userResponse: String, isConfirmed: Boolean) {
        val person = _selectedPerson.value ?: return
        val selections = _selectedExplorationPhotos.value
        if (selections.isEmpty()) return
        val pairwise = _calculatedPairwiseResults.value

        viewModelScope.launch {
            repository.saveExplorationCluster(
                directoryPersonId = person.personId,
                selections = selections,
                pairwiseResults = pairwise,
                userResponse = userResponse,
                isSavedConnection = isConfirmed
            )
            if (isConfirmed) {
                _uiNotification.value = UiNotification("Saved connection to Family Memory")
            } else {
                _uiNotification.value = UiNotification("Exploration recorded")
            }
            _isAnalyzingConnections.value = false
            _selectedExplorationPhotos.value = emptyList()
        }
    }

    fun openPhotoViewer(index: Int) {
        _viewingPhotoIndex.value = index
    }

    fun closePhotoViewer() {
        _viewingPhotoIndex.value = null
    }

    fun nextPhoto() {
        val current = _viewingPhotoIndex.value ?: return
        val total = personPhotos.value.size
        if (current < total - 1) {
            _viewingPhotoIndex.value = current + 1
        }
    }

    fun previousPhoto() {
        val current = _viewingPhotoIndex.value ?: return
        if (current > 0) {
            _viewingPhotoIndex.value = current - 1
        }
    }

    fun importPhotos(personId: String, uris: List<Uri>) {
        viewModelScope.launch {
            if (uris.isEmpty()) return@launch
            val (added, duplicates) = repository.importPhotosForPerson(getApplication(), personId, uris)
            if (duplicates > 0 && added > 0) {
                _uiNotification.value = UiNotification("Added $added photos ($duplicates duplicate skipped)")
            } else if (duplicates > 0 && added == 0) {
                _uiNotification.value = UiNotification("Selected photo is already in this collection")
            } else if (added == 1) {
                _uiNotification.value = UiNotification("Photo added to collection")
            } else {
                _uiNotification.value = UiNotification("Added $added photos to collection")
            }
        }
    }

    fun deletePhotoMemory(photo: PhotoMemory) {
        viewModelScope.launch {
            repository.deletePhotoMemory(photo)
            val current = _viewingPhotoIndex.value
            val photos = personPhotos.value
            if (photos.size <= 1) {
                _viewingPhotoIndex.value = null
            } else if (current != null && current >= photos.size - 1) {
                _viewingPhotoIndex.value = (photos.size - 2).coerceAtLeast(0)
            }
            _uiNotification.value = UiNotification("Photo removed from memories")
        }
    }

    fun setPhotoAsCover(person: Person, photo: PhotoMemory) {
        viewModelScope.launch {
            repository.setCoverPhotoFromMemory(person, photo)
            _selectedPerson.value = person.copy(coverPhotoUri = photo.localUri)
            _uiNotification.value = UiNotification("Set as cover photo")
        }
    }

    fun openAddPerson() {
        _personToEdit.value = null
        _currentScreen.value = AppDestination.ADD_PERSON
    }

    fun openEditPerson(person: Person) {
        _personToEdit.value = person
        _currentScreen.value = AppDestination.ADD_PERSON
    }

    fun savePerson(name: String, relationship: String, coverPhotoUri: String?, existingPersonId: String? = null) {
        viewModelScope.launch {
            val trimmedName = name.trim()
            val trimmedRel = relationship.trim()
            if (trimmedName.isBlank()) {
                _uiNotification.value = UiNotification("Please enter a name", isError = true)
                return@launch
            }
            if (trimmedRel.isBlank()) {
                _uiNotification.value = UiNotification("Please specify a relationship", isError = true)
                return@launch
            }

            if (existingPersonId != null) {
                val updated = Person(
                    personId = existingPersonId,
                    name = trimmedName,
                    relationship = trimmedRel,
                    coverPhotoUri = coverPhotoUri,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updatePerson(updated)
                _selectedPerson.value = updated
                _uiNotification.value = UiNotification("Updated $trimmedName")
                _currentScreen.value = AppDestination.PERSON_DETAIL
            } else {
                val newPerson = Person(
                    name = trimmedName,
                    relationship = trimmedRel,
                    coverPhotoUri = coverPhotoUri
                )
                repository.insertPerson(newPerson)
                _uiNotification.value = UiNotification("Added $trimmedName to Family Memory")
                _currentScreen.value = AppDestination.FAMILY_MEMORY
            }
        }
    }

    fun updatePersonPhoto(person: Person, newCoverPhotoUri: String?) {
        viewModelScope.launch {
            val updated = person.copy(
                coverPhotoUri = newCoverPhotoUri,
                updatedAt = System.currentTimeMillis()
            )
            repository.updatePerson(updated)
            _selectedPerson.value = updated
            _uiNotification.value = UiNotification("Updated photo for ${person.name}")
        }
    }

    fun deletePerson(person: Person) {
        viewModelScope.launch {
            PhotoStorageHelper.deletePhoto(person.coverPhotoUri)
            repository.deletePerson(person)
            _selectedPerson.value = null
            _uiNotification.value = UiNotification("${person.name} removed from Family Memory")
            _currentScreen.value = AppDestination.FAMILY_MEMORY
        }
    }

    fun clearNotification() {
        _uiNotification.value = null
    }

    fun resetAllDataForTesting() {
        viewModelScope.launch {
            repository.resetAllData()
            _currentScreen.value = AppDestination.SETUP
            _uiNotification.value = UiNotification("App reset to initial state")
        }
    }

    /**
     * Calculates the next expected reminder time today based on user's preference.
     */
    fun getNextReminderTimeString(): String {
        val prefs = userPreferences.value
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        for (hour in prefs.reminderStartHour..prefs.reminderEndHour step prefs.reminderIntervalHours) {
            if (hour > currentHour) {
                val nextCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, 0)
                }
                return timeFormat.format(nextCal.time)
            }
        }
        return "Tomorrow at ${prefs.reminderStartHour}:00 AM"
    }

    fun openMemoryMap() {
        _currentScreen.value = AppDestination.MEMORY_MAP
    }

    fun setMapTimelineMode(timeline: Boolean) {
        _mapTimelineMode.value = timeline
    }

    fun showNotification(message: String, isError: Boolean = false) {
        _uiNotification.value = UiNotification(message, isError)
    }

    fun inspectAssociation(association: PhotoAssociation) {
        _activeMapInspectionAssociation.value = association
    }

    fun inspectMapAssociation(association: PhotoAssociation) = inspectAssociation(association)

    fun closeAssociationInspection() {
        _activeMapInspectionAssociation.value = null
    }

    fun closeMapAssociationInspection() = closeAssociationInspection()

    fun inspectCluster(cluster: SelectionCluster) {
        _activeMapInspectionCluster.value = cluster
    }

    fun inspectMapCluster(cluster: SelectionCluster) = inspectCluster(cluster)

    fun closeClusterInspection() {
        _activeMapInspectionCluster.value = null
    }

    fun closeMapClusterInspection() = closeClusterInspection()

    fun deleteSelectionCluster(cluster: SelectionCluster) = deleteCluster(cluster)

    fun confirmAssociation(associationId: String) {
        viewModelScope.launch {
            repository.confirmAssociation(associationId)
            _activeMapInspectionAssociation.value?.let { current ->
                if (current.associationId == associationId) {
                    _activeMapInspectionAssociation.value = current.copy(
                        userConfirmed = true,
                        source = "user_confirmed",
                        userResponse = "confirmed",
                        confirmedAt = System.currentTimeMillis()
                    )
                }
            }
            showNotification("Connection confirmed!")
        }
    }

    fun rejectAssociation(associationId: String) {
        viewModelScope.launch {
            repository.rejectAssociation(associationId)
            _activeMapInspectionAssociation.value?.let { current ->
                if (current.associationId == associationId) {
                    _activeMapInspectionAssociation.value = current.copy(
                        userConfirmed = false,
                        userResponse = "rejected"
                    )
                }
            }
            showNotification("Candidate connection dismissed.")
        }
    }

    fun removeAssociation(associationId: String) {
        viewModelScope.launch {
            repository.removeAssociation(associationId)
            _activeMapInspectionAssociation.value = null
            showNotification("Connection removed.")
        }
    }

    fun startManualConnection(photo: PhotoMemory) {
        _manualConnectSourcePhoto.value = photo
        showNotification("First photo selected. Tap another photo to connect memories.")
    }

    fun cancelManualConnection() {
        _manualConnectSourcePhoto.value = null
    }

    fun completeManualConnection(targetPhoto: PhotoMemory, userNote: String? = null) {
        val sourcePhoto = _manualConnectSourcePhoto.value
        if (sourcePhoto == null) return
        if (sourcePhoto.photoId == targetPhoto.photoId) {
            showNotification("Please select a different photo to connect", isError = true)
            return
        }

        viewModelScope.launch {
            val people = allPeople.value.associateBy { it.personId }
            val personA = people[sourcePhoto.personId]
            val personB = people[targetPhoto.personId]
            if (personA == null || personB == null) {
                showNotification("Could not find person records for photos", isError = true)
                return@launch
            }

            val created = repository.createManualConnection(
                photoA = sourcePhoto,
                photoB = targetPhoto,
                personA = personA,
                personB = personB,
                userNote = userNote
            )
            _manualConnectSourcePhoto.value = null
            _activeMapInspectionAssociation.value = created
            showNotification("Memory connection created!")
        }
    }

    fun saveAssociationMemoryNote(associationId: String, textNote: String?, voiceNoteUri: String?) {
        viewModelScope.launch {
            repository.updateAssociationMemoryNote(associationId, textNote, voiceNoteUri)
            _activeMapInspectionAssociation.value?.let { current ->
                if (current.associationId == associationId) {
                    _activeMapInspectionAssociation.value = current.copy(
                        userNote = textNote,
                        voiceNoteUri = voiceNoteUri
                    )
                }
            }
            showNotification("Memory note saved!")
        }
    }

    fun saveClusterMemory(clusterId: String, textNote: String?, voiceNoteUri: String?, reflection: String?) {
        viewModelScope.launch {
            repository.updateClusterMemory(clusterId, textNote, voiceNoteUri, reflection)
            _activeMapInspectionCluster.value?.let { current ->
                if (current.clusterId == clusterId) {
                    _activeMapInspectionCluster.value = current.copy(
                        userNote = textNote,
                        voiceNoteUri = voiceNoteUri,
                        userReflection = reflection
                    )
                }
            }
            showNotification("Memory cluster saved!")
        }
    }

    fun deleteCluster(cluster: SelectionCluster) {
        viewModelScope.launch {
            repository.deleteCluster(cluster.clusterId)
            _activeMapInspectionCluster.value = null
            showNotification("Memory cluster removed.")
        }
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getInstance(application)
                    val repo = CognitiveRepositoryImpl(db)
                    return AppViewModel(application, repo) as T
                }
            }
    }
}
