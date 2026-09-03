package com.indiewalkabout.nowdothis.feature.ads.data

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.indiewalkabout.nowdothis.BuildConfig
import com.indiewalkabout.nowdothis.feature.ads.domain.AdsConsentState
import com.indiewalkabout.nowdothis.feature.ads.domain.AdsRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AdsConsentManager {
    private val initializationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var consentInformation: ConsentInformation? = null
    private var runtime: AdsRuntime? = null
    private var suppressAdsForTesting = false

    var state by mutableStateOf(AdsConsentState())
        private set

    fun requestConsent(activity: Activity) {
        if (suppressAdsForTesting) return
        val information = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = information
        information.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    publishState(activity.applicationContext)
                }
            },
            { publishState(activity.applicationContext) }
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            publishState(activity.applicationContext)
        }
    }

    internal fun setAdsSuppressedForTesting(suppressed: Boolean) {
        check(BuildConfig.DEBUG) { "Ad suppression is available only in debug builds" }
        suppressAdsForTesting = suppressed
        if (suppressed) state = AdsConsentState()
    }

    private fun publishState(context: Context) {
        val information = consentInformation ?: return
        val adsRuntime = runtime ?: AdsRuntime(
            initializeMobileAds = { onInitialized ->
                val applicationContext = context.applicationContext
                initializationScope.launch {
                    MobileAds.initialize(applicationContext) {
                        mainScope.launch { onInitialized() }
                    }
                }
            },
            onStateChanged = { state = it }
        ).also { runtime = it }
        adsRuntime.updateConsent(
            canRequestAds = information.canRequestAds(),
            isPrivacyOptionsRequired = information.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        )
    }
}
