package com.indiewalkabout.nowdothis.feature.ads.domain

data class AdsConsentState(
    val canRequestAds: Boolean = false,
    val isPrivacyOptionsRequired: Boolean = false,
    val isMobileAdsInitialized: Boolean = false
) {
    val showBanner: Boolean
        get() = canRequestAds && isMobileAdsInitialized
}

class AdsRuntime(
    private val initializeMobileAds: (onInitialized: () -> Unit) -> Unit,
    private val onStateChanged: (AdsConsentState) -> Unit = {}
) {
    var state: AdsConsentState = AdsConsentState()
        private set

    private var mobileAdsInitializationStarted = false

    @Synchronized
    fun updateConsent(canRequestAds: Boolean, isPrivacyOptionsRequired: Boolean) {
        state = AdsConsentState(
            canRequestAds = canRequestAds,
            isPrivacyOptionsRequired = isPrivacyOptionsRequired,
            isMobileAdsInitialized = state.isMobileAdsInitialized
        )
        onStateChanged(state)
        if (canRequestAds && !mobileAdsInitializationStarted) {
            mobileAdsInitializationStarted = true
            initializeMobileAds(::onMobileAdsInitialized)
        }
    }

    @Synchronized
    private fun onMobileAdsInitialized() {
        state = state.copy(isMobileAdsInitialized = true)
        onStateChanged(state)
    }
}
