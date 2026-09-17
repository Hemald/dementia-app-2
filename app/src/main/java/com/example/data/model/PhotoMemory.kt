package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a persistent local photograph memory belonging to a specific person.
 * Prepares the architecture for later recognition testing and Memory Map relationships
 * while remaining completely offline and private.
 */
@Entity(
    tableName = "photo_memories",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["person_id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["person_id"]),
        Index(value = ["person_id", "content_hash"])
    ]
)
data class PhotoMemory(
    @PrimaryKey
    @ColumnInfo(name = "photo_id")
    val photoId: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "person_id")
    val personId: String,

    @ColumnInfo(name = "local_uri")
    val localUri: String,

    @ColumnInfo(name = "content_hash")
    val contentHash: String? = null,

    @ColumnInfo(name = "taken_timestamp")
    val takenTimestamp: Long? = null,

    @ColumnInfo(name = "added_timestamp")
    val addedTimestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "source")
    val source: String = "photo_picker",

    @ColumnInfo(name = "optional_location")
    val optionalLocation: String? = null,

    @ColumnInfo(name = "display_order", defaultValue = "0")
    val displayOrder: Int = 0,

    @ColumnInfo(name = "width")
    val width: Int? = null,

    @ColumnInfo(name = "height")
    val height: Int? = null
)
