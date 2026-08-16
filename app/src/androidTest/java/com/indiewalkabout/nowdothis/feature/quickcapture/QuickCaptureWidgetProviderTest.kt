package com.indiewalkabout.nowdothis.feature.quickcapture

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.quickcapture.di.QuickCaptureWidgetEntryPoint
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetReceiver
import dagger.hilt.android.EntryPointAccessors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class QuickCaptureWidgetProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun providerDependencies_resolveAsProcessSingletonsThroughTheHiltEntryPoint() {
        val first = entryPoint()
        val second = entryPoint()

        assertSame(first.loadQuickCaptureTasks(), second.loadQuickCaptureTasks())
        assertSame(first.completeQuickCaptureTask(), second.completeQuickCaptureTask())
        assertSame(first.quickCaptureWidgetUpdater(), second.quickCaptureWidgetUpdater())
    }

    @Test
    fun receiver_isRegisteredForWidgetAndLocaleUpdatesWithProviderMetadata() {
        val component = ComponentName(context, QuickCaptureWidgetReceiver::class.java)
        val receiverInfo = context.packageManager.getReceiverInfo(
            component,
            PackageManager.GET_META_DATA
        )

        assertEquals(
            R.xml.quick_capture_widget_info,
            receiverInfo.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER)
        )
        assertFalse(receiverInfo.exported)
        assertTrue(receiversFor(AppWidgetManager.ACTION_APPWIDGET_UPDATE).contains(component))
        assertTrue(receiversFor(Intent.ACTION_LOCALE_CHANGED).contains(component))
    }

    @Test
    fun nonExportedReceiver_receivesSystemAppWidgetUpdate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, QuickCaptureWidgetReceiver::class.java)
        val host = RecordingAppWidgetHost(context)
        var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        lateinit var hostView: RecordingAppWidgetHostView

        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        try {
            instrumentation.runOnMainSync {
                host.startListening()
                appWidgetId = host.allocateAppWidgetId()
            }
            assertTrue(manager.bindAppWidgetIdIfAllowed(appWidgetId, provider))
            val providerInfo = requireNotNull(manager.getAppWidgetInfo(appWidgetId))
            instrumentation.runOnMainSync {
                hostView = host.createView(context, appWidgetId, providerInfo) as RecordingAppWidgetHostView
            }

            assertTrue("System AppWidget update was not delivered", hostView.awaitUpdate())
        } finally {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                host.deleteAppWidgetId(appWidgetId)
            }
            host.stopListening()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun entryPoint(): QuickCaptureWidgetEntryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        QuickCaptureWidgetEntryPoint::class.java
    )

    private fun receiversFor(action: String): List<ComponentName> =
        context.packageManager.queryBroadcastReceivers(
            Intent(action).setPackage(context.packageName),
            PackageManager.MATCH_ALL
        ).mapNotNull { it.activityInfo?.let { info -> ComponentName(info.packageName, info.name) } }

    private class RecordingAppWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo
        ): AppWidgetHostView = RecordingAppWidgetHostView(context)
    }

    private class RecordingAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
        private val updateReceived = CountDownLatch(1)

        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)
            if (remoteViews != null) updateReceived.countDown()
        }

        fun awaitUpdate(): Boolean = updateReceived.await(15, TimeUnit.SECONDS)
    }

    private companion object {
        const val HOST_ID = 0x5143
    }
}
