package com.indiewalkabout.nowdothis.feature.quickcapture

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.quickcapture.di.QuickCaptureWidgetEntryPoint
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetIntents
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetReceiver
import dagger.hilt.android.EntryPointAccessors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

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
        assertTrue(receiversFor(AppWidgetManager.ACTION_APPWIDGET_UPDATE).contains(component))
        assertTrue(receiversFor(Intent.ACTION_LOCALE_CHANGED).contains(component))
    }

    @Test
    fun addAndOpenOperations_areImmutableAndDoNotCollide() {
        val add = PendingIntent.getActivity(
            context,
            0,
            QuickCaptureWidgetIntents.add(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            QuickCaptureWidgetIntents.open(context, 42),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        assertFalse(add == open)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertTrue(add.isImmutable)
            assertTrue(open.isImmutable)
        }
        add.cancel()
        open.cancel()
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
}
