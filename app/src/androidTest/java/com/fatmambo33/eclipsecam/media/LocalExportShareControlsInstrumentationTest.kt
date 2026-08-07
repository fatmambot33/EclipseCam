package com.fatmambo33.eclipsecam.media

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fatmambo33.eclipsecam.MainActivity
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalExportShareControlsInstrumentationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val root: File
        get() = File(compose.activity.filesDir, "captures")

    @Before
    fun createFixture() {
        root.deleteRecursively()
        val session = File(root, "export-controls").apply { mkdirs() }
        File(session, "000000_capture.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(session, "session.complete").writeText("complete\n")
    }

    @After
    fun removeFixture() {
        root.deleteRecursively()
    }

    @Test
    fun exportAndShareAreExplicitAndLocationRemovalIsDefault() {
        compose.onNodeWithTag("tab-gallery").performClick()
        waitForTag("gallery-session-export-controls")
        compose.onNodeWithTag("gallery-session-export-controls").performClick()
        waitForTag("export-share-card")

        compose.onNodeWithTag("export-share-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("export-location-remove").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("export-location-preserve").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("export-destination").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("export-share").performScrollTo().assertIsDisplayed()
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
