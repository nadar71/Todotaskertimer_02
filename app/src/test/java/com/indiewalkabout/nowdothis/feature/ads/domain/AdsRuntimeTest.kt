package com.indiewalkabout.nowdothis.feature.ads.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsRuntimeTest {
    @Test
    fun initialState_hidesAdsAndPrivacyOptions() {
        val runtime = AdsRuntime(initializeMobileAds = { _ -> })

        assertFalse(runtime.state.showBanner)
        assertFalse(runtime.state.isPrivacyOptionsRequired)
    }

    @Test
    fun deniedConsent_keepsAdsHiddenButExposesRequiredPrivacyOptions() {
        var initializationCount = 0
        val runtime = AdsRuntime(initializeMobileAds = { initializationCount++ })

        runtime.updateConsent(canRequestAds = false, isPrivacyOptionsRequired = true)

        assertFalse(runtime.state.showBanner)
        assertTrue(runtime.state.isPrivacyOptionsRequired)
        assertEquals(0, initializationCount)
    }

    @Test
    fun allowedConsent_waitsForInitializationBeforeShowingBanner() {
        var initializationCount = 0
        var onInitialized: (() -> Unit)? = null
        val runtime = AdsRuntime(
            initializeMobileAds = { callback ->
                initializationCount++
                onInitialized = callback
            }
        )

        runtime.updateConsent(canRequestAds = true, isPrivacyOptionsRequired = false)
        runtime.updateConsent(canRequestAds = true, isPrivacyOptionsRequired = true)

        assertFalse(runtime.state.showBanner)
        onInitialized?.invoke()

        assertTrue(runtime.state.showBanner)
        assertTrue(runtime.state.isPrivacyOptionsRequired)
        assertEquals(1, initializationCount)
    }

    @Test
    fun revokedConsent_hidesBannerWithoutReinitializingSdk() {
        var initializationCount = 0
        val runtime = AdsRuntime(initializeMobileAds = { callback ->
            initializationCount++
            callback()
        })

        runtime.updateConsent(canRequestAds = true, isPrivacyOptionsRequired = false)
        runtime.updateConsent(canRequestAds = false, isPrivacyOptionsRequired = true)

        assertFalse(runtime.state.showBanner)
        assertTrue(runtime.state.isPrivacyOptionsRequired)
        assertEquals(1, initializationCount)
    }
}
