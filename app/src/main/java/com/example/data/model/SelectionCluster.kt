package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a cluster of photos explicitly selected by the user during an experiment session
 * because they produced a sense of familiarity, connection, or recollection.
 *
 * Supports cross-person selection groups and preserves raw selection order and timing.
 */
@Entity(
    tableName = "selection_clusters",
    indices = [
        Index(value = ["session_id"])
    ]
)
data class SelectionCluster(
    @PrimaryKey
    @ColumnInfo(name = "cluster_id")
    val clusterId: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    @ColumnInfo(name = "selected_photo_ids")
    val selectedPhotoIds: String, // JSON array string of photo IDs in selection order

    @ColumnInfo(name = "selection_timestamps")
    val selectionTimestamps: String, // JSON array string of Long timestamps

    @ColumnInfo(name = "selection_orders")
    val selectionOrders: String, // JSON array string of Int selection positions (1, 2, 3...)

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "directory_person_id")
    val directoryPersonId: String? = null,

    @ColumnInfo(name = "user_response")
    val userResponse: String? = null,

    @ColumnInfo(name = "is_saved_connection", defaultValue = "0")
    val isSavedConnection: Boolean = false,

    @ColumnInfo(name = "user_note")
    val userNote: String? = null,

    @ColumnInfo(name = "voice_note_uri")
    val voiceNoteUri: String? = null,

    @ColumnInfo(name = "user_reflection")
    val userReflection: String? = null
)
