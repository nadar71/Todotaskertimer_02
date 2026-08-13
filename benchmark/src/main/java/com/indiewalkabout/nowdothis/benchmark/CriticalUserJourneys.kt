package com.indiewalkabout.nowdothis.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.indiewalkabout.nowdothis"
internal const val TASK_LIST_TAG = "task-list"

private const val FIXTURE_AUTHORITY = "com.indiewalkabout.nowdothis.benchmark-fixture"
private const val FIXTURE_METHOD = "prepare"
private const val RESET_METHOD = "reset"
private const val WAIT_TIMEOUT_MILLIS = 10_000L

internal fun MacrobenchmarkScope.startFromHome() {
    pressHome()
    startActivityAndWait()
}

internal fun MacrobenchmarkScope.openTaskEditor(device: UiDevice) {
    device.shellTap(device.waitForTag("task-add"))
    device.waitForTag("task-title")
}

internal fun MacrobenchmarkScope.scrollTaskList(device: UiDevice) {
    val taskList = device.waitForTag(TASK_LIST_TAG)
    val bounds = taskList.visibleBounds
    val x = bounds.centerX()
    val top = bounds.top + bounds.height() / 4
    val bottom = bounds.bottom - bounds.height() / 4
    repeat(3) { device.executeShellCommand("input swipe $x $bottom $x $top 180") }
    repeat(3) { device.executeShellCommand("input swipe $x $top $x $bottom 180") }
}

internal fun UiDevice.shellBack() {
    executeShellCommand("input keyevent KEYCODE_BACK")
}

internal fun UiDevice.prepareTaskFixture() {
    resetTargetAppData()
    executeShellCommand(
        "content call --uri content://$FIXTURE_AUTHORITY --method $FIXTURE_METHOD"
    )
    executeShellCommand("am force-stop $TARGET_PACKAGE")
}

internal fun UiDevice.resetTargetAppData() {
    executeShellCommand(
        "content call --uri content://$FIXTURE_AUTHORITY --method $RESET_METHOD"
    )
    executeShellCommand("am force-stop $TARGET_PACKAGE")
}

internal fun UiDevice.waitForTag(tag: String): UiObject2 =
    requireNotNull(
        wait(Until.findObject(By.res(tag)), WAIT_TIMEOUT_MILLIS)
    ) { "Timed out waiting for test tag '$tag'" }

private fun UiDevice.shellTap(target: UiObject2) {
    val bounds = target.visibleBounds
    executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}")
}
