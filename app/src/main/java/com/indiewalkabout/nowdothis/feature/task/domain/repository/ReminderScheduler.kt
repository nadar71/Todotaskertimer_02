package com.indiewalkabout.nowdothis.feature.task.domain.repository

enum class ReminderScheduleResult {
    EXACT,
    INEXACT,
    FAILED
}

interface ReminderScheduler {
    suspend fun schedule(taskId: Int, triggerAt: Long): ReminderScheduleResult
    suspend fun cancel(taskId: Int)
    suspend fun reconcile()
}
