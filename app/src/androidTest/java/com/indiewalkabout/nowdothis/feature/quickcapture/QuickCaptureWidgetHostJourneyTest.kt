package com.indiewalkabout.nowdothis.feature.quickcapture

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.updateAll
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.database.DebugDatabaseEntryPoint
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureDueState
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureSnapshot
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureTask
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.CompactWidgetSize
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.ExpandedWidgetSize
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.MediumWidgetSize
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidget
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetContent
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetReceiver
import com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget.QuickCaptureWidgetState
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import dagger.hilt.android.EntryPointAccessors
import java.io.FileInputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickCaptureWidgetHostJourneyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database: AppDatabase by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            DebugDatabaseEntryPoint::class.java
        ).database()
    }

    @Before
    fun clearDatabase() = runBlocking {
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }

    @After
    fun resetDatabase() = runBlocking {
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }

    @Test
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    fun directRemoteViewsRender_coversSizesStatesLocalesThemesLargeFontAndTargets() = runBlocking {
        val tasks = (1..9).map { ordinal ->
            QuickCaptureTask(
                id = ordinal,
                title = "Host task $ordinal with a deliberately long title",
                dueAt = ordinal.toLong(),
                dueState = when (ordinal) {
                    1 -> QuickCaptureDueState.OVERDUE
                    2 -> QuickCaptureDueState.TODAY
                    else -> QuickCaptureDueState.UPCOMING
                }
            )
        }
        val content = QuickCaptureWidgetState.Content(QuickCaptureSnapshot(tasks))

        assertCapacity(render(content, CompactWidgetSize, Locale.ENGLISH), visible = 3)
        assertCapacity(render(content, MediumWidgetSize, Locale.ENGLISH), visible = 5)
        assertCapacity(render(content, ExpandedWidgetSize, Locale.ENGLISH), visible = 8)

        val englishEmptyLight = render(
            QuickCaptureWidgetState.Empty,
            CompactWidgetSize,
            Locale.ENGLISH,
            nightMode = false,
            fontScale = 2f
        )
        val englishEmptyDark = render(
            QuickCaptureWidgetState.Empty,
            CompactWidgetSize,
            Locale.ENGLISH,
            nightMode = true,
            fontScale = 2f
        )
        val italianUnavailable = render(
            QuickCaptureWidgetState.Unavailable,
            CompactWidgetSize,
            Locale.ITALIAN,
            nightMode = true,
            fontScale = 2f
        )
        val italianContentLarge = render(
            QuickCaptureWidgetState.Content(QuickCaptureSnapshot(tasks.take(3))),
            CompactWidgetSize,
            Locale.ITALIAN,
            nightMode = true,
            fontScale = 2f
        )
        val englishContentLarge = render(
            QuickCaptureWidgetState.Content(QuickCaptureSnapshot(tasks.take(1))),
            CompactWidgetSize,
            Locale.ENGLISH,
            nightMode = false,
            fontScale = 2f
        )

        assertTrue("No pending tasks." in englishEmptyLight.texts())
        assertTrue("Add task" in englishEmptyLight.descriptions())
        assertTrue("Impossibile aggiornare le attivit\u00e0." in italianUnavailable.texts())
        assertTrue("Riprova ad aggiornare le attivit\u00e0" in italianUnavailable.descriptions())
        assertTrue("Da fare" in italianContentLarge.texts())
        assertTrue("In ritardo" in italianContentLarge.texts())
        assertTrue("Oggi" in italianContentLarge.texts())
        assertTrue("In arrivo" in italianContentLarge.texts())
        assertTrue("Apri ${tasks.first().title}" in italianContentLarge.descriptions())
        assertTrue("Segna ${tasks.first().title} come completata" in italianContentLarge.descriptions())
        assertTrue("Open ${tasks.first().title}" in englishContentLarge.descriptions())
        assertTrue("Mark ${tasks.first().title} complete" in englishContentLarge.descriptions())
        assertNotEquals(englishEmptyLight.backgroundPixel(), englishEmptyDark.backgroundPixel())

        listOf(
            englishEmptyLight,
            italianUnavailable,
            italianContentLarge,
            englishContentLarge
        ).forEach { rendered ->
            rendered.assertDescribedActionsMeetAvailable48DpTarget()
            rendered.assertSiblingTextsDoNotOverlap()
        }
    }

    @Test
    fun boundHost_updateAllRefreshesEveryWidgetInstance() {
        seedTasks(8)

        withWidgetHost(instanceCount = 2) { hostViews ->
            hostViews.forEach { view ->
                view.resize(CompactWidgetSize)
                view.awaitText("Bound task 3")
                assertFalse("Bound task 4" in view.texts())
            }

            val beforeRefresh = hostViews.associateWith { it.updateCount }
            seedRefreshTask()
            runBlocking { QuickCaptureWidget().updateAll(context) }

            hostViews.forEach { view ->
                view.awaitUpdateAfter(requireNotNull(beforeRefresh[view]))
                view.awaitText("Refresh reached every widget")
            }
        }
    }

    @Test
    fun boundHost_liveSystemNightModeChangeRefreshesQualifiedColors() {
        val originalNightMode = context.resources.configuration.isNightMode()
        try {
            setSystemNightMode(enabled = false)
            seedTasks(1)

            withWidgetHost(instanceCount = 1) { hostViews ->
                val hostView = hostViews.single()
                hostView.resize(CompactWidgetSize)
                hostView.awaitText("Bound task 1")
                val beforeLightRefresh = hostView.updateCount
                runBlocking { QuickCaptureWidget().updateAll(context) }
                hostView.awaitUpdateAfter(beforeLightRefresh)
                val lightTextColor = ContextCompat.getColor(
                    context,
                    R.color.quick_capture_widget_on_surface
                )
                hostView.awaitTextColor("Bound task 1", lightTextColor)
                val beforeThemeChange = hostView.updateCount

                setSystemNightMode(enabled = true)

                hostView.awaitUpdateAfter(beforeThemeChange)
                val darkTextColor = ContextCompat.getColor(
                    context,
                    R.color.quick_capture_widget_on_surface
                )
                assertNotEquals(lightTextColor, darkTextColor)
                hostView.awaitTextColor("Bound task 1", darkTextColor)
            }
        } finally {
            setSystemNightMode(originalNightMode)
        }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private suspend fun render(
        state: QuickCaptureWidgetState,
        size: DpSize,
        locale: Locale,
        nightMode: Boolean = false,
        fontScale: Float = 1f
    ): RenderedWidget {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            this.fontScale = fontScale
            uiMode = uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()
            uiMode = uiMode or if (nightMode) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
        }
        val configuredContext = context.createConfigurationContext(configuration)
        val remoteViews = GlanceRemoteViews().compose(
            context = configuredContext,
            size = size,
            content = { QuickCaptureWidgetContent(state) }
        ).remoteViews
        return instrumentation.runOnMainSyncWithResult {
            val parent = FrameLayout(configuredContext)
            val root = remoteViews.apply(configuredContext, parent)
            parent.addView(root)
            val density = configuredContext.resources.displayMetrics.density
            val width = (size.width.value * density).roundToInt()
            val height = (size.height.value * density).roundToInt()
            parent.measure(exactly(width), exactly(height))
            parent.layout(0, 0, width, height)
            RenderedWidget(parent, density)
        }
    }

    private fun assertCapacity(rendered: RenderedWidget, visible: Int) {
        val texts = rendered.texts()
        assertEquals(visible, texts.count { it.startsWith("Host task ") })
        assertTrue("Host task $visible with a deliberately long title" in texts)
        assertFalse("Host task ${visible + 1} with a deliberately long title" in texts)
    }

    private fun seedTasks(count: Int) = runBlocking(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        database.taskDao().insertTasks(
            (1..count).map { ordinal ->
                TaskEntity(
                    id = ordinal,
                    title = "Bound task $ordinal",
                    description = "Production AppWidgetHost resize evidence",
                    priority = "MEDIUM",
                    dueAt = today + ordinal * 60_000L,
                    createdAt = today,
                    updatedAt = today
                )
            }
        )
    }

    private fun seedRefreshTask() = runBlocking(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        database.taskDao().insertTask(
            TaskEntity(
                id = 99,
                title = "Refresh reached every widget",
                description = "All widget instances must refresh",
                priority = "HIGH",
                dueAt = now - 60_000L,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun withWidgetHost(
        instanceCount: Int,
        block: (List<RecordingAppWidgetHostView>) -> Unit
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, QuickCaptureWidgetReceiver::class.java)
        val host = RecordingAppWidgetHost(context)
        val appWidgetIds = mutableListOf<Int>()

        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        try {
            instrumentation.runOnMainSync { host.startListening() }
            val hostViews = (1..instanceCount).map {
                val appWidgetId = instrumentation.runOnMainSyncWithResult { host.allocateAppWidgetId() }
                appWidgetIds += appWidgetId
                assertTrue(manager.bindAppWidgetIdIfAllowed(appWidgetId, provider))
                val providerInfo = requireNotNull(manager.getAppWidgetInfo(appWidgetId))
                instrumentation.runOnMainSyncWithResult {
                    host.createView(context, appWidgetId, providerInfo) as RecordingAppWidgetHostView
                }
            }
            block(hostViews)
        } finally {
            appWidgetIds.forEach(host::deleteAppWidgetId)
            host.stopListening()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError("Condition was not met within $WAIT_TIMEOUT_MILLIS ms")
    }

    private fun setSystemNightMode(enabled: Boolean) {
        val value = if (enabled) "yes" else "no"
        instrumentation.uiAutomation.executeShellCommand("cmd uimode night $value").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
        waitUntil { context.resources.configuration.isNightMode() == enabled }
    }

    private fun <T> android.app.Instrumentation.runOnMainSyncWithResult(block: () -> T): T {
        var result: Any? = null
        runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private class RenderedWidget(
        private val root: ViewGroup,
        private val density: Float
    ) {
        fun texts(): List<String> = descendants().filterIsInstance<TextView>().map { it.text.toString() }

        fun descriptions(): List<String> = descendants().mapNotNull { it.contentDescription?.toString() }

        fun backgroundPixel(): Int {
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            return bitmap.getPixel(root.width / 2, root.height - (4 * density).roundToInt())
        }

        fun assertDescribedActionsMeetAvailable48DpTarget() {
            val minimum = (48 * density).roundToInt()
            val describedActions = descendants().filter { view ->
                view.contentDescription != null &&
                    generateSequence(view) { it.parent as? View }.any(View::isClickable)
            }
            assertTrue(describedActions.isNotEmpty())
            describedActions.forEach { view ->
                assertTrue("${view.contentDescription} width was ${view.width}px", view.width >= minimum)
                assertTrue("${view.contentDescription} height was ${view.height}px", view.height >= minimum)
            }
        }

        fun assertSiblingTextsDoNotOverlap() {
            descendants().filterIsInstance<ViewGroup>().forEach { parent ->
                val texts = (0 until parent.childCount)
                    .map(parent::getChildAt)
                    .filterIsInstance<TextView>()
                    .filter { it.visibility == View.VISIBLE && it.text.isNotEmpty() }
                texts.forEachIndexed { index, first ->
                    texts.drop(index + 1).forEach { second ->
                        val firstBounds = Rect(first.left, first.top, first.right, first.bottom)
                        val secondBounds = Rect(second.left, second.top, second.right, second.bottom)
                        assertFalse(
                            "Text overlapped: '${first.text}' and '${second.text}'",
                            Rect.intersects(firstBounds, secondBounds)
                        )
                    }
                }
            }
        }

        private fun descendants(): List<View> = root.descendants()
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
        var updateCount: Int = 0
            private set

        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)
            if (remoteViews != null) updateCount++
        }

        fun resize(size: DpSize) {
            instrumentation.runOnMainSync {
                updateAppWidgetSize(
                    Bundle(),
                    listOf(SizeF(size.width.value, size.height.value))
                )
                val density = resources.displayMetrics.density
                val width = (size.width.value * density).roundToInt()
                val height = (size.height.value * density).roundToInt()
                measure(exactly(width), exactly(height))
                layout(0, 0, width, height)
            }
        }

        fun awaitText(expected: String) {
            val deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MILLIS
            while (SystemClock.uptimeMillis() < deadline) {
                if (expected in texts()) return
                SystemClock.sleep(POLL_INTERVAL_MILLIS)
            }
            throw AssertionError(
                "Text '$expected' was not rendered; texts=${texts()}, updates=$updateCount, " +
                    "view=${width}x$height, options=" +
                    AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
            )
        }

        fun awaitUpdateAfter(previousCount: Int) = waitUntil { updateCount > previousCount }

        fun awaitTextColor(text: String, expectedColor: Int) = waitUntil {
            textColorOrNull(text) == expectedColor
        }

        private fun textColorOrNull(text: String): Int? = instrumentation.runOnMainSyncWithResult {
            descendants().filterIsInstance<TextView>()
                .firstOrNull { it.text.toString() == text }
                ?.currentTextColor
        }

        fun texts(): List<String> = instrumentation.runOnMainSyncWithResult {
            descendants().filterIsInstance<TextView>().map { it.text.toString() }
        }
    }

    private companion object {
        const val HOST_ID = 0x5146
        const val WAIT_TIMEOUT_MILLIS = 20_000L
        const val POLL_INTERVAL_MILLIS = 100L

        fun exactly(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

        fun View.descendants(): List<View> = buildList {
            fun addRecursively(view: View) {
                add(view)
                if (view is ViewGroup) {
                    repeat(view.childCount) { index -> addRecursively(view.getChildAt(index)) }
                }
            }
            addRecursively(this@descendants)
        }

        fun Configuration.isNightMode(): Boolean =
            uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
}
