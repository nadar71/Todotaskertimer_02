package com.indiewalkabout.nowdothis.feature.quickcapture.presentation.widget

import android.content.Context
import android.content.res.Configuration
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.assertHasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasContentDescriptionEqualTo
import androidx.glance.testing.unit.assertHasNoClickAction
import androidx.glance.testing.unit.assertHasText
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureDueState
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureSnapshot
import com.indiewalkabout.nowdothis.feature.quickcapture.domain.model.QuickCaptureTask
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class QuickCaptureWidgetContentTest {
    @Test
    fun capacityFor_mapsOnlyApprovedResponsiveSizesToTheirExactRowCounts() {
        assertEquals(3, capacityFor(CompactWidgetSize))
        assertEquals(5, capacityFor(MediumWidgetSize))
        assertEquals(8, capacityFor(ExpandedWidgetSize))
    }

    @Test
    fun loading_rendersAStableLoadingStateWithoutTaskCommands() = runGlanceAppWidgetUnitTest {
        setContext(italianContext())
        setAppWidgetSize(CompactWidgetSize)
        provideComposable { QuickCaptureWidgetContent(QuickCaptureWidgetState.Loading) }

        onNode(hasTestTag("quick-capture-loading")).assertExists()
        onNode(hasTestTag("quick-capture-add")).assertDoesNotExist()
        onNode(hasTestTag("quick-capture-retry")).assertDoesNotExist()
    }

    @Test
    fun empty_rendersLocalizedMessageAndAddEditorAction() = runGlanceAppWidgetUnitTest {
        val context = italianContext()
        setContext(context)
        setAppWidgetSize(CompactWidgetSize)
        provideComposable { QuickCaptureWidgetContent(QuickCaptureWidgetState.Empty) }

        onNode(hasText("Nessuna attività in sospeso.")).assertExists()
        onNode(hasTestTag("quick-capture-add"))
            .assertHasStartActivityClickAction(QuickCaptureWidgetIntents.add(context))
            .assertHasContentDescriptionEqualTo("Aggiungi attività")
    }

    @Test
    fun unavailable_rendersLocalizedMessageAndRetryActionWithoutTechnicalDetails() = runGlanceAppWidgetUnitTest {
        setContext(italianContext())
        setAppWidgetSize(CompactWidgetSize)
        provideComposable { QuickCaptureWidgetContent(QuickCaptureWidgetState.Unavailable) }

        onNode(hasText("Impossibile aggiornare le attività.")).assertExists()
        onNode(hasTestTag("quick-capture-retry"))
            .assertHasRunCallbackClickAction<QuickCaptureRetryAction>()
            .assertHasContentDescriptionEqualTo("Riprova ad aggiornare le attività")
    }

    @Test
    fun compact_contentCapsRowsAtThree() = assertVisibleRows(CompactWidgetSize, 3)

    @Test
    fun medium_contentCapsRowsAtFive() = assertVisibleRows(MediumWidgetSize, 5)

    @Test
    fun expanded_contentCapsRowsAtEight() = assertVisibleRows(ExpandedWidgetSize, 8)

    @Test
    fun content_rendersOneLineTitlesLocalizedDueStatesAndOverdueAccent() = runGlanceAppWidgetUnitTest {
        setContext(italianContext())
        setAppWidgetSize(ExpandedWidgetSize)
        provideComposable {
            QuickCaptureWidgetContent(
                QuickCaptureWidgetState.Content(
                    QuickCaptureSnapshot(
                        listOf(
                            task(1, "Titolo in ritardo", QuickCaptureDueState.OVERDUE),
                            task(2, "Titolo di oggi", QuickCaptureDueState.TODAY),
                            task(3, "Titolo futuro", QuickCaptureDueState.UPCOMING)
                        )
                    )
                )
            )
        }

        onNode(hasTestTag("quick-capture-title-1")).assertHasText("Titolo in ritardo")
        onNode(hasTestTag("quick-capture-due-1")).assertHasText("In ritardo")
        onNode(hasTestTag("quick-capture-due-2")).assertHasText("Oggi")
        onNode(hasTestTag("quick-capture-due-3")).assertHasText("In arrivo")
        assertEquals(1, QuickCaptureWidgetDimensions.titleMaxLines)
        assertEquals(QuickCaptureWidgetColorRole.Overdue, colorRoleFor(QuickCaptureDueState.OVERDUE))
    }

    @Test
    fun content_exposesLocalizedOpenAndCompleteActionsWithTaskIdParameters() = runGlanceAppWidgetUnitTest {
        val context = englishContext()
        val task = task(41, "Pay bills", QuickCaptureDueState.TODAY)
        setContext(context)
        setAppWidgetSize(CompactWidgetSize)
        provideComposable {
            QuickCaptureWidgetContent(
                QuickCaptureWidgetState.Content(QuickCaptureSnapshot(listOf(task)))
            )
        }

        onNode(hasTestTag("quick-capture-title-41"))
            .assertHasStartActivityClickAction(QuickCaptureWidgetIntents.open(context, 41))
            .assertHasContentDescriptionEqualTo("Open Pay bills")
        onNode(hasTestTag("quick-capture-complete-41"))
            .assertHasRunCallbackClickAction<QuickCaptureCompleteAction>(
                actionParametersOf(QuickCaptureWidgetActionParameters.taskId to 41)
            )
            .assertHasContentDescriptionEqualTo("Mark Pay bills complete")
    }

    @Test
    fun inFlightCompletion_isDisabledAndDescribedAsCompleting() = runGlanceAppWidgetUnitTest {
        setContext(englishContext())
        setAppWidgetSize(CompactWidgetSize)
        provideComposable {
            QuickCaptureWidgetContent(
                QuickCaptureWidgetState.Content(
                    snapshot = QuickCaptureSnapshot(listOf(task(41, "Pay bills", QuickCaptureDueState.TODAY))),
                    inFlightTaskIds = setOf(41)
                )
            )
        }

        onNode(hasTestTag("quick-capture-complete-41"))
            .assertHasNoClickAction()
            .assertHasContentDescriptionEqualTo("Completing Pay bills")
    }

    private fun assertVisibleRows(size: androidx.compose.ui.unit.DpSize, count: Int) =
        runGlanceAppWidgetUnitTest {
            setContext(englishContext())
            setAppWidgetSize(size)
            provideComposable {
                QuickCaptureWidgetContent(
                    QuickCaptureWidgetState.Content(QuickCaptureSnapshot((1..9).map(::task)))
                )
            }

            onNode(hasTestTag("quick-capture-row-$count")).assertExists()
            onNode(hasTestTag("quick-capture-row-${count + 1}")).assertDoesNotExist()
        }

    private fun task(
        id: Int,
        title: String = "Task $id",
        dueState: QuickCaptureDueState = QuickCaptureDueState.UPCOMING
    ) = QuickCaptureTask(id = id, title = title, dueAt = id.toLong(), dueState = dueState)

    private fun italianContext(): Context = contextFor(Locale.ITALIAN)

    private fun englishContext(): Context = contextFor(Locale.ENGLISH)

    private fun contextFor(locale: Locale): Context {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration)
        configuration.setLocale(locale)
        return application.createConfigurationContext(configuration)
    }
}
