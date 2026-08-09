package com.fatmambo33.eclipsecam

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class PositionMapInstrumentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun positionShowsLocalGeometryWithoutConfiguredBasemap() {
        composeRule.onNodeWithTag("tab-position").performClick()

        composeRule.onNodeWithTag("eclipse-map")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("map-attribution")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Offline eclipse geometry • no basemap")
            .assertIsDisplayed()
    }
}
