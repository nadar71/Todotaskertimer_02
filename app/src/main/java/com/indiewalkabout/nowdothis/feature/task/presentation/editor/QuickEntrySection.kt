package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.indiewalkabout.nowdothis.R

@Composable
fun QuickEntrySection(
    input: String,
    summary: List<QuickEntrySummaryField>,
    issues: List<QuickEntryIssue>,
    onEvent: (TaskEditorEvent) -> Unit,
    modifier: Modifier = Modifier,
    categoryReadiness: CategoryReadiness = CategoryReadiness.READY,
    enabled: Boolean = true
) {
    val parseLabel = stringResource(R.string.quick_entry_parse)
    val retryLabel = stringResource(R.string.category_retry)
    Column(
        modifier = modifier.testTag("quick-entry-section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.quick_entry_label),
            style = MaterialTheme.typography.titleSmall
        )
        OutlinedTextField(
            value = input,
            onValueChange = { onEvent(TaskEditorEvent.UpdateQuickEntry(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick-entry-input"),
            label = { Text(stringResource(R.string.quick_entry_label)) },
            placeholder = { Text(stringResource(R.string.quick_entry_hint)) },
            minLines = 3,
            maxLines = 5,
            enabled = enabled
        )
        when (categoryReadiness) {
            CategoryReadiness.LOADING -> {
                val loadingDescription = stringResource(R.string.quick_entry_categories_loading)
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick-entry-categories-loading")
                        .semantics { contentDescription = loadingDescription }
                )
            }
            CategoryReadiness.ERROR -> {
                Text(
                    text = stringResource(R.string.category_load_failed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick-entry-categories-error")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(
                    onClick = { onEvent(TaskEditorEvent.RetryCategoryLoad) },
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("quick-entry-categories-retry")
                        .semantics { contentDescription = retryLabel }
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(retryLabel)
                }
            }
            CategoryReadiness.READY -> Unit
        }
        Button(
            onClick = { onEvent(TaskEditorEvent.ParseQuickEntry) },
            enabled = enabled &&
                categoryReadiness == CategoryReadiness.READY &&
                input.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("quick-entry-parse")
                .semantics { contentDescription = parseLabel }
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = parseLabel
            )
        }
        if (summary.isNotEmpty()) {
            val summaryText = stringResource(
                R.string.quick_entry_summary,
                summary.map { it.label() }.joinToString()
            )
            Text(
                text = summaryText,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick-entry-summary")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (issues.isNotEmpty()) {
            Text(
                text = issues.map { it.message() }.joinToString("\n"),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick-entry-issues")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun QuickEntrySummaryField.label(): String = stringResource(
    when (this) {
        QuickEntrySummaryField.TITLE -> R.string.quick_entry_field_title
        QuickEntrySummaryField.DUE_DATE -> R.string.quick_entry_field_due_date
        QuickEntrySummaryField.REMINDER -> R.string.quick_entry_field_reminder
        QuickEntrySummaryField.PRIORITY -> R.string.quick_entry_field_priority
        QuickEntrySummaryField.CATEGORY -> R.string.quick_entry_field_category
        QuickEntrySummaryField.RECURRENCE -> R.string.quick_entry_field_recurrence
    }
)

@Composable
private fun QuickEntryIssue.message(): String = stringResource(
    when (this) {
        QuickEntryIssue.EMPTY_INPUT -> R.string.quick_entry_issue_empty_input
        QuickEntryIssue.UNKNOWN_CATEGORY -> R.string.quick_entry_issue_unknown_category
        QuickEntryIssue.AMBIGUOUS_CATEGORY -> R.string.quick_entry_issue_ambiguous_category
        QuickEntryIssue.DUPLICATE_FIELD -> R.string.quick_entry_issue_duplicate_field
        QuickEntryIssue.AMBIGUOUS_RECURRENCE -> {
            R.string.quick_entry_issue_ambiguous_recurrence
        }
        QuickEntryIssue.RELATIVE_REMINDER_WITHOUT_DUE_DATE -> {
            R.string.quick_entry_issue_relative_reminder_without_due_date
        }
        QuickEntryIssue.PARSE_FAILED -> R.string.quick_entry_issue_parse_failed
    }
)
