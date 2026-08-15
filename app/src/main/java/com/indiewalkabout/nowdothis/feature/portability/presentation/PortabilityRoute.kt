package com.indiewalkabout.nowdothis.feature.portability.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference

@Composable
fun PortabilityRoute(
    onBack: () -> Unit,
    viewModel: PortabilityViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        viewModel.onEvent(
            PortabilityEvent.BackupDestinationSelected(uri?.toString()?.let(::DocumentReference))
        )
    }
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.onEvent(
            PortabilityEvent.BackupSourceSelected(uri?.toString()?.let(::DocumentReference))
        )
    }

    LaunchedEffect(viewModel, lifecycleOwner, resources) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is PortabilityEffect.LaunchCreateDocument -> {
                        createDocumentLauncher.launch(effect.suggestedName)
                    }
                    PortabilityEffect.LaunchOpenDocument -> {
                        openDocumentLauncher.launch(JSON_COMPATIBLE_MIME_TYPES)
                    }
                    is PortabilityEffect.ShowMessage -> snackbarHostState.showSnackbar(
                        resources.getString(effect.message.messageRes())
                    )
                }
            }
        }
    }

    PortabilityScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onBack = onBack
    )
}

private val JSON_COMPATIBLE_MIME_TYPES = arrayOf(
    "application/json",
    "text/json",
    "text/plain"
)

private fun PortabilityMessage.messageRes(): Int = when (this) {
    is PortabilityMessage.Error -> error.messageRes()
    PortabilityMessage.ReminderWarning -> R.string.portability_restore_reminder_warning
}

private fun com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityError.messageRes(): Int = when (this) {
    com.indiewalkabout.nowdothis.feature.portability.domain.model.InvalidBackup -> R.string.portability_error_invalid_backup
    is com.indiewalkabout.nowdothis.feature.portability.domain.model.UnsupportedFutureVersion -> R.string.portability_error_unsupported_version
    com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentTooLarge -> R.string.portability_error_document_too_large
    com.indiewalkabout.nowdothis.feature.portability.domain.model.ReadFailed -> R.string.portability_error_read_failed
    com.indiewalkabout.nowdothis.feature.portability.domain.model.WriteFailed -> R.string.portability_error_write_failed
    com.indiewalkabout.nowdothis.feature.portability.domain.model.RestoreFailed -> R.string.portability_error_restore_failed
}
