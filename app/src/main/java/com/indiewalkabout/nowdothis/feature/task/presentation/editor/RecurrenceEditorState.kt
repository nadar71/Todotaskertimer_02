package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.MonthlyOrdinalValue
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import java.time.DayOfWeek
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
enum class RecurrenceEditorKind {
    NONE,
    INTERVAL,
    SELECTED_WEEKDAYS,
    MONTHLY_DAY,
    MONTHLY_ORDINAL
}

@Serializable
enum class RecurrenceEditorBasis {
    SCHEDULED_DATE,
    COMPLETION_DATE
}

@Serializable
enum class RecurrenceEditorIntervalUnit {
    DAYS,
    WEEKS
}

@Serializable
enum class RecurrenceEditorWeekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}

@Serializable
enum class RecurrenceEditorOrdinal {
    FIRST,
    SECOND,
    THIRD,
    FOURTH,
    LAST
}

@ConsistentCopyVisibility
@Serializable(with = RecurrenceEditorStateSerializer::class)
data class RecurrenceEditorState private constructor(
    val kind: RecurrenceEditorKind = RecurrenceEditorKind.NONE,
    val basis: RecurrenceEditorBasis? = null,
    val intervalUnit: RecurrenceEditorIntervalUnit? = null,
    val intervalEvery: Int? = null,
    private val selectedWeekdaySnapshot: List<RecurrenceEditorWeekday> = emptyList(),
    val monthlyEvery: Int? = null,
    val monthlyAnchorDay: Int? = null,
    val ordinal: RecurrenceEditorOrdinal? = null,
    val ordinalWeekday: RecurrenceEditorWeekday? = null,
    val endAt: Long? = null
) {
    constructor(
        kind: RecurrenceEditorKind = RecurrenceEditorKind.NONE,
        basis: RecurrenceEditorBasis? = null,
        intervalUnit: RecurrenceEditorIntervalUnit? = null,
        intervalEvery: Int? = null,
        monthlyEvery: Int? = null,
        monthlyAnchorDay: Int? = null,
        ordinal: RecurrenceEditorOrdinal? = null,
        ordinalWeekday: RecurrenceEditorWeekday? = null,
        endAt: Long? = null
    ) : this(
        kind = kind,
        basis = basis,
        intervalUnit = intervalUnit,
        intervalEvery = intervalEvery,
        selectedWeekdaySnapshot = emptyList(),
        monthlyEvery = monthlyEvery,
        monthlyAnchorDay = monthlyAnchorDay,
        ordinal = ordinal,
        ordinalWeekday = ordinalWeekday,
        endAt = endAt
    )

    internal constructor(snapshot: SerializedRecurrenceEditorState) : this(
        kind = snapshot.kind,
        basis = snapshot.basis,
        intervalUnit = snapshot.intervalUnit,
        intervalEvery = snapshot.intervalEvery,
        selectedWeekdaySnapshot = snapshot.selectedWeekdays.toList(),
        monthlyEvery = snapshot.monthlyEvery,
        monthlyAnchorDay = snapshot.monthlyAnchorDay,
        ordinal = snapshot.ordinal,
        ordinalWeekday = snapshot.ordinalWeekday,
        endAt = snapshot.endAt
    )

    val selectedWeekdays: Set<RecurrenceEditorWeekday>
        get() = selectedWeekdaySnapshot.toSet()

    internal fun serializedSelectedWeekdays(): List<RecurrenceEditorWeekday> =
        selectedWeekdaySnapshot.toList()

    fun withBasis(value: RecurrenceEditorBasis?): RecurrenceEditorState = copy(basis = value)

    fun withIntervalUnit(
        value: RecurrenceEditorIntervalUnit?
    ): RecurrenceEditorState = copy(intervalUnit = value)

    fun withIntervalEvery(value: Int?): RecurrenceEditorState = copy(intervalEvery = value)

    fun withMonthlyEvery(value: Int?): RecurrenceEditorState = copy(monthlyEvery = value)

    fun withMonthlyAnchorDay(value: Int?): RecurrenceEditorState =
        copy(monthlyAnchorDay = value)

    fun withOrdinal(value: RecurrenceEditorOrdinal?): RecurrenceEditorState =
        copy(ordinal = value)

    fun withOrdinalWeekday(value: RecurrenceEditorWeekday?): RecurrenceEditorState =
        copy(ordinalWeekday = value)

    fun withEndAt(value: Long?): RecurrenceEditorState = copy(endAt = value)

    fun withSelectedWeekdays(
        weekdays: Iterable<RecurrenceEditorWeekday>
    ): RecurrenceEditorState = copy(selectedWeekdaySnapshot = weekdays.distinct())

    fun toggledWeekday(weekday: RecurrenceEditorWeekday): RecurrenceEditorState =
        withSelectedWeekdays(
            if (weekday in selectedWeekdaySnapshot) {
                selectedWeekdaySnapshot - weekday
            } else {
                selectedWeekdaySnapshot + weekday
            }
        )

    internal fun hasCanonicalShape(): Boolean {
        if (selectedWeekdaySnapshot.size != selectedWeekdaySnapshot.distinct().size) return false
        return when (kind) {
            RecurrenceEditorKind.NONE ->
                basis == null &&
                    intervalUnit == null &&
                    intervalEvery == null &&
                    selectedWeekdaySnapshot.isEmpty() &&
                    monthlyEvery == null &&
                    monthlyAnchorDay == null &&
                    ordinal == null &&
                    ordinalWeekday == null
            RecurrenceEditorKind.INTERVAL ->
                selectedWeekdaySnapshot.isEmpty() &&
                    monthlyEvery == null &&
                    monthlyAnchorDay == null &&
                    ordinal == null &&
                    ordinalWeekday == null
            RecurrenceEditorKind.SELECTED_WEEKDAYS ->
                intervalUnit == null &&
                    intervalEvery == null &&
                    monthlyEvery == null &&
                    monthlyAnchorDay == null &&
                    ordinal == null &&
                    ordinalWeekday == null
            RecurrenceEditorKind.MONTHLY_DAY ->
                intervalUnit == null &&
                    intervalEvery == null &&
                    selectedWeekdaySnapshot.isEmpty() &&
                    ordinal == null &&
                    ordinalWeekday == null
            RecurrenceEditorKind.MONTHLY_ORDINAL ->
                intervalUnit == null &&
                    intervalEvery == null &&
                    selectedWeekdaySnapshot.isEmpty() &&
                    monthlyAnchorDay == null
        }
    }

    companion object {
        fun forKind(kind: RecurrenceEditorKind, endAt: Long? = null): RecurrenceEditorState =
            when (kind) {
                RecurrenceEditorKind.NONE -> newRecurrenceEditorState()
                RecurrenceEditorKind.INTERVAL -> newRecurrenceEditorState(
                    kind = kind,
                    basis = RecurrenceEditorBasis.COMPLETION_DATE,
                    intervalUnit = RecurrenceEditorIntervalUnit.DAYS,
                    intervalEvery = 1,
                    endAt = endAt
                )
                RecurrenceEditorKind.SELECTED_WEEKDAYS -> newRecurrenceEditorState(
                    kind = kind,
                    basis = RecurrenceEditorBasis.SCHEDULED_DATE,
                    endAt = endAt
                )
                RecurrenceEditorKind.MONTHLY_DAY -> newRecurrenceEditorState(
                    kind = kind,
                    basis = RecurrenceEditorBasis.SCHEDULED_DATE,
                    monthlyEvery = 1,
                    monthlyAnchorDay = 1,
                    endAt = endAt
                )
                RecurrenceEditorKind.MONTHLY_ORDINAL -> newRecurrenceEditorState(
                    kind = kind,
                    basis = RecurrenceEditorBasis.SCHEDULED_DATE,
                    monthlyEvery = 1,
                    ordinal = RecurrenceEditorOrdinal.FIRST,
                    ordinalWeekday = RecurrenceEditorWeekday.MONDAY,
                    endAt = endAt
                )
            }

        fun fromRule(rule: RecurrenceRule, endAt: Long?): RecurrenceEditorState = when (rule) {
            RecurrenceRule.None -> newRecurrenceEditorState(endAt = endAt)
            is RecurrenceRule.Interval -> newRecurrenceEditorState(
                kind = RecurrenceEditorKind.INTERVAL,
                basis = rule.basis.toEditorBasis(),
                intervalUnit = rule.unit.toEditorUnit(),
                intervalEvery = rule.every,
                endAt = endAt
            )
            is RecurrenceRule.SelectedWeekdays -> newRecurrenceEditorState(
                kind = RecurrenceEditorKind.SELECTED_WEEKDAYS,
                basis = rule.basis.toEditorBasis(),
                endAt = endAt
            ).withSelectedWeekdays(rule.weekdays.map { it.toEditorWeekday() })
            is RecurrenceRule.MonthlyDay -> newRecurrenceEditorState(
                kind = RecurrenceEditorKind.MONTHLY_DAY,
                basis = rule.basis.toEditorBasis(),
                monthlyEvery = rule.everyMonths,
                monthlyAnchorDay = rule.anchorDay,
                endAt = endAt
            )
            is RecurrenceRule.MonthlyOrdinal -> newRecurrenceEditorState(
                kind = RecurrenceEditorKind.MONTHLY_ORDINAL,
                basis = rule.basis.toEditorBasis(),
                monthlyEvery = rule.everyMonths,
                ordinal = rule.ordinal.toEditorOrdinal(),
                ordinalWeekday = rule.weekday.toEditorWeekday(),
                endAt = endAt
            )
        }

    }
}

