package com.indiewalkabout.nowdothis.app.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class AdsAppShellTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun appShell_placesBannerBelowAllNavigationContentWhenAdsAreAllowed() {
        composeRule.setContent {
            AppShell(
                showBanner = true,
                banner = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("fake-ad-banner")
                    )
                },
                content = { Box(Modifier.fillMaxSize().testTag("navigation-content")) }
            )
        }

        composeRule.onNodeWithTag("navigation-content").assertIsDisplayed()
        composeRule.onNodeWithTag("fake-ad-banner").assertIsDisplayed()
    }

    @Test
    fun appShell_omitsBannerWhenConsentDoesNotAllowAds() {
        composeRule.setContent {
            AppShell(
                showBanner = false,
                banner = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("fake-ad-banner")
                    )
                },
                content = { Box(Modifier.fillMaxSize().testTag("navigation-content")) }
            )
        }

        composeRule.onNodeWithTag("fake-ad-banner").assertDoesNotExist()
    }
}
