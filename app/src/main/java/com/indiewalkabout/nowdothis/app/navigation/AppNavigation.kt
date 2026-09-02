package com.indiewalkabout.nowdothis.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.ads.domain.AdsConsentState
import com.indiewalkabout.nowdothis.feature.ads.presentation.AdMobBanner
import com.indiewalkabout.nowdothis.feature.calendar.navigation.CalendarKey
import com.indiewalkabout.nowdothis.feature.calendar.presentation.CalendarRoute
import com.indiewalkabout.nowdothis.feature.category.navigation.CategoryManagementKey
import com.indiewalkabout.nowdothis.feature.category.presentation.CategoryRoute
import com.indiewalkabout.nowdothis.feature.history.navigation.CompletionHistoryKey
import com.indiewalkabout.nowdothis.feature.history.presentation.HistoryRoute
import com.indiewalkabout.nowdothis.feature.portability.navigation.DataPortabilityKey
import com.indiewalkabout.nowdothis.feature.portability.presentation.PortabilityRoute
import com.indiewalkabout.nowdothis.feature.task.navigation.TaskEditorKey
import com.indiewalkabout.nowdothis.feature.task.navigation.TaskListKey
import com.indiewalkabout.nowdothis.feature.task.presentation.editor.TaskEditorRoute
import com.indiewalkabout.nowdothis.feature.task.presentation.list.TaskListRoute
import kotlinx.coroutines.flow.Flow

@Composable
fun AppNavigation(
    taskEditorRequests: Flow<TaskEditorRequest>,
    adsState: AdsConsentState,
    onPrivacyOptions: () -> Unit
) {
    val backStack = rememberNavBackStack(TaskListKey)
    val navigator = remember(backStack) { AppNavigator(backStack) }

    LaunchedEffect(taskEditorRequests) {
        taskEditorRequests.collect { request ->
            when (request) {
                TaskEditorRequest.Add -> navigator.openNewTask()
                is TaskEditorRequest.Open -> navigator.openTask(request.taskId)
            }
        }
    }

    AppShell(
        showBanner = adsState.showBanner,
        banner = { AdMobBanner(adUnitId = stringResource(R.string.admob_key_bottom_banner)) }
    ) {
        AppNavDisplay(
            navigator = navigator,
            showPrivacyOptions = adsState.isPrivacyOptionsRequired,
            onPrivacyOptions = onPrivacyOptions,
            modifier = Modifier.semantics { testTagsAsResourceId = true }
        )
    }
}

@Composable
internal fun AppShell(
    showBanner: Boolean,
    banner: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { content() }
        if (showBanner) banner()
    }
}

@Composable
private fun RootScaffold(
    selectedDestination: NavKey,
    navigator: AppNavigator,
    showAddTask: Boolean,
    content: @Composable () -> Unit
) {
    Scaffold(
        bottomBar = {
            RootNavigationBar(
                selectedDestination = selectedDestination,
                onSelect = navigator::selectRoot
            )
        },
        floatingActionButton = {
            if (showAddTask) {
                FloatingActionButton(
                    onClick = navigator::openNewTask,
                    modifier = Modifier.testTag("task-add")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_task))
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
private fun RootNavigationBar(
    selectedDestination: NavKey,
    onSelect: (NavKey) -> Unit
) {
    NavigationBar(modifier = Modifier.testTag("root-navigation")) {
        NavigationBarItem(
            selected = selectedDestination == TaskListKey,
            onClick = { if (selectedDestination != TaskListKey) onSelect(TaskListKey) },
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
            label = { Text(stringResource(R.string.tasks_title)) },
            modifier = Modifier.testTag("navigation-tasks")
        )
        NavigationBarItem(
            selected = selectedDestination == CalendarKey,
            onClick = { if (selectedDestination != CalendarKey) onSelect(CalendarKey) },
            icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            label = { Text(stringResource(R.string.calendar_title)) },
            modifier = Modifier.testTag("navigation-calendar")
        )
    }
}

@Composable
private fun AppNavDisplay(
    navigator: AppNavigator,
    showPrivacyOptions: Boolean,
    onPrivacyOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavDisplay(
        backStack = navigator.backStack,
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        onBack = { navigator.navigateBack() },
        entryProvider = entryProvider {
            entry<TaskListKey> {
                RootScaffold(TaskListKey, navigator, showAddTask = true) {
                    TaskListRoute(
                        onOpenTaskEditor = { navigator.openTaskEditor(it, null) },
                        onOpenCategoryManagement = navigator::openCategoryManagement,
                        onOpenCalendar = { navigator.selectRoot(CalendarKey) },
                        onOpenHistory = navigator::openCompletionHistory,
                        onOpenDataPortability = navigator::openDataPortability,
                        showPrivacyOptions = showPrivacyOptions,
                        onPrivacyOptions = onPrivacyOptions
                    )
                }
            }
            entry<CalendarKey> {
                RootScaffold(CalendarKey, navigator, showAddTask = false) {
                    CalendarRoute(onOpenTaskEditor = navigator::openTaskEditor)
                }
            }
            entry<TaskEditorKey> { key ->
                TaskEditorRoute(key = key, onNavigateBack = { navigator.navigateBack() })
            }
            entry<CategoryManagementKey> {
                CategoryRoute(onBack = { navigator.navigateBack() })
            }
            entry<CompletionHistoryKey> {
                HistoryRoute(onBack = { navigator.navigateBack() })
            }
            entry<DataPortabilityKey> {
                PortabilityRoute(onBack = { navigator.navigateBack() })
            }
        }
    )
}
