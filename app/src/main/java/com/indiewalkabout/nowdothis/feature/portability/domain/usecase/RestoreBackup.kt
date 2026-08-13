package com.indiewalkabout.nowdothis.feature.portability.domain.usecase

import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupCandidate
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityException
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityResult
import com.indiewalkabout.nowdothis.feature.portability.domain.model.RestoreFailed
import com.indiewalkabout.nowdothis.feature.portability.domain.repository.PortabilityRepository
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class RestoreBackup @Inject constructor(
    private val repository: PortabilityRepository,
    private val reminderScheduler: ReminderScheduler
) {
    suspend operator fun invoke(candidate: BackupCandidate): PortabilityResult {
        val preRestoreTaskIds = try {
            repository.replaceAll(candidate)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: PortabilityException) {
            return PortabilityResult.Failed(failure.error)
        } catch (_: Exception) {
            return PortabilityResult.Failed(RestoreFailed)
        }

        var reminderWarning = false
        preRestoreTaskIds.sorted().forEach { taskId ->
            try {
                reminderScheduler.cancel(taskId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                reminderWarning = true
            }
        }
        try {
            reminderScheduler.reconcile()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            reminderWarning = true
        }

        return if (reminderWarning) {
            PortabilityResult.RestoredWithReminderWarning(candidate.summary)
        } else {
            PortabilityResult.Restored(candidate.summary)
        }
    }
}
