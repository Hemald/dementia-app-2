package com.example.experiment.family

import com.example.data.model.Person
import com.example.data.model.PhotoAssociation
import com.example.data.model.PhotoMemory
import com.example.data.model.SelectionCluster
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * Data holder for a candidate connection between a viewed photo and another family member's photo.
 *
 * NOTE: Association Strength is a deterministic mathematical similarity score based on
 * available timestamps, metadata, and user interactions. It is NOT a medical diagnosis,
 * probability of autobiographical memory, or neural pathway.
 */
data class CandidateConnection(
    val targetPhoto: PhotoMemory,
    val candidatePhoto: PhotoMemory,
    val targetPerson: Person,
    val candidatePerson: Person,
    val associationScore: Double,
    val evidenceFeatures: String,
    val evidenceReasons: List<String>,
    val source: String
)

/**
 * Observable user photo selection in an exploration session.
 */
data class ExplorationSelection(
    val photo: PhotoMemory,
    val person: Person,
    val selectedAt: Long = System.currentTimeMillis(),
    val selectionOrder: Int
)

/**
 * Detailed pairwise association result within a selection cluster.
 */
data class PairwiseAssociationResult(
    val photoA: PhotoMemory,
    val photoB: PhotoMemory,
    val personA: Person,
    val personB: Person,
    val associationScore: Double,
    val evidenceFeatures: String,
    val evidenceReasons: List<String>,
    val source: String,
    val strengthLabel: String,
    val isRepeatedSelection: Boolean,
    val repeatedCount: Int
)

/**
 * Local deterministic association engine for Phase 3 Exploratory Family Memory.
 * Computes cross-person candidate associations and multi-photo cluster relationships.
 */
object FamilyAssociationEngine {
    const val ALGORITHM_VERSION = "3.0-exploratory"

    // Documented deterministic scoring weights
    private const val WEIGHT_SELECTED_IN_CLUSTER = 0.20
    private const val WEIGHT_ADJACENT_SELECTION_ORDER = 0.15
    private const val WEIGHT_NEARBY_SELECTION_ORDER = 0.10
    private const val WEIGHT_RAPID_SELECTION = 0.10
    private const val WEIGHT_SAME_DAY = 0.35
    private const val WEIGHT_7_DAYS = 0.25
    private const val WEIGHT_SAME_MONTH = 0.18
    private const val WEIGHT_SAME_YEAR = 0.10
    private const val WEIGHT_SAME_SEASON = 0.08
    private const val WEIGHT_SHARED_LOCATION = 0.20
    private const val WEIGHT_PER_REPEATED_SESSION = 0.12
    private const val MAX_REPEATED_WEIGHT = 0.36

