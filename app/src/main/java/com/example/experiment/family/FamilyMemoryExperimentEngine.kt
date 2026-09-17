package com.example.experiment.family

import com.example.data.model.FamilyRecognitionTrial
import com.example.data.model.Person
import com.example.data.model.PhotoAssociation
import com.example.data.model.PhotoMemory
import com.example.data.model.SelectionCluster
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Observable session metrics for a Family Memory Recognition experiment session.
 *
 * NOTE: All metrics represent observable behavioral responses and timing.
 * They do NOT represent a neurological measurement, brain score, or medical diagnosis.
 */
data class FamilyMemorySessionMetrics(
    val totalTrials: Int,
    val completedTrials: Int,
    val recognizedCount: Int,
    val familiarCount: Int,
    val uncertainCount: Int,
    val dontRememberCount: Int,
    val skippedCount: Int,

    val recognitionRate: Double,
    val familiarityRate: Double,
    val uncertaintyRate: Double,
    val dontRememberRate: Double,
    val skipRate: Double,

    // Recall metrics
    val recallYesCount: Int,
    val recallALittleCount: Int,
    val recallNotSureCount: Int,
    val recallNoCount: Int,
    val recallSkippedCount: Int,

    val recallYesRate: Double,
    val recallALittleRate: Double,
    val recallNotSureRate: Double,
    val recallNoRate: Double,

    // Timing metrics (in milliseconds)
    val meanResponseTimeMs: Double,
    val medianResponseTimeMs: Double,
    val responseTimeVariabilityMs: Double,
    val fastestResponseMs: Long?,
    val slowestResponseMs: Long?,

    // Selection metrics
    val selectedPhotosCount: Int,
    val selectionRate: Double,
    val selectionOrders: List<Int>,

    // Person-level metrics breakdown
    val personBreakdowns: Map<String, PersonObservationSummary> = emptyMap()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("total_trials", totalTrials)
            put("completed_trials", completedTrials)
            put("recognized_count", recognizedCount)
            put("familiar_count", familiarCount)
            put("uncertain_count", uncertainCount)
            put("dont_remember_count", dontRememberCount)
            put("skipped_count", skippedCount)

            put("recognition_rate", recognitionRate)
            put("familiarity_rate", familiarityRate)
            put("uncertainty_rate", uncertaintyRate)
            put("dont_remember_rate", dontRememberRate)
            put("skip_rate", skipRate)

            put("recall_yes_count", recallYesCount)
            put("recall_a_little_count", recallALittleCount)
            put("recall_not_sure_count", recallNotSureCount)
            put("recall_no_count", recallNoCount)
            put("recall_skipped_count", recallSkippedCount)

            put("recall_yes_rate", recallYesRate)
            put("recall_a_little_rate", recallALittleRate)
            put("recall_not_sure_rate", recallNotSureRate)
            put("recall_no_rate", recallNoRate)

            put("mean_response_time_ms", meanResponseTimeMs)
            put("median_response_time_ms", medianResponseTimeMs)
            put("response_time_variability_ms", responseTimeVariabilityMs)
            put("fastest_response_ms", fastestResponseMs ?: JSONObject.NULL)
            put("slowest_response_ms", slowestResponseMs ?: JSONObject.NULL)

            put("selected_photos_count", selectedPhotosCount)
            put("selection_rate", selectionRate)

            val personJson = JSONObject()
            personBreakdowns.forEach { (personId, summary) ->
                personJson.put(personId, summary.toJsonObject())
            }
            put("person_breakdowns", personJson)
        }
    }
}

data class PersonObservationSummary(
    val personId: String,
    val personName: String,
    val relationship: String,
    val photosShown: Int,
    val photosRecognized: Int,
    val photosFamiliar: Int,
    val photosUncertain: Int,
    val photosNotRemembered: Int,
    val photosSelected: Int,
    val meanResponseTimeMs: Double
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("person_id", personId)
            put("person_name", personName)
            put("relationship", relationship)
            put("photos_shown", photosShown)
            put("photos_recognized", photosRecognized)
            put("photos_familiar", photosFamiliar)
            put("photos_uncertain", photosUncertain)
            put("photos_not_remembered", photosNotRemembered)
            put("photos_selected", photosSelected)
            put("mean_response_time_ms", meanResponseTimeMs)
        }
    }
}

