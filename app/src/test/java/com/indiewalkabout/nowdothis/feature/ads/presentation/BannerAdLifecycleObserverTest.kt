package com.indiewalkabout.nowdothis.feature.ads.presentation

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class BannerAdLifecycleObserverTest {
    @Test
    fun lifecycle_pausesResumesAndDestroysBanner() {
        var pauseCount = 0
        var resumeCount = 0
        var destroyCount = 0
        val owner = TestLifecycleOwner()
        val observer = BannerAdLifecycleObserver(
            pause = { pauseCount++ },
            resume = { resumeCount++ },
            destroy = { destroyCount++ }
        )

        observer.onResume(owner)
        observer.onPause(owner)
        observer.onDestroy(owner)

        assertEquals(1, resumeCount)
        assertEquals(1, pauseCount)
        assertEquals(1, destroyCount)
    }

    @Test
    fun dispose_destroysBannerAtMostOnce() {
        var destroyCount = 0
        val owner = TestLifecycleOwner()
        val observer = BannerAdLifecycleObserver(
            pause = {},
            resume = {},
            destroy = { destroyCount++ }
        )
        observer.dispose()
        observer.onDestroy(owner)

        assertEquals(1, destroyCount)
    }
}

private class TestLifecycleOwner : LifecycleOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this)
}