    /**
     * Finds photos belonging to other family members that have temporal or metadata
     * similarity with the given photo.
     */
    fun findCrossPersonCandidates(
        targetPhoto: PhotoMemory,
        targetPerson: Person,
        allOtherPhotos: List<PhotoMemory>,
        peopleMap: Map<String, Person>,
        historicalClusters: List<SelectionCluster> = emptyList(),
        minScoreThreshold: Double = 0.20
    ): List<CandidateConnection> {
        val candidates = mutableListOf<CandidateConnection>()

        // Filter out photos belonging to the same person
        val crossPersonPhotos = allOtherPhotos.filter { it.personId != targetPerson.personId }

        for (candidatePhoto in crossPersonPhotos) {
            val candidatePerson = peopleMap[candidatePhoto.personId] ?: continue

            val evidenceReasons = mutableListOf<String>()
            val evidenceFeaturesList = mutableListOf<String>()
            var score = 0.0
            var source = "temporal"

            // 1. Capture time proximity (if EXIF taken_timestamp exists on both)
            val takenTarget = targetPhoto.takenTimestamp
            val takenCandidate = candidatePhoto.takenTimestamp

            if (takenTarget != null && takenCandidate != null) {
                val daysDiff = abs(takenTarget - takenCandidate) / (1000.0 * 60 * 60 * 24)
                val calA = Calendar.getInstance().apply { timeInMillis = takenTarget }
                val calB = Calendar.getInstance().apply { timeInMillis = takenCandidate }

                val sameDay = calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
                        calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
                val sameMonth = calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
                        calA.get(Calendar.MONTH) == calB.get(Calendar.MONTH)
                val sameYear = calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR)
                val seasonA = calA.get(Calendar.MONTH) / 3
                val seasonB = calB.get(Calendar.MONTH) / 3
                val sameSeason = sameYear && seasonA == seasonB

                when {
                    sameDay -> {
                        score += WEIGHT_SAME_DAY
                        evidenceReasons.add("Captured on the same day (${formatDateOnly(takenTarget)})")
                        evidenceFeaturesList.add("same_capture_date")
                    }
                    daysDiff <= 7.0 -> {
                        score += WEIGHT_7_DAYS
                        val daysFormatted = String.format(Locale.US, "%.0f", daysDiff)
                        evidenceReasons.add("Taken close together (approx. $daysFormatted days apart)")
                        evidenceFeaturesList.add("close_capture_time_7_days")
                    }
                    sameMonth -> {
                        score += WEIGHT_SAME_MONTH
                        evidenceReasons.add("Both captured in ${formatMonthYear(takenTarget)}")
                        evidenceFeaturesList.add("same_capture_month")
                    }
                    sameSeason -> {
                        score += WEIGHT_SAME_SEASON
                        evidenceReasons.add("Captured during the same season in ${calA.get(Calendar.YEAR)}")
                        evidenceFeaturesList.add("same_season")
                    }
                    sameYear -> {
                        score += WEIGHT_SAME_YEAR
                        evidenceReasons.add("Both captured in ${calA.get(Calendar.YEAR)}")
                        evidenceFeaturesList.add("same_capture_year")
                    }
                }
            }

            // 2. Shared location metadata (if available)
            val locA = targetPhoto.optionalLocation?.trim()
            val locB = candidatePhoto.optionalLocation?.trim()
            if (!locA.isNullOrBlank() && !locB.isNullOrBlank()) {
                if (locA.equals(locB, ignoreCase = true)) {
                    score += WEIGHT_SHARED_LOCATION
                    evidenceReasons.add("Shared location metadata ($locA)")
                    evidenceFeaturesList.add("shared_location")
                    source = if (score > WEIGHT_SHARED_LOCATION) "combined" else "metadata"
                }
            }

            // 3. Repeated co-selection across prior sessions
            val repeatedCount = countHistoricalCoSelections(
                targetPhoto.photoId,
                candidatePhoto.photoId,
                historicalClusters
            )
            if (repeatedCount > 0) {
                val repeatedWeight = (WEIGHT_PER_REPEATED_SESSION * repeatedCount).coerceAtMost(MAX_REPEATED_WEIGHT)
                score += repeatedWeight
                evidenceReasons.add("Repeated association: selected together in $repeatedCount prior session(s)")
                evidenceFeaturesList.add("repeated_selection_$repeatedCount")
                source = "combined"
            }

            val finalScore = score.coerceIn(0.0, 1.0)
            if (finalScore >= minScoreThreshold) {
                candidates.add(
                    CandidateConnection(
                        targetPhoto = targetPhoto,
                        candidatePhoto = candidatePhoto,
                        targetPerson = targetPerson,
                        candidatePerson = candidatePerson,
                        associationScore = finalScore,
                        evidenceFeatures = evidenceFeaturesList.joinToString("; "),
                        evidenceReasons = evidenceReasons,
                        source = source
                    )
                )
            }
        }

