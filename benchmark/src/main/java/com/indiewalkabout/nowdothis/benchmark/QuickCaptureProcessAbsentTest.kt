package com.indiewalkabout.nowdothis.benchmark

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickCaptureProcessAbsentTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.context
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun providerUpdateAndCompletionStartTheAbsentTargetProcessAndUseFreshRoomState() {
        resetFixture()
        device.executeShellCommand(
            "content call --uri content://$FIXTURE_AUTHORITY --method $PREPARE_METHOD"
        )
        forceStopTarget()
        assertTargetProcessAbsent()

        withWidgetHost { hostView ->
            hostView.awaitText(TASK_TITLE)

            stopTargetProcess()
            assertTargetProcessAbsent()
            val updateCount = hostView.updateCount
            assertTrue(hostView.clickCompletionFor(TASK_TITLE))

            waitUntil {
                val state = device.executeShellCommand(
                    "content call --uri content://$FIXTURE_AUTHORITY --method $QUERY_METHOD"
                )
                "original_completed=true" in state && "pending_count=0" in state
            }
            hostView.awaitUpdateAfter(updateCount)
            hostView.awaitTextAbsent(TASK_TITLE)
        }

        resetFixture()
        forceStopTarget()
    }

    private fun withWidgetHost(block: (RecordingAppWidgetHostView) -> Unit) {
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(TARGET_PACKAGE, WIDGET_RECEIVER_CLASS)
        val host = RecordingAppWidgetHost(context)
        var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        try {
            instrumentation.runOnMainSync {
                host.startListening()
                appWidgetId = host.allocateAppWidgetId()
            }
            assertTrue(manager.bindAppWidgetIdIfAllowed(appWidgetId, provider))
            val providerInfo = requireNotNull(manager.getAppWidgetInfo(appWidgetId))
            val hostView = instrumentation.runOnMainSyncWithResult {
                host.createView(context, appWidgetId, providerInfo) as RecordingAppWidgetHostView
            }
            block(hostView)
        } finally {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(appWidgetId)
            host.stopListening()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun resetFixture() {
        device.executeShellCommand(
            "content call --uri content://$FIXTURE_AUTHORITY --method $RESET_METHOD"
        )
    }

    private fun forceStopTarget() {
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        waitUntil { device.executeShellCommand("pidof $TARGET_PACKAGE").isBlank() }
    }

    private fun stopTargetProcess() {
        device.executeShellCommand("am stop-app $TARGET_PACKAGE")
        waitUntil { device.executeShellCommand("pidof $TARGET_PACKAGE").isBlank() }
    }

    private fun assertTargetProcessAbsent() {
        assertTrue(device.executeShellCommand("pidof $TARGET_PACKAGE").isBlank())
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("Condition was not met within $WAIT_TIMEOUT_MILLIS ms")
    }

    private fun <T> android.app.Instrumentation.runOnMainSyncWithResult(block: () -> T): T {
        var result: Any? = null
        runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private inner class RecordingAppWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo
        ): AppWidgetHostView = RecordingAppWidgetHostView(context)
    }

    private inner class RecordingAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
        @Volatile
        var updateCount = 0
            private set

        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)
            if (remoteViews != null) updateCount++
        }

        fun awaitText(text: String) = waitUntil { text in renderedTexts() }

        fun awaitTextAbsent(text: String) = waitUntil { text !in renderedTexts() }

        fun awaitUpdateAfter(previousCount: Int) = waitUntil { updateCount > previousCount }

        fun clickCompletionFor(title: String): Boolean =
            instrumentation.runOnMainSyncWithResult {
                val completionAction = descendants()
                    .filter { it.contentDescription?.toString()?.contains(title) == true }
                    .lastOrNull()
                    ?: return@runOnMainSyncWithResult false
                generateSequence(completionAction) { it.parent as? View }
                    .firstOrNull(View::isClickable)
                    ?.performClick() == true
            }

        private fun renderedTexts(): List<String> = instrumentation.runOnMainSyncWithResult {
            descendants().filterIsInstance<TextView>().map { it.text.toString() }
        }

        private fun descendants(): List<View> = buildList {
            fun addRecursively(view: View) {
                add(view)
                if (view is ViewGroup) {
                    repeat(view.childCount) { index -> addRecursively(view.getChildAt(index)) }
                }
            }
            addRecursively(this@RecordingAppWidgetHostView)
        }
    }

    private companion object {
        const val FIXTURE_AUTHORITY = "com.indiewalkabout.nowdothis.benchmark-fixture"
        const val PREPARE_METHOD = "prepare_quick_capture"
        const val QUERY_METHOD = "query_quick_capture"
        const val RESET_METHOD = "reset"
        const val TASK_TITLE = "Process absent task"
        const val WIDGET_RECEIVER_CLASS =
            "com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetReceiver"
        const val HOST_ID = 0x5145
        const val WAIT_TIMEOUT_MILLIS = 15_000L
        const val POLL_INTERVAL_MILLIS = 100L
    }
}
