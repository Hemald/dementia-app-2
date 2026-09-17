package com.example

import com.example.experiment.reaction.ReactionCoordinationExperiment
import com.example.experiment.reaction.ReactionSessionMeasurements
import com.example.experiment.reaction.ReactionTrialMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReactionCoordinationExperimentTest {

    @Test
    fun `test adaptive difficulty adaptation based on accuracy and miss rates`() {
        val initialDiff = 2.0
        val highAccuracySession = ReactionSessionMeasurements(
            totalTrials = 10,
            completedTrials = 10,
            meanReactionTimeMs = 420.0,
            medianReactionTimeMs = 410.0,
            reactionTimeVariabilityMs = 35.0,
            fastestReactionTimeMs = 350L,
            slowestReactionTimeMs = 500L,
            hitAccuracy = 0.90,
            directHitRate = 0.80,
            boundaryHitRate = 0.10,
            missTimeoutRate = 0.10,
            offTargetTouchRate = 0.0,
            totalOffTargetTouches = 0,
            adaptiveDifficultySeconds = initialDiff
        )

        val nextDiff = ReactionCoordinationExperiment.computeAdaptiveDifficulty(listOf(highAccuracySession))
        // High accuracy (>= 80%) and low timeout (<= 15%) steps down the visibility window (higher challenge)
        assertTrue(nextDiff < initialDiff)

        val poorSession = ReactionSessionMeasurements(
            totalTrials = 10,
            completedTrials = 10,
            meanReactionTimeMs = 800.0,
            medianReactionTimeMs = 820.0,
            reactionTimeVariabilityMs = 90.0,
            fastestReactionTimeMs = 600L,
            slowestReactionTimeMs = 1100L,
            hitAccuracy = 0.40,
            directHitRate = 0.30,
            boundaryHitRate = 0.10,
            missTimeoutRate = 0.40,
            offTargetTouchRate = 0.30,
            totalOffTargetTouches = 4,
            adaptiveDifficultySeconds = initialDiff
        )

        val relaxedDiff = ReactionCoordinationExperiment.computeAdaptiveDifficulty(listOf(poorSession))
        // Low accuracy (< 60%) or high timeout (>= 35%) widens window (easier)
        assertTrue(relaxedDiff > initialDiff)
    }

    @Test
    fun `test reaction measurements computation accurately tracks hits, off-target, and timeouts`() {
        val trials = listOf(
            ReactionTrialMeasurement(
                trialId = "t1",
                trialNumber = 1,
                timestamp = "2026-09-17T00:00:00Z",
                targetAppearanceTimestamp = 1000L,
                targetLocationX = 0.5f,
                targetLocationY = 0.5f,
                visibilityDurationMs = 2000L,
                difficultyLevel = 2.0,
                vibrationTimestamp = 1000L,
                reactionTimeMs = 450L,
                hitType = ReactionTrialMeasurement.HIT_DIRECT,
                offTargetTouch = false,
                offTargetTouchesCount = 0,
                timeout = false
            ),
            ReactionTrialMeasurement(
                trialId = "t2",
                trialNumber = 2,
                timestamp = "2026-09-17T00:00:05Z",
                targetAppearanceTimestamp = 6000L,
                targetLocationX = 0.3f,
                targetLocationY = 0.7f,
                visibilityDurationMs = 2000L,
                difficultyLevel = 2.0,
                vibrationTimestamp = 6000L,
                reactionTimeMs = 550L,
                hitType = ReactionTrialMeasurement.HIT_BOUNDARY,
                offTargetTouch = true,
                offTargetTouchesCount = 1,
                timeout = false
            ),
            ReactionTrialMeasurement(
                trialId = "t3",
                trialNumber = 3,
                timestamp = "2026-09-17T00:00:10Z",
                targetAppearanceTimestamp = 11000L,
                targetLocationX = 0.6f,
                targetLocationY = 0.4f,
                visibilityDurationMs = 2000L,
                difficultyLevel = 2.0,
                vibrationTimestamp = 11000L,
                reactionTimeMs = null,
                hitType = ReactionTrialMeasurement.HIT_TIMEOUT,
                offTargetTouch = false,
                offTargetTouchesCount = 0,
                timeout = true
            )
        )

        val measurements = ReactionCoordinationExperiment.calculateMeasurements(
            trials = trials,
            totalTrials = 3,
            adaptiveDifficultySeconds = 2.0
        )

        assertEquals(3, measurements.totalTrials)
        assertEquals(3, measurements.completedTrials)
        assertEquals(1, measurements.totalOffTargetTouches)
        // Hit accuracy: 2 hits out of 3 = 66.67%
        assertEquals(2.0 / 3.0, measurements.hitAccuracy, 0.01)
        // Direct hit rate = 1/3
        assertEquals(1.0 / 3.0, measurements.directHitRate, 0.01)
        // Boundary hit rate = 1/3
        assertEquals(1.0 / 3.0, measurements.boundaryHitRate, 0.01)
        // Timeout rate = 1/3
        assertEquals(1.0 / 3.0, measurements.missTimeoutRate, 0.01)
        // Off target rate = 1/3
        assertEquals(1.0 / 3.0, measurements.offTargetTouchRate, 0.01)
        // Mean RT of hits (450 + 550) / 2 = 500ms
        assertEquals(500.0, measurements.meanReactionTimeMs ?: 0.0, 0.01)
        assertEquals(450L, measurements.fastestReactionTimeMs)
        assertEquals(550L, measurements.slowestReactionTimeMs)
    }

    @Test
    fun `test serialization of reaction session metadata`() {
        val trials = listOf(
            ReactionTrialMeasurement(
                trialId = "t1",
                trialNumber = 1,
                timestamp = "2026-09-17T00:00:00Z",
                targetAppearanceTimestamp = 1000L,
                targetLocationX = 0.5f,
                targetLocationY = 0.5f,
                visibilityDurationMs = 2000L,
                difficultyLevel = 2.0,
                vibrationTimestamp = 1000L,
                reactionTimeMs = 400L,
                hitType = ReactionTrialMeasurement.HIT_DIRECT,
                offTargetTouch = false,
                offTargetTouchesCount = 0,
                timeout = false
            )
        )
        val measurements = ReactionCoordinationExperiment.calculateMeasurements(trials, 1, 2.0)
        val json = ReactionCoordinationExperiment.serializeSessionMetadata(measurements, trials)

        assertNotNull(json)
        assertTrue(json.contains("reaction_coordination"))
        assertTrue(json.contains("direct_hit"))
    }
}