/**
 * Item passed into trial presentation combining a photo with person context.
 */
data class TrialPhotoItem(
    val photo: PhotoMemory,
    val person: Person
)

object FamilyMemoryExperimentEngine {

    private const val ALGORITHM_VERSION = "1.0"

    /**
     * Calculates observational session metrics from completed trials.
     */
    fun calculateSessionMetrics(
        trials: List<FamilyRecognitionTrial>,
        peopleMap: Map<String, Person>
    ): FamilyMemorySessionMetrics {
        val totalTrials = trials.size
        val nonSkippedTrials = trials.filter { !it.skipped }
        val completedTrials = nonSkippedTrials.size

        val recognizedCount = trials.count { it.recognitionResponse == FamilyRecognitionTrial.RECOGNIZE }
        val familiarCount = trials.count { it.recognitionResponse == FamilyRecognitionTrial.FAMILIAR }
        val uncertainCount = trials.count { it.recognitionResponse == FamilyRecognitionTrial.NOT_SURE }
        val dontRememberCount = trials.count { it.recognitionResponse == FamilyRecognitionTrial.DONT_REMEMBER }
        val skippedCount = trials.count { it.skipped || it.recognitionResponse == FamilyRecognitionTrial.SKIPPED }

        val recognitionRate = if (totalTrials > 0) recognizedCount.toDouble() / totalTrials else 0.0
        val familiarityRate = if (totalTrials > 0) familiarCount.toDouble() / totalTrials else 0.0
        val uncertaintyRate = if (totalTrials > 0) uncertainCount.toDouble() / totalTrials else 0.0
        val dontRememberRate = if (totalTrials > 0) dontRememberCount.toDouble() / totalTrials else 0.0
        val skipRate = if (totalTrials > 0) skippedCount.toDouble() / totalTrials else 0.0

        // Recall counts
        val recallTrials = trials.filter { it.recallResponse != null }
        val recallYesCount = recallTrials.count { it.recallResponse == FamilyRecognitionTrial.RECALL_YES }
        val recallALittleCount = recallTrials.count { it.recallResponse == FamilyRecognitionTrial.RECALL_A_LITTLE }
        val recallNotSureCount = recallTrials.count { it.recallResponse == FamilyRecognitionTrial.RECALL_NOT_SURE }
        val recallNoCount = recallTrials.count { it.recallResponse == FamilyRecognitionTrial.RECALL_NO }
        val recallSkippedCount = recallTrials.count { it.recallResponse == FamilyRecognitionTrial.RECALL_SKIPPED }

        val totalRecall = recallTrials.size
        val recallYesRate = if (totalRecall > 0) recallYesCount.toDouble() / totalRecall else 0.0
        val recallALittleRate = if (totalRecall > 0) recallALittleCount.toDouble() / totalRecall else 0.0
        val recallNotSureRate = if (totalRecall > 0) recallNotSureCount.toDouble() / totalRecall else 0.0
        val recallNoRate = if (totalRecall > 0) recallNoCount.toDouble() / totalRecall else 0.0

        // Timing calculations
        val validResponseTimes = nonSkippedTrials.map { it.responseTimeMs.toDouble() }.filter { it > 0 }
        val meanRt = if (validResponseTimes.isNotEmpty()) validResponseTimes.average() else 0.0
        val medianRt = if (validResponseTimes.isNotEmpty()) {
            val sorted = validResponseTimes.sorted()
            val mid = sorted.size / 2
            if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
        } else 0.0

        val rtVariability = if (validResponseTimes.size >= 2) {
            val mean = validResponseTimes.average()
            val variance = validResponseTimes.map { (it - mean).pow(2) }.average()
            sqrt(variance)
        } else 0.0

        val fastestRt = nonSkippedTrials.map { it.responseTimeMs }.filter { it > 0 }.minOrNull()
        val slowestRt = nonSkippedTrials.map { it.responseTimeMs }.filter { it > 0 }.maxOrNull()

        // Selection metrics
        val selectedTrials = trials.filter { it.selected }.sortedBy { it.selectionOrder ?: 999 }
        val selectedCount = selectedTrials.size
        val selectionRate = if (totalTrials > 0) selectedCount.toDouble() / totalTrials else 0.0
        val selectionOrders = selectedTrials.mapNotNull { it.selectionOrder }

        // Person-level breakdowns
        val personBreakdowns = mutableMapOf<String, PersonObservationSummary>()
        trials.groupBy { it.personId }.forEach { (personId, personTrials) ->
            val person = peopleMap[personId]
            val pName = person?.name ?: "Person"
            val pRel = person?.relationship ?: "Family"
            val pNonSkipped = personTrials.filter { !it.skipped && it.responseTimeMs > 0 }
            val pMeanRt = if (pNonSkipped.isNotEmpty()) pNonSkipped.map { it.responseTimeMs.toDouble() }.average() else 0.0

            personBreakdowns[personId] = PersonObservationSummary(
                personId = personId,
                personName = pName,
                relationship = pRel,
                photosShown = personTrials.size,
                photosRecognized = personTrials.count { it.recognitionResponse == FamilyRecognitionTrial.RECOGNIZE },
                photosFamiliar = personTrials.count { it.recognitionResponse == FamilyRecognitionTrial.FAMILIAR },
                photosUncertain = personTrials.count { it.recognitionResponse == FamilyRecognitionTrial.NOT_SURE },
                photosNotRemembered = personTrials.count { it.recognitionResponse == FamilyRecognitionTrial.DONT_REMEMBER },
                photosSelected = personTrials.count { it.selected },
                meanResponseTimeMs = pMeanRt
            )
        }

        return FamilyMemorySessionMetrics(
            totalTrials = totalTrials,
            completedTrials = completedTrials,
            recognizedCount = recognizedCount,
            familiarCount = familiarCount,
            uncertainCount = uncertainCount,
            dontRememberCount = dontRememberCount,
            skippedCount = skippedCount,
            recognitionRate = recognitionRate,
            familiarityRate = familiarityRate,
            uncertaintyRate = uncertaintyRate,
            dontRememberRate = dontRememberRate,
            skipRate = skipRate,
            recallYesCount = recallYesCount,
            recallALittleCount = recallALittleCount,
            recallNotSureCount = recallNotSureCount,
            recallNoCount = recallNoCount,
            recallSkippedCount = recallSkippedCount,
            recallYesRate = recallYesRate,
            recallALittleRate = recallALittleRate,
            recallNotSureRate = recallNotSureRate,
            recallNoRate = recallNoRate,
            meanResponseTimeMs = meanRt,
            medianResponseTimeMs = medianRt,
            responseTimeVariabilityMs = rtVariability,
            fastestResponseMs = fastestRt,
            slowestResponseMs = slowestRt,
            selectedPhotosCount = selectedCount,
            selectionRate = selectionRate,
            selectionOrders = selectionOrders,
            personBreakdowns = personBreakdowns
        )
    }

