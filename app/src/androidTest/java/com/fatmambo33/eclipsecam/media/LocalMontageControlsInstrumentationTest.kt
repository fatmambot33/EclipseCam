package com.fatmambo33.eclipsecam.media

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fatmambo33.eclipsecam.MainActivity
import com.fatmambo33.eclipsecam.capture.CaptureInstruction
import com.fatmambo33.eclipsecam.capture.CapturePhase
import com.fatmambo33.eclipsecam.capture.CapturePlan
import com.fatmambo33.eclipsecam.capture.CaptureSessionCheckpoint
import com.fatmambo33.eclipsecam.capture.CaptureSessionStatus
import com.fatmambo33.eclipsecam.capture.ExposureStrategy
import java.io.File
import java.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalMontageControlsInstrumentationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val root: File
        get() = File(compose.activity.filesDir, "captures")

    @Before
    fun createFixture() {
        root.deleteRecursively()
        val start = Instant.parse("2026-08-12T17:00:00Z")
        val plan = CapturePlan(
            startsAtUtc = start,
            endsAtUtc = start.plusSeconds(2),
            instructions = listOf(
                CaptureInstruction(start, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL),
                CaptureInstruction(start.plusSeconds(1), CapturePhase.CONTACT_BURST, ExposureStrategy.CONTACT_BRACKET),
                CaptureInstruction(start.plusSeconds(2), CapturePhase.TOTALITY, ExposureStrategy.TOTALITY_BRACKET),
            ),
        )
        FileLocalCaptureSessionJournal(root).record(
            plan,
            CaptureSessionCheckpoint(
                sessionId = "montage-controls",
                planStartsAtUtc = plan.startsAtUtc,
                planEndsAtUtc = plan.endsAtUtc,
                nextInstructionIndex = 3,
                capturedCount = 3,
                skippedCount = 0,
                status = CaptureSessionStatus.COMPLETED,
                updatedAtUtc = start.plusSeconds(3),
            ),
        )
        val directory = File(root, "montage-controls")
        File(directory, "000000_partial.jpg").writeBytes(byteArrayOf(1))
        File(directory, "000001_contact.jpg").writeBytes(byteArrayOf(2))
        File(directory, "000002_totality.jpg").writeBytes(byteArrayOf(3))
    }

    @After
    fun removeFixture() {
        root.deleteRecursively()
    }

    @Test
    fun galleryExposesPhaseSelectionAndGenerationControls() {
        compose.onNodeWithTag("tab-gallery").performClick()
        waitForTag("gallery-session-montage-controls")
        compose.onNodeWithTag("gallery-session-montage-controls").performClick()
        waitForTag("montage-card")

        compose.onNodeWithTag("montage-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("montage-slot-totality").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("montage-slot-totality").assertIsNotSelected()
        compose.onNodeWithTag("montage-generate").performScrollTo().assertIsDisplayed()
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
