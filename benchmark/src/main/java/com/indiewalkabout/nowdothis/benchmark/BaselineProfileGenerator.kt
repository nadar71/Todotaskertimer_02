package com.indiewalkabout.nowdothis.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun startup() {
        device.resetTargetAppData()
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true
        ) {
            startFromHome()
        }
    }

    @Test
    fun criticalUserJourneys() {
        device.prepareTaskFixture()
        baselineProfileRule.collect(packageName = TARGET_PACKAGE) {
            startFromHome()
            openTaskEditor(device)
            device.shellBack()
            device.waitForTag(TASK_LIST_TAG)
            scrollTaskList(device)
        }
    }
}
