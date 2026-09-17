package com.example.ui.screens.family

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.Person
import com.example.data.model.PhotoAssociation
import com.example.data.model.PhotoMemory
import com.example.data.model.SelectionCluster
import com.example.experiment.family.FamilyAssociationEngine
import com.example.util.PhotoStorageHelper
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MemoryMapScreen(
    allPhotos: List<PhotoMemory>,
    allPeople: List<Person>,
    allAssociations: List<PhotoAssociation>,
    allClusters: List<SelectionCluster>,
    manualConnectSourcePhoto: PhotoMemory?,
    activeMapInspectionAssociation: PhotoAssociation?,
    activeMapInspectionCluster: SelectionCluster?,
    isTimelineMode: Boolean,
    onSetTimelineMode: (Boolean) -> Unit,
    onInspectAssociation: (PhotoAssociation) -> Unit,
    onCloseAssociationInspection: () -> Unit,
    onInspectCluster: (SelectionCluster) -> Unit,
    onCloseClusterInspection: () -> Unit,
    onConfirmAssociation: (String) -> Unit,
    onRejectAssociation: (String) -> Unit,
    onRemoveAssociation: (String) -> Unit,
    onSaveAssociationNote: (associationId: String, textNote: String?, voiceNoteUri: String?) -> Unit,
    onSaveClusterMemory: (clusterId: String, textNote: String?, voiceNoteUri: String?, reflection: String?) -> Unit,
    onDeleteCluster: (SelectionCluster) -> Unit,
    onStartManualConnection: (PhotoMemory) -> Unit,
    onCancelManualConnection: () -> Unit,
    onCompleteManualConnection: (PhotoMemory, String?) -> Unit,
    onNavigateToPhotoExploration: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val peopleMap = remember(allPeople) { allPeople.associateBy { it.personId } }
    val photosMap = remember(allPhotos) { allPhotos.associateBy { it.photoId } }

    var manualTargetPhotoCandidate by remember { mutableStateOf<PhotoMemory?>(null) }
    var viewingFullPhoto by remember { mutableStateOf<PhotoMemory?>(null) }
    var isClustersListSheetOpen by remember { mutableStateOf(false) }

    // Map Zoom & Pan State
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .testTag("back_from_memory_map_button")
                                    .size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Return to Family Memory",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Memory Map",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${allPhotos.size} photos • ${allAssociations.count { it.userConfirmed }} confirmed • ${allAssociations.count { !it.userConfirmed }} candidate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (allClusters.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { isClustersListSheetOpen = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Collections,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clusters (${allClusters.size})")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // View Mode Tabs: [ Memory Map ] [ Timeline ]
                    TabRow(
                        selectedTabIndex = if (isTimelineMode) 1 else 0,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Tab(
                            selected = !isTimelineMode,
                            onClick = { onSetTimelineMode(false) },
                            text = { Text("🌿 Memory Map", fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.height(44.dp)
                        )
                        Tab(
                            selected = isTimelineMode,
                            onClick = { onSetTimelineMode(true) },
                            text = { Text("⏳ Timeline", fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.height(44.dp)
                        )
                    }
                }
            }

            // Manual Connection In-Progress Banner
            AnimatedVisibility(visible = manualConnectSourcePhoto != null) {
                manualConnectSourcePhoto?.let { firstPhoto ->
                    val person = peopleMap[firstPhoto.personId]
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "First photo selected (${person?.name ?: "Family"}). Tap another photo to connect memories.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(
                                onClick = onCancelManualConnection,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel connection mode",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Body: Empty State OR Main Map / Timeline View
            if (allPhotos.isEmpty() || (allAssociations.isEmpty() && allClusters.isEmpty())) {
                EmptyMemoryMapState(
                    hasPhotos = allPhotos.isNotEmpty(),
                    onExplore = onNavigateToPhotoExploration,
                    onStartManualConnect = {
                        if (allPhotos.size >= 2) {
                            onStartManualConnection(allPhotos.first())
                        }
                    }
                )
            } else {
                if (isTimelineMode) {
                    TimelineMemoryView(
                        photos = allPhotos,
                        peopleMap = peopleMap,
                        associations = allAssociations,
                        onInspectPhoto = { viewingFullPhoto = it },
                        onInspectAssociation = onInspectAssociation
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        // Graph Layout Container
                        GraphMemoryMapLayout(
                            allPhotos = allPhotos,
                            peopleMap = peopleMap,
                            associations = allAssociations,
                            manualConnectSourcePhoto = manualConnectSourcePhoto,
                            zoomScale = zoomScale,
                            panOffsetX = panOffsetX,
                            panOffsetY = panOffsetY,
                            onTransform = { panX, panY, zoom ->
                                panOffsetX += panX
                                panOffsetY += panY
                                zoomScale = (zoomScale * zoom).coerceIn(0.6f, 2.5f)
                            },
                            onPhotoClick = { photo ->
                                if (manualConnectSourcePhoto != null) {
                                    if (manualConnectSourcePhoto.photoId != photo.photoId) {
                                        manualTargetPhotoCandidate = photo
                                    }
                                } else {
                                    viewingFullPhoto = photo
                                }
                            },
                            onInspectAssociation = onInspectAssociation
                        )

                        // Floating Map Controls (Zoom +, Zoom -, Reset)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FloatingActionButton(
                                onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(2.5f) },
                                modifier = Modifier.size(44.dp),
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
                            }

                            FloatingActionButton(
                                onClick = { zoomScale = (zoomScale * 0.8f).coerceAtLeast(0.6f) },
                                modifier = Modifier.size(44.dp),
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
                            }

                            FloatingActionButton(
                                onClick = {
                                    zoomScale = 1.0f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                },
                                modifier = Modifier.size(44.dp),
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "Reset map view")
                            }
                        }

                        // Map Legend Banner at Bottom Left
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(4.dp)
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Confirmed Connection", style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "- - -",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Candidate Connection", style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Metaphorical memory map",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Active Association Inspection Dialog
    activeMapInspectionAssociation?.let { association ->
        val photoA = photosMap[association.photoAId]
        val photoB = photosMap[association.photoBId]
        val personA = photoA?.let { peopleMap[it.personId] }
        val personB = photoB?.let { peopleMap[it.personId] }

        ConnectionDetailDialog(
            association = association,
            photoA = photoA,
            photoB = photoB,
            personA = personA,
            personB = personB,
            onConfirm = onConfirmAssociation,
            onReject = onRejectAssociation,
            onRemove = onRemoveAssociation,
            onSaveNote = onSaveAssociationNote,
            onDismiss = onCloseAssociationInspection
        )
    }

    // Active Cluster Inspection Dialog
    activeMapInspectionCluster?.let { cluster ->
        val idRegex = Regex("\"([^\"]+)\"")
        val clusterPhotoIds = remember(cluster.selectedPhotoIds) {
            idRegex.findAll(cluster.selectedPhotoIds).map { it.groupValues[1] }.toList()
        }
        val clusterPhotos = clusterPhotoIds.mapNotNull { photosMap[it] }

        MemoryClusterViewDialog(
            cluster = cluster,
            photos = clusterPhotos,
            peopleMap = peopleMap,
            onSaveMemory = onSaveClusterMemory,
            onDeleteCluster = onDeleteCluster,
            onDismiss = onCloseClusterInspection
        )
    }

    // Manual Connection Confirmation Dialog
    if (manualConnectSourcePhoto != null && manualTargetPhotoCandidate != null) {
        val sourcePhoto = manualConnectSourcePhoto
        val targetPhoto = manualTargetPhotoCandidate!!
        val personA = peopleMap[sourcePhoto.personId]
        val personB = peopleMap[targetPhoto.personId]

        ManualConnectDialog(
            photoA = sourcePhoto,
            photoB = targetPhoto,
            personA = personA,
            personB = personB,
            onConfirmConnection = { note ->
                onCompleteManualConnection(targetPhoto, note)
                manualTargetPhotoCandidate = null
            },
            onDismiss = { manualTargetPhotoCandidate = null }
        )
    }

    // Single Photo Action / Full View Dialog
    viewingFullPhoto?.let { photo ->
        val person = peopleMap[photo.personId]
        val connectedAssociations = allAssociations.filter {
            it.photoAId == photo.photoId || it.photoBId == photo.photoId
        }

        PhotoViewerDialog(
            photo = photo,
            person = person,
            connectedAssociations = connectedAssociations,
            photosMap = photosMap,
            peopleMap = peopleMap,
            onStartConnect = {
                onStartManualConnection(photo)
                viewingFullPhoto = null
            },
            onInspectAssociation = { assoc ->
                viewingFullPhoto = null
                onInspectAssociation(assoc)
            },
            onDismiss = { viewingFullPhoto = null }
        )
    }

    // Memory Clusters List Sheet
    if (isClustersListSheetOpen) {
        ClustersListDialog(
            clusters = allClusters,
            photosMap = photosMap,
            peopleMap = peopleMap,
            onSelectCluster = { cluster ->
                isClustersListSheetOpen = false
                onInspectCluster(cluster)
            },
            onDismiss = { isClustersListSheetOpen = false }
        )
    }
}

/**
 * Organic Graph Layout with Bezier connections between photo nodes grouped by person.
 */
@Composable
private fun GraphMemoryMapLayout(
    allPhotos: List<PhotoMemory>,
    peopleMap: Map<String, Person>,
    associations: List<PhotoAssociation>,
    manualConnectSourcePhoto: PhotoMemory?,
    zoomScale: Float,
    panOffsetX: Float,
    panOffsetY: Float,
    onTransform: (panX: Float, panY: Float, zoom: Float) -> Unit,
    onPhotoClick: (PhotoMemory) -> Unit,
    onInspectAssociation: (PhotoAssociation) -> Unit
) {
    val context = LocalContext.current
    val nodePositions = remember { mutableStateMapOf<String, Offset>() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Group photos by person
    val photosByPerson = remember(allPhotos) {
        allPhotos.groupBy { it.personId }
    }

    // Count connections for each photo
    val connectionCounts = remember(associations) {
        val map = mutableMapOf<String, Int>()
        for (a in associations) {
            map[a.photoAId] = (map[a.photoAId] ?: 0) + 1
            map[a.photoBId] = (map[a.photoBId] ?: 0) + 1
        }
        map
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onTransform(pan.x, pan.y, zoom)
                }
            }
            .graphicsLayer {
                scaleX = zoomScale
                scaleY = zoomScale
                translationX = panOffsetX
                translationY = panOffsetY
            }
    ) {
        // 1. Draw Organic Connection Curves in background Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (assoc in associations) {
                val posA = nodePositions[assoc.photoAId]
                val posB = nodePositions[assoc.photoBId]
                if (posA != null && posB != null) {
                    val path = Path().apply {
                        moveTo(posA.x, posA.y)
                        // Smooth cubic curve with organic bend
                        val dx = posB.x - posA.x
                        val dy = posB.y - posA.y
                        val cx1 = posA.x + dx * 0.25f
                        val cy1 = posA.y + dy * 0.75f + (dx * 0.15f)
                        val cx2 = posA.x + dx * 0.75f
                        val cy2 = posB.y - dy * 0.25f - (dx * 0.15f)
                        cubicTo(cx1, cy1, cx2, cy2, posB.x, posB.y)
                    }

                    if (assoc.userConfirmed) {
                        // Strong persistent connection
                        drawPath(
                            path = path,
                            color = primaryColor.copy(alpha = 0.85f),
                            style = Stroke(
                                width = 4.5f,
                                cap = StrokeCap.Round
                            )
                        )
                    } else {
                        // Subtle dashed candidate connection
                        drawPath(
                            path = path,
                            color = tertiaryColor.copy(alpha = 0.75f),
                            style = Stroke(
                                width = 2.5f,
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                            )
                        )
                    }
                }
            }
        }

        // 2. Render Person Lanes & Photo Nodes
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(36.dp)
        ) {
            photosByPerson.forEach { (personId, personPhotos) ->
                val person = peopleMap[personId]
                PersonGroupNodeRow(
                    person = person,
                    photos = personPhotos,
                    manualConnectSourcePhoto = manualConnectSourcePhoto,
                    connectionCounts = connectionCounts,
                    onNodePositioned = { photoId, centerOffset ->
                        nodePositions[photoId] = centerOffset
                    },
                    onPhotoClick = onPhotoClick
                )
            }

            // Connections Quick-Access Cards at bottom of canvas
            if (associations.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Memory Connections (${associations.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap any connection below or inspect photos above",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(associations, key = { it.associationId }) { assoc ->
                                ConnectionSummaryChip(
                                    association = assoc,
                                    peopleMap = peopleMap,
                                    allPhotos = allPhotos,
                                    onClick = { onInspectAssociation(assoc) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun PersonGroupNodeRow(
    person: Person?,
    photos: List<PhotoMemory>,
    manualConnectSourcePhoto: PhotoMemory?,
    connectionCounts: Map<String, Int>,
    onNodePositioned: (String, Offset) -> Unit,
    onPhotoClick: (PhotoMemory) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Person Header Node
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = person?.name?.take(1)?.uppercase() ?: "F",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 18.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = person?.name ?: "Family Member",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${photos.size} memories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row of Photo Nodes for this person
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                items(photos, key = { it.photoId }) { photo ->
                    PhotoNodeItem(
                        photo = photo,
                        person = person,
                        isFirstManualSelection = manualConnectSourcePhoto?.photoId == photo.photoId,
                        connectionCount = connectionCounts[photo.photoId] ?: 0,
                        onPositioned = { offset ->
                            onNodePositioned(photo.photoId, offset)
                        },
                        onClick = { onPhotoClick(photo) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoNodeItem(
    photo: PhotoMemory,
    person: Person?,
    isFirstManualSelection: Boolean,
    connectionCount: Int,
    onPositioned: (Offset) -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val borderColor = if (isFirstManualSelection) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier
            .width(136.dp)
            .onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInRoot()
                val center = Offset(pos.x + coordinates.size.width / 2f, pos.y + coordinates.size.height / 2f)
                onPositioned(center)
            }
            .clickable(onClick = onClick)
            .testTag("photo_node_${photo.photoId}"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (isFirstManualSelection) 3.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFirstManualSelection) 6.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
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

                // Connection Count Badge Overlay
                if (connectionCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$connectionCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                if (isFirstManualSelection) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "First Photo",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = FamilyAssociationEngine.formatPhotoDate(photo),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConnectionSummaryChip(
    association: PhotoAssociation,
    peopleMap: Map<String, Person>,
    allPhotos: List<PhotoMemory>,
    onClick: () -> Unit
) {
    val photoA = remember(association.photoAId) { allPhotos.find { it.photoId == association.photoAId } }
    val photoB = remember(association.photoBId) { allPhotos.find { it.photoId == association.photoBId } }
    val personA = photoA?.let { peopleMap[it.personId] }
    val personB = photoB?.let { peopleMap[it.personId] }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (association.userConfirmed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (association.userConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.height(42.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (association.userConfirmed) Icons.Default.CheckCircle else Icons.Default.HelpOutline,
                contentDescription = null,
                tint = if (association.userConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${personA?.name ?: "Photo"} ↔ ${personB?.name ?: "Photo"}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Chronological Timeline View organizing photos by year.
 */
@Composable
private fun TimelineMemoryView(
    photos: List<PhotoMemory>,
    peopleMap: Map<String, Person>,
    associations: List<PhotoAssociation>,
    onInspectPhoto: (PhotoMemory) -> Unit,
    onInspectAssociation: (PhotoAssociation) -> Unit
) {
    val context = LocalContext.current

    // Group photos by Year
    val photosByYear = remember(photos) {
        val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
        photos.groupBy { photo ->
            when {
                photo.takenTimestamp != null -> yearFormat.format(Date(photo.takenTimestamp))
                photo.addedTimestamp > 0L -> yearFormat.format(Date(photo.addedTimestamp))
                else -> "Undated"
            }
        }.toSortedMap(compareByDescending { it })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Educational Disclaimer Banner
        item(key = "timeline_disclaimer") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
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
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Proximity in time does not prove photos belong to the same event.",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        photosByYear.forEach { (year, yearPhotos) ->
            item(key = "year_header_$year") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = year,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "${yearPhotos.size} photos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(yearPhotos, key = { it.photoId }) { photo ->
                val person = peopleMap[photo.personId]
                val relatedAssocs = associations.filter {
                    it.photoAId == photo.photoId || it.photoBId == photo.photoId
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onInspectPhoto(photo) },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
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

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = person?.name ?: "Family Member",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = FamilyAssociationEngine.formatPhotoDate(photo),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (relatedAssocs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "🔗 Connected to ${relatedAssocs.size} memories",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty State for Memory Map.
 */
@Composable
private fun EmptyMemoryMapState(
    hasPhotos: Boolean,
    onExplore: () -> Unit,
    onStartManualConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Your Memory Map is still growing",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Explore photos and select memories that feel familiar or connected. As you explore, potential associations will blossom here for you to verify.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 440.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onExplore,
            modifier = Modifier
                .widthIn(min = 220.dp)
                .height(52.dp)
                .testTag("explore_photos_empty_state_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Explore, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Explore Family Photos", fontWeight = FontWeight.Bold)
        }

        if (hasPhotos) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onStartManualConnect,
                modifier = Modifier
                    .widthIn(min = 220.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manually Connect Photos")
            }
        }
    }
}

/**
 * Dialog to view single photo details and connect options.
 */
@Composable
private fun PhotoViewerDialog(
    photo: PhotoMemory,
    person: Person?,
    connectedAssociations: List<PhotoAssociation>,
    photosMap: Map<String, PhotoMemory>,
    peopleMap: Map<String, Person>,
    onStartConnect: () -> Unit,
    onInspectAssociation: (PhotoAssociation) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 500.dp)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = person?.name ?: "Family Photo",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = FamilyAssociationEngine.formatPhotoDate(photo),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close photo viewer")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val imageModel: Any? = remember(photo.localUri) {
                        PhotoStorageHelper.resolveImageModel(context, photo.localUri)
                    }
                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = "Full photo of ${person?.name ?: "Family"}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action: Connect to another photo
                Button(
                    onClick = onStartConnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("connect_memory_from_photo_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect to Another Photo")
                }

                // Linked Connections list
                if (connectedAssociations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Connections (${connectedAssociations.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    connectedAssociations.forEach { assoc ->
                        val otherId = if (assoc.photoAId == photo.photoId) assoc.photoBId else assoc.photoAId
                        val otherPhoto = photosMap[otherId]
                        val otherPerson = otherPhoto?.let { peopleMap[it.personId] }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onInspectAssociation(assoc) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (assoc.userConfirmed) Icons.Default.CheckCircle else Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = if (assoc.userConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connected with ${otherPerson?.name ?: "Photo"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog to browse all multi-photo clusters.
 */
@Composable
private fun ClustersListDialog(
    clusters: List<SelectionCluster>,
    photosMap: Map<String, PhotoMemory>,
    peopleMap: Map<String, Person>,
    onSelectCluster: (SelectionCluster) -> Unit,
    onDismiss: () -> Unit
) {
    val idRegex = Regex("\"([^\"]+)\"")
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 540.dp)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Memory Clusters",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close clusters dialog")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(clusters, key = { it.clusterId }) { cluster ->
                        val photoIds = idRegex.findAll(cluster.selectedPhotoIds).map { it.groupValues[1] }.toList()
                        val clusterPhotos = photoIds.mapNotNull { photosMap[it] }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCluster(cluster) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${clusterPhotos.size} Photos in Cluster",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = FamilyAssociationEngine.formatDateOnly(cluster.createdAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (!cluster.userReflection.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Reflection: ${cluster.userReflection}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (!cluster.userNote.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "\"${cluster.userNote}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
