package com.indiewalkabout.nowdothis.feature.portability.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference

@Composable
fun PortabilityRoute(
    onMessage: (PortabilityMessage) -> Unit = {},
    viewModel: PortabilityViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnMessage = rememberUpdatedState(onMessage)
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

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is PortabilityEffect.LaunchCreateDocument -> {
                        createDocumentLauncher.launch(effect.suggestedName)
                    }
                    PortabilityEffect.LaunchOpenDocument -> {
                        openDocumentLauncher.launch(JSON_COMPATIBLE_MIME_TYPES)
                    }
                    is PortabilityEffect.ShowMessage -> currentOnMessage.value(effect.message)
                }
            }
        }
    }
}

private val JSON_COMPATIBLE_MIME_TYPES = arrayOf(
    "application/json",
    "text/json",
    "text/plain"
)
