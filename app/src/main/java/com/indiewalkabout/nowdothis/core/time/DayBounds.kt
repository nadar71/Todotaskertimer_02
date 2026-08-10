package com.indiewalkabout.nowdothis.core.time

import java.time.Instant
import java.time.ZoneId

data class DayBounds(val startInclusive: Long, val endExclusive: Long) {
    companion object {
        fun forEpochMillis(now: Long, zoneId: ZoneId): DayBounds {
            val date = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
            return DayBounds(
                date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            )
        }
    }
}
