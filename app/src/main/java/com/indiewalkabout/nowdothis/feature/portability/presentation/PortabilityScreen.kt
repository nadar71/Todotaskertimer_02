package com.indiewalkabout.nowdothis.feature.portability.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortabilityScreen(
    state: PortabilityUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (PortabilityEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.portability_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("portability-back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.portability_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PortabilityCommand(
                tag = "portability-create",
                label = stringResource(R.string.portability_create_backup),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                enabled = !state.isBusy,
                onClick = { onEvent(PortabilityEvent.CreateBackup) }
            )
            PortabilityCommand(
                tag = "portability-restore",
                label = stringResource(R.string.portability_restore_backup),
                icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                enabled = !state.isBusy,
                onClick = { onEvent(PortabilityEvent.RestoreBackup) }
            )
            state.candidate?.let { BackupPreview(it) }
            state.result?.let { ResultMessage(it) }
            if (state.isBusy) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.testTag("portability-progress"))
                }
            }
        }
    }

    if (state.showRestoreConfirmation) {
        RestoreConfirmation(
            enabled = !state.isBusy,
            onEvent = onEvent
        )
    }
}

@Composable
private fun PortabilityCommand(
    tag: String,
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .semantics { contentDescription = label }
            .testTag(tag)
    ) {
        icon()
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun BackupPreview(summary: BackupSummary) {
    val createdAt = Instant.ofEpochMilli(summary.createdAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .format(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(LocalLocale.current.platformLocale)
        )
    val description = stringResource(
        R.string.portability_summary_counts,
        pluralStringResource(
            R.plurals.portability_category_count,
            summary.categoryCount,
            summary.categoryCount
        ),
        pluralStringResource(R.plurals.portability_task_count, summary.taskCount, summary.taskCount),
        pluralStringResource(
            R.plurals.portability_completed_task_count,
            summary.completedTaskCount,
            summary.completedTaskCount
        ),
        pluralStringResource(
            R.plurals.portability_subtask_count,
            summary.subtaskCount,
            summary.subtaskCount
        )
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .semantics { contentDescription = "$createdAt. $description" }
            .testTag("portability-summary"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.portability_summary_created, createdAt),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(text = description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ResultMessage(result: PortabilityUiResult) {
    val message = when (result) {
        is PortabilityUiResult.Exported -> stringResource(R.string.portability_backup_created)
        is PortabilityUiResult.Restored -> stringResource(R.string.portability_restore_completed)
        is PortabilityUiResult.RestoredWithReminderWarning -> stringResource(
            R.string.portability_restore_reminder_warning
        )
    }
    Text(
        text = message,
        modifier = Modifier.padding(top = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun RestoreConfirmation(enabled: Boolean, onEvent: (PortabilityEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(PortabilityEvent.DismissRestore) },
        title = { Text(stringResource(R.string.portability_replace_title)) },
        text = { Text(stringResource(R.string.portability_replace_warning)) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(PortabilityEvent.ConfirmRestore) },
                enabled = enabled,
                modifier = Modifier.testTag("portability-confirm")
            ) {
                Text(stringResource(R.string.portability_replace_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(PortabilityEvent.DismissRestore) },
                enabled = enabled,
                modifier = Modifier.testTag("portability-cancel")
            ) {
                Text(stringResource(R.string.portability_replace_cancel))
            }
        }
    )
}
