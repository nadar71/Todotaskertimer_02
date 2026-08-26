package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.task.navigation.TaskEditorKey

@Composable
fun TaskEditorRoute(
    key: TaskEditorKey,
    onNavigateBack: () -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel<
        TaskEditorViewModel,
        TaskEditorViewModel.Factory
    > { factory -> factory.create(key) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onEvent(TaskEditorEvent.NotificationPermissionResult(granted))
    }
    val exactAlarmAccess = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onEvent(TaskEditorEvent.RefreshExactAlarmAccess)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TaskEditorEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(resources.getString(effect.messageRes))
                }
                TaskEditorEffect.RequestNotificationPermission -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                TaskEditorEffect.RequestExactAlarmAccess -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            "package:${context.packageName}".toUri()
                        )
                        runCatching { exactAlarmAccess.launch(intent) }
                            .onFailure {
                                viewModel.onEvent(TaskEditorEvent.RefreshExactAlarmAccess)
                                snackbarHostState.showSnackbar(
                                    resources.getString(
                                        R.string.task_editor_exact_alarm_settings_failed
                                    )
                                )
                            }
                    }
                }
                TaskEditorEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    TaskEditorScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onBack = onNavigateBack
    )
}