internal object RecurrenceEditorStateSerializer : KSerializer<RecurrenceEditorState> {
    private val delegate = SerializedRecurrenceEditorState.serializer()

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: RecurrenceEditorState) {
        encoder.encodeSerializableValue(
            delegate,
            SerializedRecurrenceEditorState(
                kind = value.kind,
                basis = value.basis,
                intervalUnit = value.intervalUnit,
                intervalEvery = value.intervalEvery,
                selectedWeekdays = value.serializedSelectedWeekdays(),
                monthlyEvery = value.monthlyEvery,
                monthlyAnchorDay = value.monthlyAnchorDay,
                ordinal = value.ordinal,
                ordinalWeekday = value.ordinalWeekday,
                endAt = value.endAt
            )
        )
    }

    override fun deserialize(decoder: Decoder): RecurrenceEditorState {
        val snapshot = decoder.decodeSerializableValue(delegate)
        return RecurrenceEditorState(snapshot)
    }
}

@Serializable
internal data class SerializedRecurrenceEditorState(
    val kind: RecurrenceEditorKind = RecurrenceEditorKind.NONE,
    val basis: RecurrenceEditorBasis? = null,
    val intervalUnit: RecurrenceEditorIntervalUnit? = null,
    val intervalEvery: Int? = null,
    val selectedWeekdays: List<RecurrenceEditorWeekday> = emptyList(),
    val monthlyEvery: Int? = null,
    val monthlyAnchorDay: Int? = null,
    val ordinal: RecurrenceEditorOrdinal? = null,
    val ordinalWeekday: RecurrenceEditorWeekday? = null,
    val endAt: Long? = null
)

