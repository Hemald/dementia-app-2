package com.example

import com.example.data.model.Person
import com.example.data.model.PhotoMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyMemoryModelTest {

    @Test
    fun testPersonInitializationWithDefaults() {
        val person = Person(
            name = "Sarah",
            relationship = "Daughter"
        )

        assertNotNull(person.personId)
        assertTrue(person.personId.isNotEmpty())
        assertEquals("Sarah", person.name)
        assertEquals("Daughter", person.relationship)
        assertNull(person.coverPhotoUri)
        assertTrue(person.createdAt > 0)
        assertEquals(person.createdAt, person.updatedAt)
    }

    @Test
    fun testPersonWithCustomPhotoAndId() {
        val customId = "person-12345"
        val photoUri = "file:///data/user/0/com.example/files/photos/person-12345_cover.jpg"
        val person = Person(
            personId = customId,
            name = "Michael",
            relationship = "Son",
            coverPhotoUri = photoUri,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        assertEquals("person-12345", person.personId)
        assertEquals("Michael", person.name)
        assertEquals("Son", person.relationship)
        assertEquals(photoUri, person.coverPhotoUri)
        assertEquals(1000L, person.createdAt)
        assertEquals(2000L, person.updatedAt)
    }

    @Test
    fun testPersonUpdatePhotoImmutability() {
        val original = Person(
            name = "Eleanor",
            relationship = "Sister",
            coverPhotoUri = null
        )

        val updatedPhotoUri = "file:///data/user/0/com.example/files/photos/new_photo.jpg"
        val updated = original.copy(
            coverPhotoUri = updatedPhotoUri,
            updatedAt = 5000L
        )

        assertEquals(original.personId, updated.personId)
        assertEquals(original.name, updated.name)
        assertEquals(original.relationship, updated.relationship)
        assertEquals(updatedPhotoUri, updated.coverPhotoUri)
        assertEquals(5000L, updated.updatedAt)
    }

    @Test
    fun testPhotoMemoryModelDefaults() {
        val personId = "person-dad-1"
        val localUri = "file:///data/user/0/com.example/files/person_memories/photo_dad.jpg"

        val photoMemory = PhotoMemory(
            personId = personId,
            localUri = localUri
        )

        assertNotNull(photoMemory.photoId)
        assertTrue(photoMemory.photoId.isNotEmpty())
        assertEquals(personId, photoMemory.personId)
        assertEquals(localUri, photoMemory.localUri)
        assertNull(photoMemory.takenTimestamp)
        assertNull(photoMemory.contentHash)
        assertNull(photoMemory.optionalLocation)
        assertNull(photoMemory.width)
        assertNull(photoMemory.height)
        assertEquals("photo_picker", photoMemory.source)
        assertEquals(0, photoMemory.displayOrder)
        assertTrue(photoMemory.addedTimestamp > 0)
    }

    @Test
    fun testPhotoMemoryWithMetadata() {
        val photoMemory = PhotoMemory(
            photoId = "photo-999",
            personId = "person-mom-2",
            localUri = "file:///data/user/0/com.example/files/person_memories/photo_mom.jpg",
            contentHash = "a1b2c3d4e5f6",
            takenTimestamp = 1600000000000L,
            addedTimestamp = 1700000000000L,
            source = "photo_picker",
            optionalLocation = "37.7749, -122.4194",
            displayOrder = 1,
            width = 1920,
            height = 1080
        )

        assertEquals("photo-999", photoMemory.photoId)
        assertEquals("person-mom-2", photoMemory.personId)
        assertEquals("a1b2c3d4e5f6", photoMemory.contentHash)
        assertEquals(1600000000000L, photoMemory.takenTimestamp)
        assertEquals(1700000000000L, photoMemory.addedTimestamp)
        assertEquals("37.7749, -122.4194", photoMemory.optionalLocation)
        assertEquals(1, photoMemory.displayOrder)
        assertEquals(1920, photoMemory.width)
        assertEquals(1080, photoMemory.height)
    }

    @Test
    fun testPhotoMemoryDistinctIdsGenerated() {
        val p1 = PhotoMemory(personId = "p1", localUri = "uri1")
        val p2 = PhotoMemory(personId = "p1", localUri = "uri2")

        assertNotEquals(p1.photoId, p2.photoId)
    }

    @Test
    fun testPhotoSortingFallbackOrder() {
        val now = System.currentTimeMillis()
        val photoWithTaken = PhotoMemory(
            photoId = "1",
            personId = "dad",
            localUri = "uri1",
            takenTimestamp = now - 100000L,
            addedTimestamp = now
        )
        val photoWithoutTaken = PhotoMemory(
            photoId = "2",
            personId = "dad",
            localUri = "uri2",
            takenTimestamp = null,
            addedTimestamp = now - 50000L
        )

        val list = listOf(photoWithTaken, photoWithoutTaken)
        val sorted = list.sortedWith(
            compareBy<PhotoMemory> { it.displayOrder }
                .thenByDescending { it.takenTimestamp ?: it.addedTimestamp }
        )

        assertEquals("2", sorted[0].photoId)
        assertEquals("1", sorted[1].photoId)
    }
}
