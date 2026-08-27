package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.intl.Locale
import com.indiewalkabout.nowdothis.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar

@Composable
fun RecurrenceEditor(
    state: RecurrenceEditorState,
    recurrenceError: TaskEditorFieldError?,
    recurrenceEndError: TaskEditorFieldError?,
    onEvent: (TaskEditorEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag("task-recurrence-editor"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RecurrenceKindMenu(state.kind, onEvent)
        when (state.kind) {
            RecurrenceEditorKind.NONE -> Unit
            RecurrenceEditorKind.INTERVAL -> IntervalControls(state, recurrenceError, onEvent)
            RecurrenceEditorKind.SELECTED_WEEKDAYS -> {
                WeekdayControls(state, recurrenceError, onEvent)
            }
            RecurrenceEditorKind.MONTHLY_DAY -> {
                MonthlyDayControls(state, recurrenceError, onEvent)
            }
            RecurrenceEditorKind.MONTHLY_ORDINAL -> {
                MonthlyOrdinalControls(state, recurrenceError, onEvent)
            }
        }
        if (state.kind != RecurrenceEditorKind.NONE) {
            BasisControl(state.basis, onEvent)
        }
        if (state.kind != RecurrenceEditorKind.NONE || state.endAt != null) {
            RecurrenceEndControl(
                value = state.endAt,
                error = recurrenceEndError,
                onValueChange = { onEvent(TaskEditorEvent.UpdateRecurrenceEndAt(it)) }
            )
        }
        if (recurrenceError == TaskEditorFieldError.DUE_REQUIRED) {
            RecurrenceErrorText(
                text = stringResource(R.string.task_editor_error_recurrence_due_required)
            )
        } else if (recurrenceError == TaskEditorFieldError.RECURRENCE_INCOMPLETE) {
            RecurrenceErrorText(
                text = stringResource(R.string.task_editor_error_recurrence_incomplete)
            )
        }
    }
}

@Composable
private fun RecurrenceKindMenu(
    selected: RecurrenceEditorKind,
    onEvent: (TaskEditorEvent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        RecurrenceLabel(stringResource(R.string.task_editor_recurrence_label))
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("task-recurrence-kind")
        ) {
            Text(recurrenceKindLabel(selected))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RecurrenceEditorKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(recurrenceKindLabel(kind)) },
                    onClick = {
                        expanded = false
                        onEvent(TaskEditorEvent.SelectRecurrenceKind(kind))
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("task-recurrence-kind-option-${kind.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun IntervalControls(
    state: RecurrenceEditorState,
    error: TaskEditorFieldError?,
    onEvent: (TaskEditorEvent) -> Unit
) {
    NumericStepper(
        label = stringResource(R.string.task_editor_recurrence_every_label),
        value = state.intervalEvery,
        valueTag = "task-recurrence-interval-value",
        decrementTag = "task-recurrence-interval-decrement",
        incrementTag = "task-recurrence-interval-increment",
        onValueChange = { onEvent(TaskEditorEvent.UpdateRecurrenceIntervalEvery(it)) }
    )
    IntervalUnitMenu(state.intervalUnit, onEvent)
    if (error == TaskEditorFieldError.RECURRENCE_INTERVAL_OUT_OF_RANGE) {
        RecurrenceErrorText(stringResource(R.string.task_editor_error_recurrence_interval))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayControls(
    state: RecurrenceEditorState,
    error: TaskEditorFieldError?,
    onEvent: (TaskEditorEvent) -> Unit
) {
    val weekdays = localeOrderedWeekdays()
    RecurrenceLabel(stringResource(R.string.task_editor_recurrence_weekdays_label))
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task-recurrence-weekdays")
            .semantics { isTraversalGroup = true },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        weekdays.forEachIndexed { index, weekday ->
            val isSelected = weekday in state.selectedWeekdays
            FilterChip(
                selected = isSelected,
                onClick = { onEvent(TaskEditorEvent.ToggleRecurrenceWeekday(weekday)) },
                label = { Text(weekdayShortLabel(weekday)) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("task-recurrence-weekday-${weekday.name.lowercase()}")
                    .semantics {
                        role = Role.Checkbox
                        selected = isSelected
                        traversalIndex = index.toFloat()
                    }
            )
        }
    }
    if (error == TaskEditorFieldError.RECURRENCE_WEEKDAY_REQUIRED) {
        RecurrenceErrorText(
            stringResource(R.string.task_editor_error_recurrence_weekday_required)
        )
    }
}

@Composable
private fun MonthlyDayControls(
    state: RecurrenceEditorState,
    error: TaskEditorFieldError?,
    onEvent: (TaskEditorEvent) -> Unit
) {
    NumericStepper(
        label = stringResource(R.string.task_editor_recurrence_anchor_label),
        value = state.monthlyAnchorDay,
        valueTag = "task-recurrence-monthly-anchor-value",
        decrementTag = "task-recurrence-monthly-anchor-decrement",
        incrementTag = "task-recurrence-monthly-anchor-increment",
        onValueChange = { onEvent(TaskEditorEvent.UpdateRecurrenceMonthlyAnchorDay(it)) }
    )
    MonthlyEveryControl(state.monthlyEvery, onEvent)
    when (error) {
        TaskEditorFieldError.RECURRENCE_ANCHOR_OUT_OF_RANGE -> RecurrenceErrorText(
            stringResource(R.string.task_editor_error_recurrence_anchor)
        )
        TaskEditorFieldError.RECURRENCE_INTERVAL_OUT_OF_RANGE -> RecurrenceErrorText(
            stringResource(R.string.task_editor_error_recurrence_interval)
        )
        else -> Unit
    }
}

@Composable
private fun MonthlyOrdinalControls(
    state: RecurrenceEditorState,
    error: TaskEditorFieldError?,
    onEvent: (TaskEditorEvent) -> Unit
) {
    OrdinalMenu(state.ordinal, onEvent)
    OrdinalWeekdayMenu(state.ordinalWeekday, onEvent)
    MonthlyEveryControl(state.monthlyEvery, onEvent)
    if (error == TaskEditorFieldError.RECURRENCE_INTERVAL_OUT_OF_RANGE) {
        RecurrenceErrorText(stringResource(R.string.task_editor_error_recurrence_interval))
    }
}

@Composable
private fun MonthlyEveryControl(value: Int?, onEvent: (TaskEditorEvent) -> Unit) {
    NumericStepper(
        label = stringResource(R.string.task_editor_recurrence_every_months_label),
        value = value,
        valueTag = "task-recurrence-monthly-every-value",
        decrementTag = "task-recurrence-monthly-every-decrement",
        incrementTag = "task-recurrence-monthly-every-increment",
        onValueChange = { onEvent(TaskEditorEvent.UpdateRecurrenceMonthlyEvery(it)) }
    )
}

@Composable
private fun NumericStepper(
    label: String,
    value: Int?,
    valueTag: String,
    decrementTag: String,
    incrementTag: String,
    onValueChange: (Int?) -> Unit
) {
    val decrementDescription = stringResource(
        R.string.task_editor_recurrence_decrement_field,
        label
    )
    val valueDescription = stringResource(R.string.task_editor_recurrence_value_field, label)
    val incrementDescription = stringResource(
        R.string.task_editor_recurrence_increment_field,
        label
    )
    Column {
        RecurrenceLabel(label)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { onValueChange(value?.minus(1)?.takeIf { it >= 1 }) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(decrementTag)
                    .semantics { contentDescription = decrementDescription }
            ) {
                Text(text = "-")
            }
            OutlinedTextField(
                value = value?.toString().orEmpty(),
                onValueChange = { input ->
                    if (input.isEmpty() || input.all(Char::isDigit)) {
                        onValueChange(input.toIntOrNull())
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(valueTag)
                    .semantics { contentDescription = valueDescription },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            IconButton(
                onClick = { onValueChange(value?.plus(1) ?: 1) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(incrementTag)
                    .semantics { contentDescription = incrementDescription }
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun IntervalUnitMenu(
    selected: RecurrenceEditorIntervalUnit?,
    onEvent: (TaskEditorEvent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    RecurrenceMenu(
        label = stringResource(R.string.task_editor_recurrence_unit_label),
        value = selected?.let { intervalUnitLabel(it) }
            ?: stringResource(R.string.task_editor_recurrence_choose),
        tag = "task-recurrence-interval-unit",
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        RecurrenceEditorIntervalUnit.entries.forEach { unit ->
            DropdownMenuItem(
                text = { Text(intervalUnitLabel(unit)) },
                onClick = {
                    expanded = false
                    onEvent(TaskEditorEvent.SelectRecurrenceIntervalUnit(unit))
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("task-recurrence-interval-unit-${unit.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun OrdinalMenu(
    selected: RecurrenceEditorOrdinal?,
    onEvent: (TaskEditorEvent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    RecurrenceMenu(
        label = stringResource(R.string.task_editor_recurrence_ordinal_label),
        value = selected?.let { ordinalLabel(it) }
            ?: stringResource(R.string.task_editor_recurrence_choose),
        tag = "task-recurrence-ordinal",
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        RecurrenceEditorOrdinal.entries.forEach { ordinal ->
            DropdownMenuItem(
                text = { Text(ordinalLabel(ordinal)) },
                onClick = {
                    expanded = false
                    onEvent(TaskEditorEvent.SelectRecurrenceOrdinal(ordinal))
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("task-recurrence-ordinal-${ordinal.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun OrdinalWeekdayMenu(
    selected: RecurrenceEditorWeekday?,
    onEvent: (TaskEditorEvent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val weekdays = localeOrderedWeekdays()
    RecurrenceMenu(
        label = stringResource(R.string.task_editor_recurrence_ordinal_weekday_label),
        value = selected?.let { weekdayLongLabel(it) }
            ?: stringResource(R.string.task_editor_recurrence_choose),
        tag = "task-recurrence-ordinal-weekday",
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        weekdays.forEachIndexed { index, weekday ->
            DropdownMenuItem(
                text = { Text(weekdayLongLabel(weekday)) },
                onClick = {
                    expanded = false
                    onEvent(TaskEditorEvent.SelectRecurrenceOrdinalWeekday(weekday))
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("task-recurrence-ordinal-weekday-${weekday.name.lowercase()}")
                    .semantics {
                        role = Role.RadioButton
                        this.selected = selected == weekday
                        traversalIndex = index.toFloat()
                    }
            )
        }
    }
}

@Composable
private fun RecurrenceMenu(
    label: String,
    value: String,
    tag: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        RecurrenceLabel(label)
        OutlinedButton(
            onClick = { onExpandedChange(true) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(tag)
        ) {
            Text(value)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            content()
        }
    }
}

@Composable
private fun BasisControl(
    selected: RecurrenceEditorBasis?,
    onEvent: (TaskEditorEvent) -> Unit
) {
    Column(modifier = Modifier.testTag("task-recurrence-basis")) {
        RecurrenceLabel(stringResource(R.string.task_editor_recurrence_basis_label))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            RecurrenceEditorBasis.entries.forEachIndexed { index, basis ->
                SegmentedButton(
                    selected = selected == basis,
                    onClick = { onEvent(TaskEditorEvent.SelectRecurrenceBasis(basis)) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index,
                        RecurrenceEditorBasis.entries.size
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("task-recurrence-basis-${basis.name.lowercase()}")
                        .semantics {
                            role = Role.RadioButton
                            this.selected = selected == basis
                        }
                ) {
                    Text(basisLabel(basis))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceEndControl(
    value: Long?,
    error: TaskEditorFieldError?,
    onValueChange: (Long?) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(modifier = Modifier.testTag("task-recurrence-end")) {
        RecurrenceLabel(stringResource(R.string.task_editor_recurrence_end_label))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { showPicker = true },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    value?.let {
                        DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE)
                    } ?: stringResource(R.string.task_editor_not_set)
                )
            }
            if (value != null) {
                IconButton(
                    onClick = { onValueChange(null) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("task-recurrence-end-clear")
                ) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(
                            R.string.task_editor_clear_value,
                            stringResource(R.string.task_editor_recurrence_end_label)
                        )
                    )
                }
            }
        }
        recurrenceEndErrorText(error)?.let { RecurrenceErrorText(it) }
    }
    if (showPicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = value?.toRecurrenceDatePickerMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(
                        pickerState.selectedDateMillis
                            ?.recurrenceUtcDate()
                            ?.endOfRecurrenceLocalDayMillis()
                    )
                    showPicker = false
                }) {
                    Text(stringResource(R.string.task_editor_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.task_editor_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun recurrenceEndErrorText(error: TaskEditorFieldError?): String? = when (error) {
    TaskEditorFieldError.END_WITHOUT_RECURRENCE -> {
        stringResource(R.string.task_editor_error_end_without_recurrence)
    }
    TaskEditorFieldError.END_BEFORE_DUE -> {
        stringResource(R.string.task_editor_error_end_before_due)
    }
    else -> null
}

@Composable
private fun RecurrenceLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall
    )
}

@Composable
private fun RecurrenceErrorText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun recurrenceKindLabel(kind: RecurrenceEditorKind): String = when (kind) {
    RecurrenceEditorKind.NONE -> stringResource(R.string.task_recurrence_none)
    RecurrenceEditorKind.INTERVAL -> stringResource(R.string.task_recurrence_interval)
    RecurrenceEditorKind.SELECTED_WEEKDAYS -> {
        stringResource(R.string.task_recurrence_selected_weekdays)
    }
    RecurrenceEditorKind.MONTHLY_DAY -> stringResource(R.string.task_recurrence_monthly_day)
    RecurrenceEditorKind.MONTHLY_ORDINAL -> {
        stringResource(R.string.task_recurrence_monthly_ordinal)
    }
}

@Composable
private fun intervalUnitLabel(unit: RecurrenceEditorIntervalUnit): String = when (unit) {
    RecurrenceEditorIntervalUnit.DAYS -> stringResource(R.string.task_recurrence_unit_days)
    RecurrenceEditorIntervalUnit.WEEKS -> stringResource(R.string.task_recurrence_unit_weeks)
}

@Composable
private fun basisLabel(basis: RecurrenceEditorBasis): String = when (basis) {
    RecurrenceEditorBasis.SCHEDULED_DATE -> {
        stringResource(R.string.task_recurrence_basis_scheduled)
    }
    RecurrenceEditorBasis.COMPLETION_DATE -> {
        stringResource(R.string.task_recurrence_basis_completion)
    }
}

@Composable
private fun ordinalLabel(ordinal: RecurrenceEditorOrdinal): String = when (ordinal) {
    RecurrenceEditorOrdinal.FIRST -> stringResource(R.string.task_recurrence_ordinal_first)
    RecurrenceEditorOrdinal.SECOND -> stringResource(R.string.task_recurrence_ordinal_second)
    RecurrenceEditorOrdinal.THIRD -> stringResource(R.string.task_recurrence_ordinal_third)
    RecurrenceEditorOrdinal.FOURTH -> stringResource(R.string.task_recurrence_ordinal_fourth)
    RecurrenceEditorOrdinal.LAST -> stringResource(R.string.task_recurrence_ordinal_last)
}

@Composable
private fun weekdayShortLabel(weekday: RecurrenceEditorWeekday): String = when (weekday) {
    RecurrenceEditorWeekday.MONDAY -> stringResource(R.string.task_weekday_monday_short)
    RecurrenceEditorWeekday.TUESDAY -> stringResource(R.string.task_weekday_tuesday_short)
    RecurrenceEditorWeekday.WEDNESDAY -> stringResource(R.string.task_weekday_wednesday_short)
    RecurrenceEditorWeekday.THURSDAY -> stringResource(R.string.task_weekday_thursday_short)
    RecurrenceEditorWeekday.FRIDAY -> stringResource(R.string.task_weekday_friday_short)
    RecurrenceEditorWeekday.SATURDAY -> stringResource(R.string.task_weekday_saturday_short)
    RecurrenceEditorWeekday.SUNDAY -> stringResource(R.string.task_weekday_sunday_short)
}

@Composable
private fun weekdayLongLabel(weekday: RecurrenceEditorWeekday): String = when (weekday) {
    RecurrenceEditorWeekday.MONDAY -> stringResource(R.string.task_weekday_monday)
    RecurrenceEditorWeekday.TUESDAY -> stringResource(R.string.task_weekday_tuesday)
    RecurrenceEditorWeekday.WEDNESDAY -> stringResource(R.string.task_weekday_wednesday)
    RecurrenceEditorWeekday.THURSDAY -> stringResource(R.string.task_weekday_thursday)
    RecurrenceEditorWeekday.FRIDAY -> stringResource(R.string.task_weekday_friday)
    RecurrenceEditorWeekday.SATURDAY -> stringResource(R.string.task_weekday_saturday)
    RecurrenceEditorWeekday.SUNDAY -> stringResource(R.string.task_weekday_sunday)
}

@Composable
private fun localeOrderedWeekdays(): List<RecurrenceEditorWeekday> {
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    return remember(locale) {
        val first = when (Calendar.getInstance(locale).firstDayOfWeek) {
            Calendar.SUNDAY -> RecurrenceEditorWeekday.SUNDAY
            Calendar.MONDAY -> RecurrenceEditorWeekday.MONDAY
            Calendar.TUESDAY -> RecurrenceEditorWeekday.TUESDAY
            Calendar.WEDNESDAY -> RecurrenceEditorWeekday.WEDNESDAY
            Calendar.THURSDAY -> RecurrenceEditorWeekday.THURSDAY
            Calendar.FRIDAY -> RecurrenceEditorWeekday.FRIDAY
            Calendar.SATURDAY -> RecurrenceEditorWeekday.SATURDAY
            else -> RecurrenceEditorWeekday.MONDAY
        }
        val values = RecurrenceEditorWeekday.entries
        val firstIndex = values.indexOf(first)
        values.drop(firstIndex) + values.take(firstIndex)
    }
}

private fun Long.recurrenceUtcDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun Long.toRecurrenceDatePickerMillis(): Long =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

private fun LocalDate.endOfRecurrenceLocalDayMillis(): Long =
    plusDays(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli() - 1L
