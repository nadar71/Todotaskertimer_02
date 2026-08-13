package com.indiewalkabout.nowdothis.feature.portability.presentation

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupSummary
import com.indiewalkabout.nowdothis.feature.task.presentation.list.TaskListTestActivity
import com.indiewalkabout.nowdothis.feature.task.presentation.list.TaskListTestContent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortabilityScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TaskListTestActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun commands_preview_andBack_areAccessibleAndDispatchEvents() {
        val events = mutableListOf<PortabilityEvent>()
        var backCount = 0
        setScreen(
            state = PortabilityUiState(candidate = summary()),
            onEvent = events::add,
            onBack = { backCount++ }
        )

        composeRule.onNodeWithTag("portability-back")
            .assertContentDescriptionEquals(context.getString(R.string.portability_back))
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("portability-create")
            .assertContentDescriptionEquals(context.getString(R.string.portability_create_backup))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("portability-restore")
            .assertContentDescriptionEquals(context.getString(R.string.portability_restore_backup))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("portability-summary").assertIsDisplayed()
        val createdAt = Instant.ofEpochMilli(summary().createdAtEpochMillis)
            .atZone(ZoneId.systemDefault())
            .format(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                    .withLocale(context.resources.configuration.locales[0])
            )
        composeRule.onNodeWithText(
            context.getString(R.string.portability_summary_created, createdAt)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(
                R.string.portability_summary_counts,
                context.resources.getQuantityString(R.plurals.portability_category_count, 3, 3),
                context.resources.getQuantityString(R.plurals.portability_task_count, 8, 8),
                context.resources.getQuantityString(R.plurals.portability_completed_task_count, 2, 2),
                context.resources.getQuantityString(R.plurals.portability_subtask_count, 5, 5)
            )
        )
            .assertIsDisplayed()

        assertEquals(1, backCount)
        assertEquals(
            listOf(PortabilityEvent.CreateBackup, PortabilityEvent.RestoreBackup),
            events
        )
    }

    @Test
    fun destructiveConfirmation_andBusyState_haveStableControls() {
        val events = mutableListOf<PortabilityEvent>()
        setScreen(
            state = PortabilityUiState(
                candidate = summary(),
                showRestoreConfirmation = true
            ),
            onEvent = events::add
        )

        composeRule.onNodeWithText(context.getString(R.string.portability_replace_warning)).assertIsDisplayed()
        composeRule.onNodeWithTag("portability-confirm").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("portability-cancel").assertIsEnabled().performClick()

        assertEquals(
            listOf(PortabilityEvent.ConfirmRestore, PortabilityEvent.DismissRestore),
            events
        )

        setScreen(state = PortabilityUiState(isBusy = true))
        composeRule.onNodeWithTag("portability-progress").assertIsDisplayed()
        composeRule.onNodeWithTag("portability-create").assertIsNotEnabled()
        composeRule.onNodeWithTag("portability-restore").assertIsNotEnabled()
    }

    @Test
    fun primaryAndEnglishCommandStrings_areLocalized() {
        val italian = Configuration(context.resources.configuration).apply {
            setLocale(Locale.ITALIAN)
        }
        val italianContext = context.createConfigurationContext(italian)
        assertEquals("Crea backup", italianContext.getString(R.string.portability_create_backup))
        val english = Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        val englishContext = context.createConfigurationContext(english)
        assertEquals("Create backup", englishContext.getString(R.string.portability_create_backup))
        assertEquals("Restore backup", englishContext.getString(R.string.portability_restore_backup))
    }

    private fun setScreen(
        state: PortabilityUiState,
        onEvent: (PortabilityEvent) -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeRule.runOnUiThread {
            TaskListTestContent.content = {
                MaterialTheme {
                    PortabilityScreen(
                        state = state,
                        snackbarHostState = SnackbarHostState(),
                        onEvent = onEvent,
                        onBack = onBack
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun summary() = BackupSummary(
        createdAtEpochMillis = 1_786_640_000_000L,
        categoryCount = 3,
        taskCount = 8,
        completedTaskCount = 2,
        subtaskCount = 5
    )
}
