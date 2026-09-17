package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Dedicated local Room entity representing a person in the Family Memory module.
 * Uses a stable, unique personId so names, relationships, and cover photos
 * can be edited freely without breaking foreign keys or future associations.
 */
@Entity(tableName = "people")
data class Person(
    @PrimaryKey
    @ColumnInfo(name = "person_id")
    val personId: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "relationship")
    val relationship: String,

    @ColumnInfo(name = "cover_photo_uri")
    val coverPhotoUri: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
