package com.indiewalkabout.nowdothis.core.time

fun interface AppClock {
    fun nowMillis(): Long
}
