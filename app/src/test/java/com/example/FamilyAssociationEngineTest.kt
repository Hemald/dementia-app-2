package com.example

import com.example.data.model.Person
import com.example.data.model.PhotoMemory
import com.example.data.model.SelectionCluster
import com.example.experiment.family.ExplorationSelection
import com.example.experiment.family.FamilyAssociationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FamilyAssociationEngineTest {

    private val dad = Person(personId = "person-dad", name = "Robert", relationship = "Father")
    private val mom = Person(personId = "person-mom", name = "Eleanor", relationship = "Mother")

    @Test
    fun testSameDayCrossPersonCandidates() {
        val cal = Calendar.getInstance().apply {
            set(2024, Calendar.JULY, 15, 14, 30, 0)
        }
        val targetTimestamp = cal.timeInMillis

        // Candidate photo taken on the exact same day, 2 hours later
        val candidateCal = Calendar.getInstance().apply {
            set(2024, Calendar.JULY, 15, 16, 30, 0)
        }
        val candidateTimestamp = candidateCal.timeInMillis

        val targetPhoto = PhotoMemory(
            photoId = "photo-dad-1",
            personId = dad.personId,
            localUri = "uri_dad_1",
            takenTimestamp = targetTimestamp
        )

        val candidatePhoto = PhotoMemory(
            photoId = "photo-mom-1",
            personId = mom.personId,
            localUri = "uri_mom_1",
            takenTimestamp = candidateTimestamp
        )

        val candidates = FamilyAssociationEngine.findCrossPersonCandidates(
            targetPhoto = targetPhoto,
            targetPerson = dad,
            allOtherPhotos = listOf(candidatePhoto),
            peopleMap = mapOf(dad.personId to dad, mom.personId to mom)
        )

        assertEquals(1, candidates.size)
        val candidate = candidates[0]
        assertEquals("photo-mom-1", candidate.candidatePhoto.photoId)
        assertEquals(mom.personId, candidate.candidatePerson.personId)
        assertTrue(candidate.associationScore >= 0.35)
        assertTrue(candidate.evidenceReasons.any { it.contains("same day") })
    }

    @Test
    fun testClusterAssociationsCalculation() {
        val now = System.currentTimeMillis()
        val photo1 = PhotoMemory(photoId = "p1", personId = dad.personId, localUri = "uri1", takenTimestamp = now)
        val photo2 = PhotoMemory(photoId = "p2", personId = dad.personId, localUri = "uri2", takenTimestamp = now + 1000)

        val sel1 = ExplorationSelection(photo = photo1, person = dad, selectedAt = now, selectionOrder = 1)
        val sel2 = ExplorationSelection(photo = photo2, person = dad, selectedAt = now + 2000, selectionOrder = 2)

        val results = FamilyAssociationEngine.computeClusterAssociations(listOf(sel1, sel2))
        assertEquals(1, results.size)
        val pair = results[0]
        assertEquals("p1", pair.photoA.photoId)
        assertEquals("p2", pair.photoB.photoId)
        assertTrue(pair.associationScore >= 0.50) // selected together + adjacent order + rapid selection + same day
        assertTrue(pair.evidenceReasons.any { it.contains("consecutively") })
    }

    @Test
    fun testRepeatedHistoricalClusterAssociations() {
        val photo1 = PhotoMemory(photoId = "p1", personId = dad.personId, localUri = "uri1")
        val photo2 = PhotoMemory(photoId = "p2", personId = mom.personId, localUri = "uri2")

        val historicalClusters = listOf(
            SelectionCluster(
                clusterId = "c1",
                sessionId = 1L,
                selectedPhotoIds = "[\"p1\", \"p2\"]",
                selectionTimestamps = "[1000, 2000]",
                selectionOrders = "[1, 2]",
                userResponse = "confirmed_connection",
                isSavedConnection = true
            )
        )

        val sel1 = ExplorationSelection(photo = photo1, person = dad, selectionOrder = 1)
        val sel2 = ExplorationSelection(photo = photo2, person = mom, selectionOrder = 2)

        val results = FamilyAssociationEngine.computeClusterAssociations(
            listOf(sel1, sel2),
            historicalClusters = historicalClusters
        )

        assertEquals(1, results.size)
        val pair = results[0]
        assertTrue(pair.isRepeatedSelection)
        assertEquals(1, pair.repeatedCount)
        assertTrue(pair.evidenceReasons.any { it.contains("Repeated association") })
    }

    @Test
    fun testCreatePhotoAssociationsEntityMapping() {
        val photo1 = PhotoMemory(photoId = "p1", personId = dad.personId, localUri = "uri1")
        val photo2 = PhotoMemory(photoId = "p2", personId = mom.personId, localUri = "uri2")
        val sel1 = ExplorationSelection(photo = photo1, person = dad, selectionOrder = 1)
        val sel2 = ExplorationSelection(photo = photo2, person = mom, selectionOrder = 2)

        val pairwise = FamilyAssociationEngine.computeClusterAssociations(listOf(sel1, sel2))
        val clusterId = "cluster-test-123"

        val entities = FamilyAssociationEngine.createPhotoAssociations(
            clusterId = clusterId,
            pairwiseResults = pairwise,
            userConfirmed = true,
            userResponse = "confirmed_connection"
        )

        assertEquals(1, entities.size)
        val entity = entities[0]
        assertEquals(clusterId, entity.clusterId)
        assertEquals("p1", entity.photoAId)
        assertEquals("p2", entity.photoBId)
        assertTrue(entity.userConfirmed)
        assertEquals("confirmed_connection", entity.userResponse)
        assertEquals(FamilyAssociationEngine.ALGORITHM_VERSION, entity.algorithmVersion)
    }
}