    /**
     * Builds candidate photo associations from an explicit cross-person or single-person selection cluster.
     *
     * INVARIANT:
     * User selection is the observation.
     * Metadata is the evidence.
     * The score represents deterministic connection strength (0.0 to 1.0) based on defined evidence.
     * It is NOT a medical claim or confirmation of a neurological memory.
     */
    fun computeCandidateAssociations(
        clusterId: String,
        selectedTrials: List<FamilyRecognitionTrial>,
        photosMap: Map<String, PhotoMemory>,
        historicalClusters: List<SelectionCluster> = emptyList()
    ): List<PhotoAssociation> {
        if (selectedTrials.size < 2) return emptyList()

        val associations = mutableListOf<PhotoAssociation>()
        val count = selectedTrials.size

        for (i in 0 until count) {
            for (j in (i + 1) until count) {
                val trialA = selectedTrials[i]
                val trialB = selectedTrials[j]
                val photoA = photosMap[trialA.photoId]
                val photoB = photosMap[trialB.photoId]

                if (photoA == null || photoB == null) continue

                val evidenceList = mutableListOf<String>()
                var score = 0.0

                // 1. Both selected together in this session
                evidenceList.add("selected_together_in_session")
                score += 0.20

                // 2. Selection order proximity (adjacent selections)
                val orderA = trialA.selectionOrder ?: (i + 1)
                val orderB = trialB.selectionOrder ?: (j + 1)
                val orderDiff = abs(orderA - orderB)
                if (orderDiff == 1) {
                    evidenceList.add("adjacent_selection_order")
                    score += 0.15
                } else if (orderDiff <= 2) {
                    evidenceList.add("close_selection_order")
                    score += 0.10
                }

                // 3. Selection time interval
                val timeA = trialA.selectionTimestamp ?: trialA.responseAt
                val timeB = trialB.selectionTimestamp ?: trialB.responseAt
                val timeIntervalSec = abs(timeA - timeB) / 1000.0
                if (timeIntervalSec <= 15.0) {
                    evidenceList.add("rapid_successive_selection: ${String.format(Locale.US, "%.1fs", timeIntervalSec)}")
                    score += 0.10
                }

                // 4. Cross-person photo indicator
                val isCrossPerson = trialA.personId != trialB.personId
                if (isCrossPerson) {
                    evidenceList.add("cross_person_cluster")
                } else {
                    evidenceList.add("same_person_directory")
                }

                // 5. Capture time proximity (if EXIF taken_timestamp exists)
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
                            evidenceList.add("same_capture_date: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(takenA)}")
                            score += 0.35
                        }
                        daysDiff <= 7.0 -> {
                            evidenceList.add("close_capture_time_7_days: ${String.format(Locale.US, "%.1fd", daysDiff)}")
                            score += 0.25
                        }
                        sameMonth -> {
                            evidenceList.add("same_capture_month: ${SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(takenA)}")
                            score += 0.18
                        }
                        sameYear -> {
                            evidenceList.add("same_capture_year: ${calA.get(Calendar.YEAR)}")
                            score += 0.10
                        }
                    }
                }

