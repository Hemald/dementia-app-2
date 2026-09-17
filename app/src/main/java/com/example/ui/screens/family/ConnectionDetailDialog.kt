package com.example.ui.screens.family

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.Person
import com.example.data.model.PhotoAssociation
import com.example.data.model.PhotoMemory
import com.example.experiment.family.FamilyAssociationEngine
import com.example.util.AudioMemoryHelper
import com.example.util.PhotoStorageHelper
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale

@Composable
fun ConnectionDetailDialog(
    association: PhotoAssociation,
    photoA: PhotoMemory?,
    photoB: PhotoMemory?,
    personA: Person?,
    personB: Person?,
    onConfirm: (String) -> Unit,
    onReject: (String) -> Unit,
    onRemove: (String) -> Unit,
    onSaveNote: (associationId: String, textNote: String?, voiceNoteUri: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isEditingNote by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf(association.userNote ?: "") }
    var currentVoiceUri by remember { mutableStateOf(association.voiceNoteUri) }

    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingDurationSeconds by remember { mutableIntStateOf(0) }
    var isPlayingAudio by remember { mutableStateOf(false) }

    // Audio recording timer
    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            recordingDurationSeconds = 0
            while (isRecordingAudio) {
                delay(1000)
                recordingDurationSeconds++
            }
        }
    }

    // Permission launcher for microphone
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = AudioMemoryHelper.startRecording(context)
            isRecordingAudio = file != null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AudioMemoryHelper.stopRecording()
            AudioMemoryHelper.stopPlayback()
        }
    }

    val reasons = remember(association.evidenceFeatures) {
        parseEvidenceReasons(association.evidenceFeatures)
    }

    Dialog(
        onDismissRequest = {
            AudioMemoryHelper.stopRecording()
            AudioMemoryHelper.stopPlayback()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (association.userConfirmed) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (association.userConfirmed) Icons.Default.CheckCircle else Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = if (association.userConfirmed) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (association.userConfirmed) "Confirmed Connection" else "Possible Connection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (association.userConfirmed) "Verified by you" else "Suggested for your review",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            AudioMemoryHelper.stopRecording()
                            AudioMemoryHelper.stopPlayback()
                            onDismiss()
                        },
                        modifier = Modifier
                            .testTag("close_connection_detail_button")
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close connection inspection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Photos Pair Visual
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConnectionPhotoItem(
                        photo = photoA,
                        person = personA,
                        label = "Photo A"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "🔗",
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.getDefault(), "Score: %.2f", association.associationScore),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    ConnectionPhotoItem(
                        photo = photoB,
                        person = personB,
                        label = "Photo B"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Evidence Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Observed Evidence:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (reasons.isEmpty()) {
                            Text(
                                text = if (association.associationScore < 0.2) "• Insufficient metadata evidence" else "• Potential connection based on overall collection patterns",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            reasons.forEach { reason ->
                                Text(
                                    text = "• $reason",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mandatory Disclosure Statement
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "This is a possible association based on available information. It does not prove that the photos belong to the same event or memory.",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // User Memory Note Section (Voice / Text)
                if (association.userConfirmed) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Your Memory Notes",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                if (!isEditingNote && (currentVoiceUri != null || noteText.isNotBlank())) {
                                    IconButton(
                                        onClick = { isEditingNote = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit memory notes",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (isEditingNote || (currentVoiceUri == null && noteText.isBlank())) {
                                // Editing / Adding Note
                                OutlinedTextField(
                                    value = noteText,
                                    onValueChange = { noteText = it },
                                    label = { Text("Write a reflection or memory...") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("connection_text_note_input"),
                                    minLines = 2,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Voice Recording Controls
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (isRecordingAudio) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Recording... ${recordingDurationSeconds}s",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                val savedPath = AudioMemoryHelper.stopRecording()
                                                isRecordingAudio = false
                                                if (!savedPath.isNullOrBlank()) {
                                                    currentVoiceUri = savedPath
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Stop, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Stop")
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                val hasPermission = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED

                                                if (hasPermission) {
                                                    val file = AudioMemoryHelper.startRecording(context)
                                                    isRecordingAudio = file != null
                                                } else {
                                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (currentVoiceUri != null) "Re-record Voice" else "Record Voice Note")
                                        }

                                        if (currentVoiceUri != null) {
                                            IconButton(
                                                onClick = {
                                                    AudioMemoryHelper.deleteVoiceNote(currentVoiceUri)
                                                    currentVoiceUri = null
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete voice recording",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (isEditingNote && (association.userNote != null || association.voiceNoteUri != null)) {
                                        OutlinedButton(
                                            onClick = {
                                                noteText = association.userNote ?: ""
                                                currentVoiceUri = association.voiceNoteUri
                                                isEditingNote = false
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Cancel")
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }

                                    Button(
                                        onClick = {
                                            onSaveNote(
                                                association.associationId,
                                                noteText.trim().ifBlank { null },
                                                currentVoiceUri
                                            )
                                            isEditingNote = false
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Save Memory")
                                    }
                                }
                            } else {
                                // Displaying Saved Note
                                if (noteText.isNotBlank()) {
                                    Text(
                                        text = "\"$noteText\"",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                currentVoiceUri?.let { voiceUri ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Button(
                                            onClick = {
                                                if (isPlayingAudio) {
                                                    AudioMemoryHelper.stopPlayback()
                                                    isPlayingAudio = false
                                                } else {
                                                    isPlayingAudio = AudioMemoryHelper.playVoiceNote(
                                                        filePath = voiceUri,
                                                        onComplete = { isPlayingAudio = false },
                                                        onError = { isPlayingAudio = false }
                                                    )
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingAudio) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = null
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isPlayingAudio) "Stop Voice Memory" else "▶ Play Voice Memory")
                                        }

                                        IconButton(
                                            onClick = {
                                                AudioMemoryHelper.deleteVoiceNote(voiceUri)
                                                currentVoiceUri = null
                                                onSaveNote(association.associationId, noteText.trim().ifBlank { null }, null)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete voice memory",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Action Buttons Row
                if (association.userConfirmed) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                AudioMemoryHelper.stopPlayback()
                                AudioMemoryHelper.stopRecording()
                                onRemove(association.associationId)
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Remove Connection")
                        }

                        Button(
                            onClick = {
                                AudioMemoryHelper.stopPlayback()
                                AudioMemoryHelper.stopRecording()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Done")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                AudioMemoryHelper.stopPlayback()
                                AudioMemoryHelper.stopRecording()
                                onReject(association.associationId)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Dismiss Candidate")
                        }

                        Button(
                            onClick = {
                                AudioMemoryHelper.stopPlayback()
                                AudioMemoryHelper.stopRecording()
                                onConfirm(association.associationId)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("confirm_association_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Confirm Connection")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionPhotoItem(
    photo: PhotoMemory?,
    person: Person?,
    label: String
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.width(130.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(114.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (photo != null) {
                    val imageModel: Any? = remember(photo.localUri) {
                        PhotoStorageHelper.resolveImageModel(context, photo.localUri)
                    }
                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = "Photo of ${person?.name ?: "Family"}",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = person?.name ?: "Family Member",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = photo?.let { FamilyAssociationEngine.formatPhotoDate(it) } ?: "Unknown date",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun parseEvidenceReasons(evidenceFeatures: String): List<String> {
    return try {
        val json = JSONObject(evidenceFeatures)
        if (json.has("reasons")) {
            val arr = json.getJSONArray("reasons")
            (0 until arr.length()).map { arr.getString(it) }
        } else {
            emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}
