package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.BreathingPreparationScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PracticeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WelcomeSetupScreen
import com.example.ui.screens.caregiver.CaregiverDashboardScreen
import com.example.ui.screens.experiment.MemoryExperimentScreen
import com.example.ui.screens.experiment.ReactionExperimentScreen
import com.example.ui.screens.family.AddEditPersonScreen
import com.example.ui.screens.family.FamilyMemoryScreen
import com.example.ui.screens.family.MemoryMapScreen
import com.example.ui.screens.family.MemoryRecognitionScreen
import com.example.ui.screens.family.PersonDetailScreen
import com.example.ui.theme.CognitiveAssistantTheme
import com.example.ui.viewmodel.AppDestination
import com.example.ui.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = viewModel(
                factory = AppViewModel.provideFactory(application)
            )
            val preferences by appViewModel.userPreferences.collectAsStateWithLifecycle()
            val userProfile by appViewModel.userProfile.collectAsStateWithLifecycle()
            val currentScreen by appViewModel.currentScreen.collectAsStateWithLifecycle()
            val todayRecords by appViewModel.todayRecords.collectAsStateWithLifecycle()
            val cognitiveState by appViewModel.cognitiveState.collectAsStateWithLifecycle()
            val allPeople by appViewModel.allPeople.collectAsStateWithLifecycle()
            val allPhotos by appViewModel.allPhotos.collectAsStateWithLifecycle()
            val selectedPerson by appViewModel.selectedPerson.collectAsStateWithLifecycle()
            val personPhotos by appViewModel.personPhotos.collectAsStateWithLifecycle()
            val viewingPhotoIndex by appViewModel.viewingPhotoIndex.collectAsStateWithLifecycle()
            val personToEdit by appViewModel.personToEdit.collectAsStateWithLifecycle()
            val selectedExplorationPhotos by appViewModel.selectedExplorationPhotos.collectAsStateWithLifecycle()
            val crossPersonCandidates by appViewModel.crossPersonCandidates.collectAsStateWithLifecycle()
            val activeCandidateToInspect by appViewModel.activeCandidateToInspect.collectAsStateWithLifecycle()
            val isAnalyzingConnections by appViewModel.isAnalyzingConnections.collectAsStateWithLifecycle()
            val calculatedPairwiseResults by appViewModel.calculatedPairwiseResults.collectAsStateWithLifecycle()
            val allAssociations by appViewModel.allAssociations.collectAsStateWithLifecycle()
            val allClusters by appViewModel.allClusters.collectAsStateWithLifecycle()
            val manualConnectSourcePhoto by appViewModel.manualConnectSourcePhoto.collectAsStateWithLifecycle()
            val activeMapInspectionAssociation by appViewModel.activeMapInspectionAssociation.collectAsStateWithLifecycle()
            val activeMapInspectionCluster by appViewModel.activeMapInspectionCluster.collectAsStateWithLifecycle()
            val isMapTimelineMode by appViewModel.mapTimelineMode.collectAsStateWithLifecycle()
            val notification by appViewModel.uiNotification.collectAsStateWithLifecycle()
            val isInitialized by appViewModel.isInitialized.collectAsStateWithLifecycle()

            val snackbarHostState = remember { SnackbarHostState() }
            var isEditingProfileInSettings by remember { mutableStateOf(false) }

            LaunchedEffect(notification) {
                notification?.let {
                    snackbarHostState.showSnackbar(it.message)
                    appViewModel.clearNotification()
                }
            }

            CognitiveAssistantTheme(darkTheme = preferences.isDarkMode) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (!isInitialized) {
                            // Waiting for initial state
                        } else if (isEditingProfileInSettings) {
                            BackHandler {
                                isEditingProfileInSettings = false
                            }
                            WelcomeSetupScreen(
                                initialName = userProfile?.name ?: "",
                                initialDob = userProfile?.dateOfBirth ?: "",
                                isDarkMode = preferences.isDarkMode,
                                onToggleDarkMode = { appViewModel.toggleDarkMode() },
                                onSaveProfile = { name, dob ->
                                    appViewModel.saveProfile(name, dob)
                                    isEditingProfileInSettings = false
                                },
                                isEditing = true,
                                onCancelEdit = { isEditingProfileInSettings = false }
                            )
                        } else {
                            when (currentScreen) {
                                AppDestination.SETUP -> {
                                    WelcomeSetupScreen(
                                        initialName = userProfile?.name ?: "",
                                        initialDob = userProfile?.dateOfBirth ?: "",
                                        isDarkMode = preferences.isDarkMode,
                                        onToggleDarkMode = { appViewModel.toggleDarkMode() },
                                        onSaveProfile = { name, dob ->
                                            appViewModel.saveProfile(name, dob)
                                        }
                                    )
                                }

                                AppDestination.BREATHING -> {
                                    BreathingPreparationScreen(
                                        onComplete = {
                                            appViewModel.onBreathingCompleted()
                                        }
                                    )
                                }

                                AppDestination.HOME -> {
                                    HomeScreen(
                                        userProfile = userProfile,
                                        isDarkMode = preferences.isDarkMode,
                                        onToggleDarkMode = { appViewModel.toggleDarkMode() },
                                        todayRecords = todayRecords,
                                        nextReminderTime = appViewModel.getNextReminderTimeString(),
                                        onDrankWater = { appViewModel.recordDrankWater() },
                                        onSimulateMissedReminder = { appViewModel.simulateMissedReminder() },
                                        onOpenPractice = { appViewModel.navigateTo(AppDestination.PRACTICE) },
                                        onOpenSettings = { appViewModel.navigateTo(AppDestination.SETTINGS) },
                                        onOpenCaregiverDashboard = { appViewModel.openCaregiverDashboard() }
                                    )
                                }

                                AppDestination.PRACTICE, AppDestination.PRACTICE_PLACEHOLDER -> {
                                    BackHandler {
                                        appViewModel.navigateTo(AppDestination.HOME)
                                    }
                                    PracticeScreen(
                                        onStartPractice = {
                                            appViewModel.startPractice()
                                        },
                                        onStartReactionExperiment = {
                                            appViewModel.startReactionExperiment()
                                        },
                                        onOpenFamilyMemory = {
                                            appViewModel.openFamilyMemory()
                                        },
                                        onBackToHome = { appViewModel.navigateTo(AppDestination.HOME) }
                                    )
                                }

                                AppDestination.PRACTICE_BREATHING -> {
                                    BackHandler {
                                        appViewModel.navigateTo(AppDestination.PRACTICE)
                                    }
                                    BreathingPreparationScreen(
                                        title = "Prepare Your Attention",
                                        subtitle = "A brief 3-breath routine to settle and focus your attention before beginning the exercise.",
                                        continueButtonText = "Begin Practice",
                                        skipButtonText = "Skip to Practice",
                                        onComplete = {
                                            appViewModel.onPracticeBreathingCompleted()
                                        }
                                    )
                                }

                                AppDestination.MEMORY_EXPERIMENT -> {
                                    BackHandler {
                                        appViewModel.navigateTo(AppDestination.PRACTICE)
                                    }
                                    MemoryExperimentScreen(
                                        onSaveSession = { result ->
                                            appViewModel.saveMemoryExperimentSession(result)
                                        },
                                        onFinishAndReturnHome = {
                                            appViewModel.onExperimentFinished()
                                        }
                                    )
                                }

                                AppDestination.REACTION_EXPERIMENT -> {
                                    BackHandler {
                                        appViewModel.navigateTo(AppDestination.PRACTICE)
                                    }
                                    ReactionExperimentScreen(
                                        startingDifficultySeconds = appViewModel.getReactionAdaptiveDifficulty(),
                                        onSaveSession = { result ->
                                            appViewModel.saveReactionExperimentSession(result)
                                        },
                                        onFinishAndReturnHome = {
                                            appViewModel.onExperimentFinished()
                                        }
                                    )
                                }

                                AppDestination.SETTINGS -> {
                                    BackHandler {
                                        appViewModel.navigateTo(AppDestination.HOME)
                                    }
                                    SettingsScreen(
                                        userProfile = userProfile,
                                        userPreferences = preferences,
                                        isDarkMode = preferences.isDarkMode,
                                        onToggleDarkMode = { appViewModel.toggleDarkMode() },
                                        onEditProfile = { isEditingProfileInSettings = true },
                                        onChangeReminderInterval = { hours ->
                                            appViewModel.updateReminderInterval(hours)
                                        },
                                        onStartBreathing = {
                                            appViewModel.navigateTo(AppDestination.BREATHING)
                                        },
                                        onResetAllData = {
                                            appViewModel.resetAllDataForTesting()
                                        },
                                        onBack = { appViewModel.navigateTo(AppDestination.HOME) },
                                        onOpenCaregiverDashboard = { appViewModel.openCaregiverDashboard() }
                                    )
                                }

                                AppDestination.CAREGIVER_DASHBOARD -> {
                                    BackHandler {
                                        appViewModel.navigateTo(AppDestination.HOME)
                                    }
                                    CaregiverDashboardScreen(
                                        cognitiveState = cognitiveState,
                                        onRecalculateState = {
                                            appViewModel.recalculateCognitiveState()
                                        },
                                        onBackToHome = {
                                            appViewModel.navigateTo(AppDestination.HOME)
                                        }
                                    )
                                }

                                AppDestination.FAMILY_MEMORY -> {
                                    BackHandler {
                                        appViewModel.navigateTo(AppDestination.PRACTICE)
                                    }
                                    FamilyMemoryScreen(
                                        people = allPeople,
                                        totalPhotosCount = allPhotos.size,
                                        onOpenPerson = { person ->
                                            appViewModel.openPersonDetail(person)
                                        },
                                        onAddPerson = {
                                            appViewModel.openAddPerson()
                                        },
                                        onStartExperiment = {
                                            appViewModel.startFamilyMemoryExperiment()
                                        },
                                        onOpenMemoryMap = {
                                            appViewModel.openMemoryMap()
                                        },
                                        onBack = {
                                            appViewModel.navigateTo(AppDestination.PRACTICE)
                                        }
                                    )
                                }

                                AppDestination.FAMILY_MEMORY_BREATHING -> {
                                    BackHandler {
                                        appViewModel.navigateTo(AppDestination.FAMILY_MEMORY)
                                    }
                                    BreathingPreparationScreen(
                                        title = "Prepare Your Attention",
                                        subtitle = "Take three calm breaths before viewing your family memories.",
                                        continueButtonText = "Begin Experiment",
                                        skipButtonText = "Skip to Experiment",
                                        onComplete = {
                                            appViewModel.onFamilyMemoryBreathingCompleted()
                                        }
                                    )
                                }

                                AppDestination.FAMILY_MEMORY_EXPERIMENT -> {
                                    BackHandler {
                                        appViewModel.navigateTo(AppDestination.FAMILY_MEMORY)
                                    }
                                    MemoryRecognitionScreen(
                                        photos = allPhotos,
                                        people = allPeople,
                                        onSaveSession = { startedAt, completedAt, trials, metrics ->
                                            appViewModel.saveFamilyMemoryExperimentSession(
                                                startedAt,
                                                completedAt,
                                                trials,
                                                metrics
                                            )
                                        },
                                        onFinish = {
                                            appViewModel.onFamilyMemoryExperimentFinished()
                                        },
                                        onBack = {
                                            appViewModel.navigateTo(AppDestination.FAMILY_MEMORY)
                                        }
                                    )
                                }

                                 AppDestination.PERSON_DETAIL -> {
                                    BackHandler {
                                        if (viewingPhotoIndex != null) {
                                            appViewModel.closePhotoViewer()
                                        } else if (isAnalyzingConnections) {
                                            appViewModel.closeClusterAnalysis()
                                        } else if (activeCandidateToInspect != null) {
                                            appViewModel.closeCandidateInspection()
                                        } else if (selectedExplorationPhotos.isNotEmpty()) {
                                            appViewModel.clearExplorationSelections()
                                        } else {
                                            appViewModel.navigateTo(AppDestination.FAMILY_MEMORY)
                                        }
                                    }
                                    selectedPerson?.let { person ->
                                        PersonDetailScreen(
                                            person = person,
                                            photos = personPhotos,
                                            viewingPhotoIndex = viewingPhotoIndex,
                                            selectedExplorationPhotos = selectedExplorationPhotos,
                                            crossPersonCandidates = crossPersonCandidates,
                                            activeCandidateToInspect = activeCandidateToInspect,
                                            isAnalyzingConnections = isAnalyzingConnections,
                                            calculatedPairwiseResults = calculatedPairwiseResults,
                                            onTogglePhotoSelection = { photo ->
                                                appViewModel.togglePhotoSelection(photo)
                                            },
                                            onClearSelections = {
                                                appViewModel.clearExplorationSelections()
                                            },
                                            onAddCandidatePhoto = { candidate ->
                                                appViewModel.addCandidatePhotoToSelection(candidate)
                                            },
                                            onOpenCandidateInspection = { candidate ->
                                                appViewModel.openCandidateInspection(candidate)
                                            },
                                            onCloseCandidateInspection = {
                                                appViewModel.closeCandidateInspection()
                                            },
                                            onStartClusterAnalysis = {
                                                appViewModel.startClusterAnalysis()
                                            },
                                            onCloseClusterAnalysis = {
                                                appViewModel.closeClusterAnalysis()
                                            },
                                            onSaveExplorationCluster = { userResponse, isConfirmed ->
                                                appViewModel.saveExplorationCluster(userResponse, isConfirmed)
                                            },
                                            onOpenPhotoViewer = { index ->
                                                appViewModel.openPhotoViewer(index)
                                            },
                                            onClosePhotoViewer = {
                                                appViewModel.closePhotoViewer()
                                            },
                                            onNextPhoto = {
                                                appViewModel.nextPhoto()
                                            },
                                            onPreviousPhoto = {
                                                appViewModel.previousPhoto()
                                            },
                                            onDeletePhoto = { photo ->
                                                appViewModel.deletePhotoMemory(photo)
                                            },
                                            onSetPhotoAsCover = { photo ->
                                                appViewModel.setPhotoAsCover(person, photo)
                                            },
                                            onImportPhotos = { uris ->
                                                appViewModel.importPhotos(person.personId, uris)
                                            },
                                            onEditPerson = { p ->
                                                appViewModel.openEditPerson(p)
                                            },
                                            onUpdatePhoto = { p, newPhotoUri ->
                                                appViewModel.updatePersonPhoto(p, newPhotoUri)
                                            },
                                            onDeletePerson = { p ->
                                                appViewModel.deletePerson(p)
                                            },
                                            onBack = {
                                                appViewModel.clearExplorationSelections()
                                                appViewModel.navigateTo(AppDestination.FAMILY_MEMORY)
                                            }
                                        )
                                    } ?: run {
                                        appViewModel.navigateTo(AppDestination.FAMILY_MEMORY)
                                    }
                                }

                                AppDestination.ADD_PERSON -> {
                                    BackHandler {
                                        if (personToEdit != null) {
                                            appViewModel.navigateTo(AppDestination.PERSON_DETAIL)
                                        } else {
                                            appViewModel.navigateTo(AppDestination.FAMILY_MEMORY)
                                        }
                                    }
                                    AddEditPersonScreen(
                                        personToEdit = personToEdit,
                                        onSavePerson = { name, relationship, coverPhotoUri, existingPersonId ->
                                            appViewModel.savePerson(name, relationship, coverPhotoUri, existingPersonId)
                                        },
                                        onBack = {
                                            if (personToEdit != null) {
                                                appViewModel.navigateTo(AppDestination.PERSON_DETAIL)
                                            } else {
                                                appViewModel.navigateTo(AppDestination.FAMILY_MEMORY)
                                            }
                                        }
                                    )
                                }

                                AppDestination.MEMORY_MAP -> {
                                    BackHandler {
                                        appViewModel.navigateTo(AppDestination.FAMILY_MEMORY)
                                    }
                                    MemoryMapScreen(
                                        allPhotos = allPhotos,
                                        allPeople = allPeople,
                                        allAssociations = allAssociations,
                                        allClusters = allClusters,
                                        manualConnectSourcePhoto = manualConnectSourcePhoto,
                                        activeMapInspectionAssociation = activeMapInspectionAssociation,
                                        activeMapInspectionCluster = activeMapInspectionCluster,
                                        isTimelineMode = isMapTimelineMode,
                                        onSetTimelineMode = { appViewModel.setMapTimelineMode(it) },
                                        onInspectAssociation = { appViewModel.inspectMapAssociation(it) },
                                        onCloseAssociationInspection = { appViewModel.closeMapAssociationInspection() },
                                        onInspectCluster = { appViewModel.inspectMapCluster(it) },
                                        onCloseClusterInspection = { appViewModel.closeMapClusterInspection() },
                                        onConfirmAssociation = { appViewModel.confirmAssociation(it) },
                                        onRejectAssociation = { appViewModel.rejectAssociation(it) },
                                        onRemoveAssociation = { appViewModel.removeAssociation(it) },
                                        onSaveAssociationNote = { id, text, voice ->
                                            appViewModel.saveAssociationMemoryNote(id, text, voice)
                                        },
                                        onSaveClusterMemory = { id, text, voice, ref ->
                                            appViewModel.saveClusterMemory(id, text, voice, ref)
                                        },
                                        onDeleteCluster = { appViewModel.deleteSelectionCluster(it) },
                                        onStartManualConnection = { appViewModel.startManualConnection(it) },
                                        onCancelManualConnection = { appViewModel.cancelManualConnection() },
                                        onCompleteManualConnection = { target, note ->
                                            appViewModel.completeManualConnection(target, note)
                                        },
                                        onNavigateToPhotoExploration = {
                                            if (allPeople.isNotEmpty()) {
                                                appViewModel.openPersonDetail(allPeople.first())
                                            } else {
                                                appViewModel.navigateTo(AppDestination.FAMILY_MEMORY)
                                            }
                                        },
                                        onBack = { appViewModel.navigateTo(AppDestination.FAMILY_MEMORY) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Retained for greeting preview and tests
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
