package com.example.ui.screens.family

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Person
import com.example.data.model.PhotoMemory
import com.example.util.PhotoStorageHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotoViewerScreen(
    photos: List<PhotoMemory>,
    currentIndex: Int,
    person: Person,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDelete: (PhotoMemory) -> Unit,
    onSetAsCover: (PhotoMemory) -> Unit,
    onClose: () -> Unit
) {
    BackHandler {
        onClose()
    }

    if (photos.isEmpty() || currentIndex !in photos.indices) {
        onClose()
        return
    }

    val currentPhoto = photos[currentIndex]
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val dateCaption = remember(currentPhoto) {
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        if (currentPhoto.takenTimestamp != null && currentPhoto.takenTimestamp > 0) {
            "Captured: ${dateFormat.format(Date(currentPhoto.takenTimestamp))}"
        } else {
            "Added: ${dateFormat.format(Date(currentPhoto.addedTimestamp))}"
        }
    }

    val isCurrentCover = currentPhoto.localUri == person.coverPhotoUri

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("photo_viewer_screen"),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("viewer_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close photo viewer",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Photo ${currentIndex + 1} of ${photos.size}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("viewer_counter_text")
                )

                IconButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("viewer_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove photo from collection",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Center Fullscreen Photo
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                AsyncImage(
                    model = PhotoStorageHelper.resolveImageModel(context, currentPhoto.localUri),
                    contentDescription = "Memory photo of ${person.name}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("viewer_image")
                )
            }

            // Bottom Controls Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC1A1A1A))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Date Caption
                Text(
                    text = dateCaption,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("viewer_date_caption")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Buttons (Previous & Next)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 500.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onPrevious,
                        enabled = currentIndex > 0,
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("viewer_previous_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Previous",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (!isCurrentCover) {
                        TextButton(
                            onClick = { onSetAsCover(currentPhoto) },
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("viewer_set_cover_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Use as Cover",
                                color = MaterialTheme.colorScheme.primaryContainer,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Button(
                        onClick = onNext,
                        enabled = currentIndex < photos.size - 1,
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("viewer_next_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = Color.White.copy(alpha = 0.12f),
                            disabledContentColor = Color.White.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = "Next",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Confirmation Dialog before deletion
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = {
                    Text(
                        text = "Remove Photo?",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to remove this photo from ${person.name}'s memories?",
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 22.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            onDelete(currentPhoto)
                        },
                        modifier = Modifier.testTag("confirm_delete_photo_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Remove", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteConfirmDialog = false },
                        modifier = Modifier.testTag("cancel_delete_photo_button")
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    }
}
