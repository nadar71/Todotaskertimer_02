package com.indiewalkabout.nowdothis.feature.task.domain.model

/**
 * Temporary legacy compatibility boundary for Room and the editor/parser migration.
 *
 * [RecurrenceRule] is the only recurrence domain source of truth. Remove this enum after
 * Tasks 6 and 7 migrate the remaining editor and parser references.
 */
@Deprecated(
    message = "Temporary legacy recurrence boundary. Use RecurrenceRule in domain code.",
    level = DeprecationLevel.WARNING
)
enum class RecurrenceType {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}
