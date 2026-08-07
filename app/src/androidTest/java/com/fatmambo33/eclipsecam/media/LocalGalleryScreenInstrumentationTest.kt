package com.fatmambo33.eclipsecam.media

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fatmambo33.eclipsecam.MainActivity
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalGalleryScreenInstrumentationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val root: File
        get() = File(compose.activity.filesDir, "captures")

    @Before
    fun cleanFixture() {
        root.deleteRecursively()
    }

    @After
    fun removeFixture() {
        root.deleteRecursively()
    }

    @Test
    fun emptyGalleryShowsLocalPrivacyEmptyState() {
        openGallery()

        waitForTag("gallery-empty")
        compose.onNodeWithTag("gallery-empty").assertIsDisplayed()
    }

    @Test
    fun completeSessionAppearsAndOpensDetail() {
        val session = File(root, "complete-session").apply { mkdirs() }
        File(session, "000000_capture.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(session, "session.complete").writeText("complete\n")

        openGallery()

        waitForTag("gallery-session-complete-session")
        compose.onNodeWithTag("gallery-session-complete-session").assertIsDisplayed().performClick()
        waitForTag("gallery-detail")
        compose.onNodeWithTag("gallery-detail").assertIsDisplayed()
    }

    @Test
    fun interruptedSessionRemainsVisible() {
        val session = File(root, "interrupted-session").apply { mkdirs() }
        File(session, "000000_capture.jpg").writeBytes(byteArrayOf(1))

        openGallery()

        waitForTag("gallery-session-interrupted-session")
        compose.onNodeWithTag("gallery-session-interrupted-session").assertIsDisplayed()
        compose.onNodeWithTag("gallery-session-interrupted-session").performClick()
        waitForTag("gallery-detail")
        compose.onNodeWithTag("gallery-detail").assertIsDisplayed()
    }

    private fun openGallery() {
        compose.onNodeWithContentDescription("Gallery").performClick()
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
