package com.indiewalkabout.nowdothis.feature.task.domain.repository

enum class ReminderScheduleResult {
    EXACT,
    INEXACT,
    FAILED
}

enum class ReminderReconcileResult {
    SUCCESS,
    SOME_UNAVAILABLE
}

interface ReminderScheduler {
    suspend fun schedule(taskId: Int, triggerAt: Long): ReminderScheduleResult
    suspend fun cancel(taskId: Int)
    suspend fun reconcile()

    suspend fun reconcileWithResult(): ReminderReconcileResult {
        reconcile()
        return ReminderReconcileResult.SUCCESS
    }
}
