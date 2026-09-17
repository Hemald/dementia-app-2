package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a candidate mathematical connection between two selected photos within a selection cluster.
 *
 * INVARIANT:
 * User selection is the observation.
 * Metadata is the evidence.
 * The score is strictly an algorithm-defined candidate relationship score (0.0 to 1.0),
 * NOT a medical probability or confirmation of an autobiographical memory.
 */
@Entity(
    tableName = "photo_associations",
    foreignKeys = [
        ForeignKey(
            entity = SelectionCluster::class,
            parentColumns = ["cluster_id"],
            childColumns = ["cluster_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cluster_id"]),
        Index(value = ["photo_a_id", "photo_b_id"]),
        Index(value = ["person_a_id", "person_b_id"]),
        Index(value = ["user_confirmed"])
    ]
)
data class PhotoAssociation(
    @PrimaryKey
    @ColumnInfo(name = "association_id")
    val associationId: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "cluster_id")
    val clusterId: String,

    @ColumnInfo(name = "photo_a_id")
    val photoAId: String,

    @ColumnInfo(name = "photo_b_id")
    val photoBId: String,

    @ColumnInfo(name = "person_a_id")
    val personAId: String,

    @ColumnInfo(name = "person_b_id")
    val personBId: String,

    @ColumnInfo(name = "evidence_features")
    val evidenceFeatures: String,

    @ColumnInfo(name = "association_score")
    val associationScore: Double,

    @ColumnInfo(name = "algorithm_version")
    val algorithmVersion: String = "1.0",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "source", defaultValue = "'combined'")
    val source: String = "combined",

    @ColumnInfo(name = "user_confirmed", defaultValue = "0")
    val userConfirmed: Boolean = false,

    @ColumnInfo(name = "user_response")
    val userResponse: String? = null,

    @ColumnInfo(name = "confirmed_at")
    val confirmedAt: Long? = null,

    @ColumnInfo(name = "user_note")
    val userNote: String? = null,

    @ColumnInfo(name = "voice_note_uri")
    val voiceNoteUri: String? = null
)
