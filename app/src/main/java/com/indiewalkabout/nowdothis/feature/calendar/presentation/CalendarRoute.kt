package com.indiewalkabout.nowdothis.feature.calendar.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CalendarRoute(
    onOpenTaskEditor: (taskId: Int?, initialDueAt: Long?) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CalendarEffect.OpenEditor -> {
                    onOpenTaskEditor(effect.taskId, effect.initialDueAt)
                }
            }
        }
    }

    CalendarScreen(state = state, onEvent = viewModel::onEvent)
}
