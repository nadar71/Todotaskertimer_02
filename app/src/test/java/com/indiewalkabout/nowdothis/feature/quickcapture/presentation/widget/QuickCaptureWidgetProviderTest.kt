package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.TextView
import androidx.glance.appwidget.AppWidgetId
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.repository.QuickCaptureTaskSource
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.usecase.LoadQuickCaptureTasks
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSections
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class QuickCaptureWidgetProviderTest {
    @Test
    fun providerCapacity_usesApprovedThresholdsForTheLargestHostHeight() {
        assertEquals(3, quickCaptureCapacityForHeight(200))
        assertEquals(5, quickCaptureCapacityForHeight(320))
        assertEquals(8, quickCaptureCapacityForHeight(464))
    }

    @Test
    fun everyStateLoad_performsAFreshCapacityBoundRead() = runTest {
        var reads = 0
        val loadTasks = LoadQuickCaptureTasks(
            QuickCaptureTaskSource {
                flow {
                    reads++
                    emit(TaskSections())
                }
            }
        )

        val first = loadQuickCaptureWidgetState(loadTasks, capacity = 3, inFlightTaskIds = emptySet())
        val second = loadQuickCaptureWidgetState(loadTasks, capacity = 3, inFlightTaskIds = emptySet())

        assertEquals(QuickCaptureWidgetState.Empty, first)
        assertEquals(QuickCaptureWidgetState.Empty, second)
        assertEquals(2, reads)
    }

    @Test
    fun stateLoad_mapsReadFailureToUnavailable() = runTest {
        val loadTasks = LoadQuickCaptureTasks(
            QuickCaptureTaskSource { flow { throw IllegalStateException("database unavailable") } }
        )

        val state = loadQuickCaptureWidgetState(
            loadTasks = loadTasks,
            capacity = 5,
            inFlightTaskIds = setOf(7)
        )

        assertEquals(QuickCaptureWidgetState.Unavailable, state)
    }

    @Test
    fun compositionFailure_installsRawLocalizedErrorRemoteViews() {
        val context = RuntimeEnvironment.getApplication()
        val manager = AppWidgetManager.getInstance(context)
        val shadowManager = shadowOf(manager)
        val appWidgetId = 73
        shadowManager.bindAppWidgetId(
            appWidgetId,
            ComponentName(context, QuickCaptureWidgetReceiver::class.java)
        )

        QuickCaptureWidget().onCompositionError(
            context = context,
            glanceId = AppWidgetId(appWidgetId),
            appWidgetId = appWidgetId,
            throwable = IllegalStateException("must not be rendered")
        )

        val errorText = shadowManager.getViewFor(appWidgetId)
            .findViewById<TextView>(R.id.quick_capture_widget_error_text)
        assertEquals(context.getString(R.string.quick_capture_widget_unavailable), errorText.text.toString())
    }

    @Test
    fun receiver_exposesTheProductionResponsiveWidget() {
        val widget = QuickCaptureWidgetReceiver().glanceAppWidget

        assertTrue(widget is QuickCaptureWidget)
        assertSame(QuickCaptureWidgetSizeMode, widget.sizeMode)
    }

    @Test
    fun manifest_registersReceiverForWidgetUpdatesAndLocaleChanges() {
        val context = RuntimeEnvironment.getApplication()
        val component = ComponentName(context, QuickCaptureWidgetReceiver::class.java)
        val receiverInfo = context.packageManager.getReceiverInfo(
            component,
            PackageManager.GET_META_DATA
        )

        assertEquals(
            R.xml.quick_capture_widget_info,
            receiverInfo.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER)
        )
        assertTrue(registeredReceivers(context.packageName, AppWidgetManager.ACTION_APPWIDGET_UPDATE).contains(component))
        assertTrue(registeredReceivers(context.packageName, Intent.ACTION_LOCALE_CHANGED).contains(component))
    }

    @Test
    fun providerDependencies_resolveFromTheHiltApplicationGraph() {
        val entryPoint = quickCaptureWidgetEntryPoint(RuntimeEnvironment.getApplication())

        assertNotNull(entryPoint.loadQuickCaptureTasks())
        assertNotNull(entryPoint.completeQuickCaptureTask())
        assertNotNull(entryPoint.quickCaptureWidgetUpdater())
    }

    private fun registeredReceivers(packageName: String, action: String): List<ComponentName> {
        val context = RuntimeEnvironment.getApplication()
        return context.packageManager.queryBroadcastReceivers(
            Intent(action).setPackage(packageName),
            PackageManager.MATCH_ALL
        ).mapNotNull { it.activityInfo?.let { info -> ComponentName(info.packageName, info.name) } }
    }
}
