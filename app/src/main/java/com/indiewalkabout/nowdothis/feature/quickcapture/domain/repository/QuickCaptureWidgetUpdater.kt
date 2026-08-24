package com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository

fun interface QuickCaptureWidgetUpdater {
    suspend fun updateAll()
}
