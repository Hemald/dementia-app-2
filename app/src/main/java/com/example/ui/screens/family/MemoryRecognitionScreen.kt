package com.example.ui.screens.family

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.FamilyRecognitionTrial
import com.example.data.model.Person
import com.example.data.model.PhotoAssociation
import com.example.data.model.PhotoMemory
import com.example.experiment.family.FamilyMemoryExperimentEngine
import com.example.experiment.family.FamilyMemorySessionMetrics
import com.example.util.PhotoStorageHelper
import java.io.File
import java.util.Locale

private enum class ExperimentStage {
    INTRO,
    TRIAL,
    SELECTION,
    SUMMARY
}

@Composable
fun MemoryRecognitionScreen(
    photos: List<PhotoMemory>,
    people: List<Person>,
    onSaveSession: (
        startedAt: Long,
        completedAt: Long,
        trials: List<FamilyRecognitionTrial>,
        metrics: FamilyMemorySessionMetrics
    ) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    // Pick up to 8 randomized or sequential photos for the experiment session (minimum 3 required)
    val sessionPhotos = remember(photos) {
        photos.shuffled().take(8.coerceAtMost(photos.size))
    }

    var currentStage by remember { mutableStateOf(ExperimentStage.INTRO) }
    var currentTrialIndex by remember { mutableIntStateOf(0) }
    var trialStartTimeMs by remember { mutableLongStateOf(0L) }
    var experimentStartedAtMs by remember { mutableLongStateOf(0L) }
    var experimentCompletedAtMs by remember { mutableLongStateOf(0L) }
    var showExitDialog by remember { mutableStateOf(false) }

    // Recorded responses for each trial
    val recordedTrials = remember { mutableStateListOf<FamilyRecognitionTrial>() }

    // Selected photo indices and their selection orders
    val selectedPhotoIdsInOrder = remember { mutableStateListOf<String>() }

    // Calculated metrics and candidate associations for summary
    var sessionMetrics by remember { mutableStateOf<FamilyMemorySessionMetrics?>(null) }
    var candidateAssociations by remember { mutableStateOf<List<PhotoAssociation>>(emptyList()) }

    val peopleMap = remember(people) { people.associateBy { it.personId } }
    val photosMap = remember(photos) { photos.associateBy { it.photoId } }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Experiment?") },
            text = { Text("Your progress for this session will not be saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onBack()
                    }
                ) {
                    Text("Exit", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Continue")
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = currentStage,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "experiment_stage_transition"
        ) { stage ->
            when (stage) {
                ExperimentStage.INTRO -> {
                    IntroStageContent(
                        totalPhotos = sessionPhotos.size,
                        onStart = {
                            experimentStartedAtMs = System.currentTimeMillis()
                            trialStartTimeMs = System.currentTimeMillis()
                            currentTrialIndex = 0
                            recordedTrials.clear()
                            selectedPhotoIdsInOrder.clear()
                            currentStage = ExperimentStage.TRIAL
                        },
                        onBack = onBack
                    )
                }

                ExperimentStage.TRIAL -> {
                    if (currentTrialIndex < sessionPhotos.size) {
                        val currentPhoto = sessionPhotos[currentTrialIndex]

                        LaunchedEffect(currentTrialIndex) {
                            trialStartTimeMs = System.currentTimeMillis()
                        }

                        TrialStageContent(
                            photo = currentPhoto,
                            currentIndex = currentTrialIndex,
                            totalCount = sessionPhotos.size,
                            onResponse = { responseType ->
                                val now = System.currentTimeMillis()
                                val responseTime = (now - trialStartTimeMs).coerceAtLeast(50L)

                                val trial = FamilyRecognitionTrial(
                                    trialId = java.util.UUID.randomUUID().toString(),
                                    sessionId = 0L,
                                    photoId = currentPhoto.photoId,
                                    personId = currentPhoto.personId,
                                    trialStartedAt = trialStartTimeMs,
                                    photoShownAt = trialStartTimeMs,
                                    questionShownAt = trialStartTimeMs,
                                    responseAt = now,
                                    recognitionResponse = responseType,
                                    responseTimeMs = responseTime
                                )
                                recordedTrials.add(trial)

                                if (currentTrialIndex + 1 < sessionPhotos.size) {
                                    currentTrialIndex++
                                } else {
                                    // Trials complete, move to selection stage
                                    currentStage = ExperimentStage.SELECTION
                                }
                            },
                            onExitRequest = { showExitDialog = true }
                        )
                    }
                }

                ExperimentStage.SELECTION -> {
                    SelectionStageContent(
                        testedPhotos = sessionPhotos,
                        selectedPhotoIdsInOrder = selectedPhotoIdsInOrder,
                        onToggleSelection = { photoId ->
                            if (selectedPhotoIdsInOrder.contains(photoId)) {
                                selectedPhotoIdsInOrder.remove(photoId)
                            } else {
                                selectedPhotoIdsInOrder.add(photoId)
                            }
                        },
                        onContinue = {
                            experimentCompletedAtMs = System.currentTimeMillis()

                            // Update trials with selection flags and orders
                            val finalizedTrials = recordedTrials.map { trial ->
                                val orderIndex = selectedPhotoIdsInOrder.indexOf(trial.photoId)
                                if (orderIndex >= 0) {
                                    trial.copy(
                                        selected = true,
                                        selectionOrder = orderIndex + 1,
                                        selectionTimestamp = experimentCompletedAtMs
                                    )
                                } else {
                                    trial.copy(
                                        selected = false,
                                        selectionOrder = null
                                    )
                                }
                            }
                            recordedTrials.clear()
                            recordedTrials.addAll(finalizedTrials)

                            // Compute metrics
                            val metrics = FamilyMemoryExperimentEngine.calculateSessionMetrics(finalizedTrials, peopleMap)
                            sessionMetrics = metrics

                            // Compute candidate associations if 2+ photos were selected
                            val selectedTrials = finalizedTrials.filter { it.selected }
                            if (selectedTrials.size >= 2) {
                                candidateAssociations = FamilyMemoryExperimentEngine.computeCandidateAssociations(
                                    clusterId = "temp",
                                    selectedTrials = selectedTrials,
                                    photosMap = photosMap
                                )
                            } else {
                                candidateAssociations = emptyList()
                            }

                            currentStage = ExperimentStage.SUMMARY
                        }
                    )
                }

                ExperimentStage.SUMMARY -> {
                    sessionMetrics?.let { metrics ->
                        SummaryStageContent(
                            metrics = metrics,
                            trials = recordedTrials,
                            candidateAssociations = candidateAssociations,
                            photosMap = photosMap,
                            peopleMap = peopleMap,
                            onComplete = {
                                onSaveSession(
                                    experimentStartedAtMs,
                                    experimentCompletedAtMs,
                                    recordedTrials.toList(),
                                    metrics
                                )
                                onFinish()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroStageContent(
    totalPhotos: Int,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .testTag("exit_family_memory_experiment_button")
                    .size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Return to Family Memory",
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Family Memory",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Memory Recognition",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Observe your recall and familiarity with personal photographs.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "How it works:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    IntroStepRow(
                        number = "1",
                        title = "Photo Presentation",
                        description = "$totalPhotos family photos will be shown one at a time."
                    )
                    IntroStepRow(
                        number = "2",
                        title = "Immediate Response",
                        description = "Indicate whether you recognize, feel familiar with, or don't recall each photo."
                    )
                    IntroStepRow(
                        number = "3",
                        title = "Connect Memories",
                        description = "After viewing, choose photos that feel connected to you."
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("start_family_recognition_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Begin Experiment",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun IntroStepRow(
    number: String,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrialStageContent(
    photo: PhotoMemory,
    currentIndex: Int,
    totalCount: Int,
    onResponse: (String) -> Unit,
    onExitRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar: Progress and Close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Photo ${currentIndex + 1} of $totalCount",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (currentIndex + 1).toFloat() / totalCount.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    strokeCap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            IconButton(
                onClick = onExitRequest,
                modifier = Modifier
                    .testTag("trial_close_button")
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit experiment"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Photo View Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(20.dp))
                    .testTag("trial_photo_display"),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val context = LocalContext.current
                    val imageModel = PhotoStorageHelper.resolveImageModel(context, photo.localUri)
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "Family photograph for recognition testing",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "How does this photograph feel to you?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Response Buttons (Vertical Stack with rich affordances, touch target >= 56dp)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RecognitionResponseButton(
                    testTag = "response_recognized_button",
                    title = "I Recognize This",
                    subtitle = "I clearly recognize who or what is shown",
                    icon = Icons.Default.CheckCircle,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onResponse(FamilyRecognitionTrial.RECOGNIZE) }
                )

                RecognitionResponseButton(
                    testTag = "response_familiar_button",
                    title = "Seems Familiar",
                    subtitle = "Feels familiar, though specific details are hazy",
                    icon = Icons.Default.Visibility,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onResponse(FamilyRecognitionTrial.FAMILIAR) }
                )

                RecognitionResponseButton(
                    testTag = "response_uncertain_button",
                    title = "Not Sure",
                    subtitle = "Uncertain whether I have seen this photo before",
                    icon = Icons.Default.HelpOutline,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onResponse(FamilyRecognitionTrial.NOT_SURE) }
                )

                RecognitionResponseButton(
                    testTag = "response_dont_remember_button",
                    title = "I Don't Remember",
                    subtitle = "I do not recall this photograph",
                    icon = Icons.Default.Close,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    isOutlined = true,
                    onClick = { onResponse(FamilyRecognitionTrial.DONT_REMEMBER) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RecognitionResponseButton(
    testTag: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    isOutlined: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isOutlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun SelectionStageContent(
    testedPhotos: List<PhotoMemory>,
    selectedPhotoIdsInOrder: List<String>,
    onToggleSelection: (String) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select Connected Memories",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tap any photos that feel connected or meaningful to you right now. Choose in whatever order feels natural.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedPhotoIdsInOrder.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "${selectedPhotoIdsInOrder.size} photo(s) selected in order",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Photo Grid (Selection)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(items = testedPhotos, key = { _, photo -> photo.photoId }) { _, photo ->
                val orderIndex = selectedPhotoIdsInOrder.indexOf(photo.photoId)
                val isSelected = orderIndex >= 0

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onToggleSelection(photo.photoId) }
                        .testTag("selection_photo_${photo.photoId}"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val context = LocalContext.current
                        val imageModel = PhotoStorageHelper.resolveImageModel(context, photo.localUri)
                        AsyncImage(
                            model = imageModel,
                            contentDescription = "Selectable photograph",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Selection Order Badge
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.25f))
                            )
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .align(Alignment.TopEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${orderIndex + 1}",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .height(56.dp)
                .testTag("continue_to_summary_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (selectedPhotoIdsInOrder.isNotEmpty()) "Continue to Summary" else "Skip Selection & View Summary",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SummaryStageContent(
    metrics: FamilyMemorySessionMetrics,
    trials: List<FamilyRecognitionTrial>,
    candidateAssociations: List<PhotoAssociation>,
    photosMap: Map<String, PhotoMemory>,
    peopleMap: Map<String, Person>,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Experiment Summary",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Observed behavioral responses from this session",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Non-medical Disclaimer Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Observed response data. Not a medical evaluation, neural diagnostic, or impairment assessment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Metrics Grid
            Text(
                text = "Observed Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Recognized",
                    value = String.format(Locale.getDefault(), "%.0f%%", metrics.recognitionRate * 100),
                    subtitle = "${metrics.recognizedCount} of ${metrics.totalTrials} photos",
                    color = MaterialTheme.colorScheme.primary
                )
                MetricSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Familiar",
                    value = String.format(Locale.getDefault(), "%.0f%%", metrics.familiarityRate * 100),
                    subtitle = "${metrics.familiarCount} photos",
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Not Sure / No Recall",
                    value = String.format(Locale.getDefault(), "%.0f%%", (metrics.uncertaintyRate + metrics.dontRememberRate) * 100),
                    subtitle = "${metrics.uncertainCount + metrics.dontRememberCount} photos",
                    color = MaterialTheme.colorScheme.tertiary
                )
                MetricSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Mean Response",
                    value = if (metrics.meanResponseTimeMs != null) {
                        String.format(Locale.getDefault(), "%.1fs", metrics.meanResponseTimeMs / 1000.0)
                    } else "—",
                    subtitle = "Reaction speed",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Candidate Associations Section
            if (candidateAssociations.isNotEmpty()) {
                Text(
                    text = "Candidate Memory Connections",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Candidate connections computed from your selection order and timing. You decide if these connections are meaningful.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    candidateAssociations.take(4).forEach { assoc ->
                        val pA = photosMap[assoc.photoAId]
                        val pB = photosMap[assoc.photoBId]
                        val personA = pA?.let { peopleMap[it.personId] }
                        val personB = pB?.let { peopleMap[it.personId] }

                        CandidateAssociationCard(
                            photoA = pA,
                            photoB = pB,
                            personAName = personA?.name ?: "Family Photo",
                            personBName = personB?.name ?: "Family Photo",
                            candidateScore = assoc.associationScore,
                            evidence = assoc.evidenceFeatures
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("save_and_finish_family_experiment_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Save Session & Return",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun MetricSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CandidateAssociationCard(
    photoA: PhotoMemory?,
    photoB: PhotoMemory?,
    personAName: String,
    personBName: String,
    candidateScore: Double,
    evidence: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Photo A thumbnail
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val context = LocalContext.current
                    photoA?.let {
                        AsyncImage(
                            model = PhotoStorageHelper.resolveImageModel(context, it.localUri),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                // Photo B thumbnail
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val context = LocalContext.current
                    photoB?.let {
                        AsyncImage(
                            model = PhotoStorageHelper.resolveImageModel(context, it.localUri),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$personAName ↔ $personBName",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Score: ${String.format(Locale.getDefault(), "%.2f", candidateScore)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = evidence,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
