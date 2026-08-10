package com.indiewalkabout.nowdothis.core.notifications

interface AlarmGateway {
    val canScheduleExact: Boolean
    fun setExact(taskId: Int, triggerAt: Long): Boolean
    fun setInexact(taskId: Int, triggerAt: Long): Boolean
    fun cancel(taskId: Int)
}