private fun newRecurrenceEditorState(
    kind: RecurrenceEditorKind = RecurrenceEditorKind.NONE,
    basis: RecurrenceEditorBasis? = null,
    intervalUnit: RecurrenceEditorIntervalUnit? = null,
    intervalEvery: Int? = null,
    monthlyEvery: Int? = null,
    monthlyAnchorDay: Int? = null,
    ordinal: RecurrenceEditorOrdinal? = null,
    ordinalWeekday: RecurrenceEditorWeekday? = null,
    endAt: Long? = null
): RecurrenceEditorState = RecurrenceEditorState(
    kind = kind,
    basis = basis,
    intervalUnit = intervalUnit,
    intervalEvery = intervalEvery,
    monthlyEvery = monthlyEvery,
    monthlyAnchorDay = monthlyAnchorDay,
    ordinal = ordinal,
    ordinalWeekday = ordinalWeekday,
    endAt = endAt
)

internal sealed interface RecurrenceRuleDraftResult {
    data class Valid(val rule: RecurrenceRule) : RecurrenceRuleDraftResult
    data class Invalid(val error: TaskEditorFieldError) : RecurrenceRuleDraftResult
}

internal fun RecurrenceEditorState.toValidatedRule(): RecurrenceRuleDraftResult {
    if (!hasCanonicalShape()) {
        return RecurrenceRuleDraftResult.Invalid(TaskEditorFieldError.RECURRENCE_INCOMPLETE)
    }
    if (kind == RecurrenceEditorKind.NONE) {
        return RecurrenceRuleDraftResult.Valid(RecurrenceRule.None)
    }
    val domainBasis = basis?.toDomainBasis()
        ?: return RecurrenceRuleDraftResult.Invalid(TaskEditorFieldError.RECURRENCE_INCOMPLETE)
    return when (kind) {
        RecurrenceEditorKind.NONE -> RecurrenceRuleDraftResult.Valid(RecurrenceRule.None)
        RecurrenceEditorKind.INTERVAL -> {
            val every = intervalEvery
            if (every == null || every !in 1..999) {
                RecurrenceRuleDraftResult.Invalid(
                    TaskEditorFieldError.RECURRENCE_INTERVAL_OUT_OF_RANGE
                )
            } else {
                val unit = intervalUnit?.toDomainUnit()
                    ?: return RecurrenceRuleDraftResult.Invalid(
                        TaskEditorFieldError.RECURRENCE_INCOMPLETE
                    )
                RecurrenceRuleDraftResult.Valid(RecurrenceRule.Interval(unit, every, domainBasis))
            }
        }
        RecurrenceEditorKind.SELECTED_WEEKDAYS -> {
            if (selectedWeekdays.isEmpty()) {
                RecurrenceRuleDraftResult.Invalid(
                    TaskEditorFieldError.RECURRENCE_WEEKDAY_REQUIRED
                )
            } else {
                RecurrenceRuleDraftResult.Valid(
                    RecurrenceRule.SelectedWeekdays(
                        selectedWeekdays.mapTo(linkedSetOf()) { it.toDayOfWeek() },
                        domainBasis
                    )
                )
            }
        }
        RecurrenceEditorKind.MONTHLY_DAY -> {
            val anchor = monthlyAnchorDay
            val every = monthlyEvery
            when {
                anchor == null || anchor !in 1..31 -> RecurrenceRuleDraftResult.Invalid(
                    TaskEditorFieldError.RECURRENCE_ANCHOR_OUT_OF_RANGE
                )
                every == null || every !in 1..999 -> RecurrenceRuleDraftResult.Invalid(
                    TaskEditorFieldError.RECURRENCE_INTERVAL_OUT_OF_RANGE
                )
                else -> RecurrenceRuleDraftResult.Valid(
                    RecurrenceRule.MonthlyDay(anchor, every, domainBasis)
                )
            }
        }
        RecurrenceEditorKind.MONTHLY_ORDINAL -> {
            val every = monthlyEvery
            if (every == null || every !in 1..999) {
                RecurrenceRuleDraftResult.Invalid(
                    TaskEditorFieldError.RECURRENCE_INTERVAL_OUT_OF_RANGE
                )
            } else {
                val domainOrdinal = ordinal?.toDomainOrdinal()
                    ?: return RecurrenceRuleDraftResult.Invalid(
                        TaskEditorFieldError.RECURRENCE_INCOMPLETE
                    )
                val weekday = ordinalWeekday?.toDayOfWeek()
                    ?: return RecurrenceRuleDraftResult.Invalid(
                        TaskEditorFieldError.RECURRENCE_INCOMPLETE
                    )
                RecurrenceRuleDraftResult.Valid(
                    RecurrenceRule.MonthlyOrdinal(
                        domainOrdinal,
                        weekday,
                        every,
                        domainBasis
                    )
                )
            }
        }
    }
}

private fun RecurrenceEditorBasis.toDomainBasis(): RecurrenceBasis =
    RecurrenceBasis.valueOf(name)

private fun RecurrenceBasis.toEditorBasis(): RecurrenceEditorBasis =
    RecurrenceEditorBasis.valueOf(name)

private fun RecurrenceEditorIntervalUnit.toDomainUnit(): IntervalUnit = IntervalUnit.valueOf(name)

private fun IntervalUnit.toEditorUnit(): RecurrenceEditorIntervalUnit =
    RecurrenceEditorIntervalUnit.valueOf(name)

private fun RecurrenceEditorWeekday.toDayOfWeek(): DayOfWeek = DayOfWeek.valueOf(name)

private fun DayOfWeek.toEditorWeekday(): RecurrenceEditorWeekday =
    RecurrenceEditorWeekday.valueOf(name)

private fun RecurrenceEditorOrdinal.toDomainOrdinal(): MonthlyOrdinalValue =
    MonthlyOrdinalValue.valueOf(name)

private fun MonthlyOrdinalValue.toEditorOrdinal(): RecurrenceEditorOrdinal =
    RecurrenceEditorOrdinal.valueOf(name)