        // Return candidates sorted by association score descending
        return candidates.sortedByDescending { it.associationScore }
    }

    /**
     * Calculates pairwise association evidence and scores for all pairs within a selection cluster.
     */
    fun computeClusterAssociations(
        selections: List<ExplorationSelection>,
        historicalClusters: List<SelectionCluster> = emptyList()
    ): List<PairwiseAssociationResult> {
        if (selections.size < 2) return emptyList()

        val results = mutableListOf<PairwiseAssociationResult>()
        val count = selections.size

        for (i in 0 until count) {
            for (j in (i + 1) until count) {
                val selA = selections[i]
                val selB = selections[j]
                val photoA = selA.photo
                val photoB = selB.photo

                val evidenceReasons = mutableListOf<String>()
                val featuresList = mutableListOf<String>()
                var score = 0.0

                // 1. Both selected together in this cluster
                score += WEIGHT_SELECTED_IN_CLUSTER
                featuresList.add("selected_together")

                // 2. Selection order proximity
                val orderDiff = abs(selA.selectionOrder - selB.selectionOrder)
                if (orderDiff == 1) {
                    score += WEIGHT_ADJACENT_SELECTION_ORDER
                    featuresList.add("adjacent_selection_order")
                    evidenceReasons.add("Selected consecutively (#${selA.selectionOrder} and #${selB.selectionOrder})")
                } else if (orderDiff <= 2) {
                    score += WEIGHT_NEARBY_SELECTION_ORDER
                    featuresList.add("close_selection_order")
                }

                // 3. Selection time interval
                val timeDiffSec = abs(selA.selectedAt - selB.selectedAt) / 1000.0
                if (timeDiffSec <= 15.0) {
                    score += WEIGHT_RAPID_SELECTION
                    featuresList.add("rapid_selection_${String.format(Locale.US, "%.1fs", timeDiffSec)}")
                }

                // 4. Directory & Person context
                val isCrossPerson = selA.person.personId != selB.person.personId
                if (isCrossPerson) {
                    featuresList.add("cross_person_cluster")
                    evidenceReasons.add("Cross-person connection (${selA.person.name} & ${selB.person.name})")
                } else {
                    featuresList.add("same_person_directory")
                    evidenceReasons.add("From ${selA.person.name}'s photo collection")
                }

                // 5. Temporal similarity from EXIF
                val takenA = photoA.takenTimestamp
                val takenB = photoB.takenTimestamp
                if (takenA != null && takenB != null) {
                    val daysDiff = abs(takenA - takenB) / (1000.0 * 60 * 60 * 24)
                    val calA = Calendar.getInstance().apply { timeInMillis = takenA }
                    val calB = Calendar.getInstance().apply { timeInMillis = takenB }

                    val sameDay = calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
                            calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
                    val sameMonth = calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
                            calA.get(Calendar.MONTH) == calB.get(Calendar.MONTH)
                    val sameYear = calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR)

                    when {
                        sameDay -> {
                            score += WEIGHT_SAME_DAY
                            featuresList.add("same_capture_date")
                            evidenceReasons.add("Captured on the same day (${formatDateOnly(takenA)})")
                        }
                        daysDiff <= 7.0 -> {
                            score += WEIGHT_7_DAYS
                            featuresList.add("close_capture_time_7_days")
                            evidenceReasons.add("Captured within ${String.format(Locale.US, "%.0f", daysDiff)} days of each other")
                        }
                        sameMonth -> {
                            score += WEIGHT_SAME_MONTH
                            featuresList.add("same_capture_month")
                            evidenceReasons.add("Both captured in ${formatMonthYear(takenA)}")
                        }
                        sameYear -> {
                            score += WEIGHT_SAME_YEAR
                            featuresList.add("same_capture_year")
                            evidenceReasons.add("Both captured in ${calA.get(Calendar.YEAR)}")
                        }
                    }
                }

                // 6. Location metadata
                val locA = photoA.optionalLocation?.trim()
                val locB = photoB.optionalLocation?.trim()
                if (!locA.isNullOrBlank() && !locB.isNullOrBlank() && locA.equals(locB, ignoreCase = true)) {
                    score += WEIGHT_SHARED_LOCATION
                    featuresList.add("shared_location")
                    evidenceReasons.add("Shared location ($locA)")
                }

                // 7. Repeated co-selection across prior sessions
                val repeatedCount = countHistoricalCoSelections(photoA.photoId, photoB.photoId, historicalClusters)
                if (repeatedCount > 0) {
                    val repeatedWeight = (WEIGHT_PER_REPEATED_SESSION * repeatedCount).coerceAtMost(MAX_REPEATED_WEIGHT)
                    score += repeatedWeight
                    featuresList.add("repeated_selection_$repeatedCount")
                    evidenceReasons.add("Repeated association: co-selected in $repeatedCount previous session(s)")
                }

                val finalScore = score.coerceIn(0.0, 1.0)
                val source = when {
                    repeatedCount > 0 && (takenA != null || locA != null) -> "combined"
                    repeatedCount > 0 -> "repeated_selection"
                    takenA != null -> "temporal"
                    locA != null -> "metadata"
                    else -> "combined"
                }

                val strengthLabel = when {
                    finalScore >= 0.65 -> "Strong"
                    finalScore >= 0.35 -> "Moderate"
                    else -> "Insufficient Evidence"
                }

                results.add(
                    PairwiseAssociationResult(
                        photoA = photoA,
                        photoB = photoB,
                        personA = selA.person,
                        personB = selB.person,
                        associationScore = finalScore,
                        evidenceFeatures = featuresList.joinToString("; "),
                        evidenceReasons = evidenceReasons,
                        source = source,
                        strengthLabel = strengthLabel,
                        isRepeatedSelection = repeatedCount > 0,
                        repeatedCount = repeatedCount
                    )
                )
            }
        }

        return results.sortedByDescending { it.associationScore }
    }

    /**
     * Converts calculated pairwise association results into storable Room PhotoAssociation entities.
     */
    fun createPhotoAssociations(
        clusterId: String,
        pairwiseResults: List<PairwiseAssociationResult>,
        userConfirmed: Boolean,
        userResponse: String?
    ): List<PhotoAssociation> {
        return pairwiseResults.map { result ->
            PhotoAssociation(
                associationId = UUID.randomUUID().toString(),
                clusterId = clusterId,
                photoAId = result.photoA.photoId,
                photoBId = result.photoB.photoId,
                personAId = result.personA.personId,
                personBId = result.personB.personId,
                evidenceFeatures = result.evidenceFeatures,
                associationScore = result.associationScore,
                algorithmVersion = ALGORITHM_VERSION,
                createdAt = System.currentTimeMillis(),
                source = result.source,
                userConfirmed = userConfirmed,
                userResponse = userResponse
            )
        }
    }

    private fun countHistoricalCoSelections(
        photoAId: String,
        photoBId: String,
        historicalClusters: List<SelectionCluster>
    ): Int {
        var count = 0
        val idRegex = Regex("\"([^\"]+)\"")
        for (cluster in historicalClusters) {
            try {
                val raw = cluster.selectedPhotoIds
                val idSet = idRegex.findAll(raw).map { it.groupValues[1] }.toSet()
                if (idSet.contains(photoAId) && idSet.contains(photoBId)) {
                    count++
                }
            } catch (_: Exception) {}
        }
        return count
    }

    fun formatPhotoDate(photo: PhotoMemory): String {
        return when {
            photo.takenTimestamp != null -> formatDateOnly(photo.takenTimestamp)
            photo.addedTimestamp > 0L -> "Added ${formatDateOnly(photo.addedTimestamp)}"
            else -> "Date unknown"
        }
    }

    fun formatDateOnly(timestamp: Long): String {
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }

    fun formatMonthYear(timestamp: Long): String {
        return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
