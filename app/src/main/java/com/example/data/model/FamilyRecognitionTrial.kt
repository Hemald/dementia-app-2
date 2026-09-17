package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Detailed observational trial record for Family Memory Recognition experiments.
 *
 * Captures observable behaviors (recognition self-report, recall response, timing,
 * selection and selection order) without clinical or diagnostic interpretation.
 */
@Entity(
    tableName = "family_recognition_trials",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["person_id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PhotoMemory::class,
            parentColumns = ["photo_id"],
            childColumns = ["photo_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["person_id"]),
        Index(value = ["photo_id"])
    ]
)
data class FamilyRecognitionTrial(
    @PrimaryKey
    @ColumnInfo(name = "trial_id")
    val trialId: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    @ColumnInfo(name = "person_id")
    val personId: String,

    @ColumnInfo(name = "photo_id")
    val photoId: String,

    @ColumnInfo(name = "trial_started_at")
    val trialStartedAt: Long,

    @ColumnInfo(name = "photo_shown_at")
    val photoShownAt: Long,

    @ColumnInfo(name = "question_shown_at")
    val questionShownAt: Long,

    @ColumnInfo(name = "response_at")
    val responseAt: Long,

    @ColumnInfo(name = "recognition_response")
    val recognitionResponse: String, // "recognize", "familiar", "not_sure", "dont_remember", "skipped"

    @ColumnInfo(name = "recall_response")
    val recallResponse: String? = null, // "yes", "a_little", "not_sure", "no", "skipped", null

    @ColumnInfo(name = "response_time_ms")
    val responseTimeMs: Long,

    @ColumnInfo(name = "selected")
    val selected: Boolean = false,

    @ColumnInfo(name = "selection_order")
    val selectionOrder: Int? = null,

    @ColumnInfo(name = "selection_timestamp")
    val selectionTimestamp: Long? = null,

    @ColumnInfo(name = "skipped")
    val skipped: Boolean = false,

    @ColumnInfo(name = "difficulty")
    val difficulty: String = "standard",

    @ColumnInfo(name = "raw_metadata")
    val rawMetadata: String? = null
) {
    companion object {
        const val RECOGNIZE = "recognize"
        const val FAMILIAR = "familiar"
        const val NOT_SURE = "not_sure"
        const val DONT_REMEMBER = "dont_remember"
        const val SKIPPED = "skipped"

        const val RECALL_YES = "yes"
        const val RECALL_A_LITTLE = "a_little"
        const val RECALL_NOT_SURE = "not_sure"
        const val RECALL_NO = "no"
        const val RECALL_SKIPPED = "skipped"
    }
}
