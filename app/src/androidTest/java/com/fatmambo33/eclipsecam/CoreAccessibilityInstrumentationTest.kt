package com.fatmambo33.eclipsecam

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class CoreAccessibilityInstrumentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun coreNavigationHasLargeReachableTouchTargets() {
        listOf("camera", "live", "position", "gallery").forEach { tab ->
            composeRule.onNodeWithTag("tab-$tab")
                .assertIsDisplayed()
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun cameraStatusIsNotCommunicatedByColourAlone() {
        composeRule.onNodeWithTag("hero-status")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Needs attention",
                ),
            )

        composeRule.onNodeWithTag("camera-primary-action")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun liveAndPositionExposeMeaningfulStatusSemantics() {
        composeRule.onNodeWithTag("tab-live").performClick()
        composeRule.onNodeWithTag("hero-status")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Reference countdown",
                ),
            )

        composeRule.onNodeWithTag("tab-position").performClick()
        composeRule.onNodeWithTag("hero-status")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription),
            )
    }

    @Test
    fun everyCoreSurfaceRemainsReachableThroughBottomNavigation() {
        composeRule.onNodeWithTag("tab-camera").performClick()
        composeRule.onNodeWithTag("hero-status").assertIsDisplayed()

        composeRule.onNodeWithTag("tab-live").performClick()
        composeRule.onNodeWithTag("hero-status").assertIsDisplayed()

        composeRule.onNodeWithTag("tab-position").performClick()
        composeRule.onNodeWithTag("hero-status").assertIsDisplayed()

        composeRule.onNodeWithTag("tab-gallery").performClick()
        composeRule.onNodeWithText("Your eclipse sessions").assertIsDisplayed()
    }
}