                // 6. Location metadata comparison (if available and permitted)
                val locA = photoA.optionalLocation?.trim()
                val locB = photoB.optionalLocation?.trim()
                if (!locA.isNullOrBlank() && !locB.isNullOrBlank()) {
                    if (locA.equals(locB, ignoreCase = true)) {
                        evidenceList.add("shared_location_metadata: $locA")
                        score += 0.20
                    }
                }

                // 7. Repeated co-selection across prior sessions
                var historicalCoSelections = 0
                historicalClusters.forEach { prevCluster ->
                    try {
                        val idsArray = JSONArray(prevCluster.selectedPhotoIds)
                        val idSet = (0 until idsArray.length()).map { idsArray.getString(it) }.toSet()
                        if (idSet.contains(photoA.photoId) && idSet.contains(photoB.photoId)) {
                            historicalCoSelections++
                        }
                    } catch (_: Exception) {
                        // ignore malformed history
                    }
                }
                if (historicalCoSelections > 0) {
                    evidenceList.add("repeated_co_selection: $historicalCoSelections previous sessions")
                    score += (0.10 * historicalCoSelections).coerceAtMost(0.25)
                }

                // Check for minimum evidence threshold
                val finalScore = score.coerceIn(0.0, 1.0)
                val evidenceFeatures = if (finalScore >= 0.20) {
                    evidenceList.joinToString(separator = "; ")
                } else {
                    "insufficient_evidence; " + evidenceList.joinToString(separator = "; ")
                }

                associations.add(
                    PhotoAssociation(
                        associationId = UUID.randomUUID().toString(),
                        clusterId = clusterId,
                        photoAId = photoA.photoId,
                        photoBId = photoB.photoId,
                        personAId = trialA.personId,
                        personBId = trialB.personId,
                        evidenceFeatures = evidenceFeatures,
                        associationScore = finalScore,
                        algorithmVersion = ALGORITHM_VERSION,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }

        return associations
    }
}
