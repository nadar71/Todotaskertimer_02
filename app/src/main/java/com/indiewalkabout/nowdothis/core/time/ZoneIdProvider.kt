package com.indiewalkabout.nowdothis.core.time

import java.time.ZoneId

fun interface ZoneIdProvider {
    fun zoneId(): ZoneId
}
