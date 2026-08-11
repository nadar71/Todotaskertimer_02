package com.indiewalkabout.nowdothis.feature.calendar.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.indiewalkabout.nowdothis.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun MonthGrid(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    taskCounts: Map<LocalDate, Int>,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = currentLocale()
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val firstOfMonth = visibleMonth.atDay(1)
    val leadingDays = (firstOfMonth.dayOfWeek.value - firstDay.value + DAYS_PER_WEEK) % DAYS_PER_WEEK
    val gridStart = firstOfMonth.minusDays(leadingDays.toLong())
    val weekdayFormatter = DateTimeFormatter.ofPattern("EEEEE", locale)

    Column(modifier = modifier.fillMaxWidth().testTag("calendar-grid")) {
        Row(Modifier.fillMaxWidth()) {
            repeat(DAYS_PER_WEEK) { index ->
                val date = gridStart.plusDays(index.toLong())
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = date.format(weekdayFormatter),
                        modifier = Modifier.testTag("calendar-weekday-$index"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        repeat(WEEKS_PER_GRID) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(DAYS_PER_WEEK) { day ->
                    val date = gridStart.plusDays((week * DAYS_PER_WEEK + day).toLong())
                    DayCell(
                        date = date,
                        inVisibleMonth = YearMonth.from(date) == visibleMonth,
                        selected = date == selectedDate,
                        today = date == today,
                        taskCount = taskCounts[date] ?: 0,
                        onClick = { onSelectDate(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inVisibleMonth: Boolean,
    selected: Boolean,
    today: Boolean,
    taskCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = currentLocale()
    val dateLabel = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    val descriptions = buildList {
        add(dateLabel)
        if (today) add(stringResource(R.string.calendar_today_description))
        if (selected) add(stringResource(R.string.calendar_selected_description))
        if (taskCount > 0) {
            add(pluralStringResource(R.plurals.calendar_task_count, taskCount, taskCount))
        }
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        inVisibleMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }

    Box(modifier = modifier.aspectRatio(1f).padding(2.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .matchParentSize()
                .testTag("calendar-day-$date")
                .semantics {
                    this.selected = selected
                    contentDescription = descriptions.joinToString(", ")
                },
            shape = MaterialTheme.shapes.small,
            color = containerColor,
            contentColor = contentColor,
            border = if (today) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
                if (taskCount > 0) {
                    Text(
                        text = taskCount.toString(),
                        modifier = Modifier.testTag("calendar-count-$date"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
internal fun currentLocale(): Locale =
    Locale.forLanguageTag(LocalLocale.current.toLanguageTag())

private const val DAYS_PER_WEEK = 7
private const val WEEKS_PER_GRID = 6
