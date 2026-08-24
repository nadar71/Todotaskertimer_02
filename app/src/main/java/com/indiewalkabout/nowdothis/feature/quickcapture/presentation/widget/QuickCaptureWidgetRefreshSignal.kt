package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class QuickCaptureWidgetRefreshSignal @Inject constructor() {
    private val mutableVersion = MutableStateFlow(0L)

    val version: StateFlow<Long> = mutableVersion.asStateFlow()

    fun invalidate() {
        mutableVersion.update { it + 1 }
    }
}
