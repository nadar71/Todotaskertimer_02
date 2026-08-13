package com.indiewalkabout.nowdothis.feature.portability.data.repository

import com.indiewalkabout.nowdothis.core.di.ApplicationDispatcher
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.portability.data.local.PlanningDataStore
import com.indiewalkabout.nowdothis.feature.portability.data.serialization.BackupCodec
import com.indiewalkabout.nowdothis.feature.portability.data.serialization.BackupValidator
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupCandidate
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupSummary
import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupValidationResult
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentTooLarge
import com.indiewalkabout.nowdothis.feature.portability.domain.model.InvalidBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityException
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityResult
import com.indiewalkabout.nowdothis.feature.portability.domain.model.ReadFailed
import com.indiewalkabout.nowdothis.feature.portability.domain.model.RestoreFailed
import com.indiewalkabout.nowdothis.feature.portability.domain.model.WriteFailed
import com.indiewalkabout.nowdothis.feature.portability.domain.repository.PortabilityRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class OfflinePortabilityRepository @Inject constructor(
    private val planningDataStore: PlanningDataStore,
    private val documentGateway: DocumentGateway,
    private val backupCodec: BackupCodec,
    private val backupValidator: BackupValidator,
    private val clock: AppClock,
    @param:ApplicationDispatcher private val dispatcher: CoroutineDispatcher
) : PortabilityRepository {
    override suspend fun createBackup(reference: DocumentReference): PortabilityResult.Exported =
        withContext(dispatcher) {
            try {
                val backup = planningDataStore.snapshot(clock.nowMillis())
                documentGateway.write(reference, backupCodec.encode(backup))
                PortabilityResult.Exported(backup.summary())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                throw PortabilityException(WriteFailed)
            }
        }

    override suspend fun inspectBackup(reference: DocumentReference): BackupCandidate =
        withContext(dispatcher) {
            val bytes = try {
                documentGateway.read(reference, BackupValidator.MAX_DOCUMENT_SIZE_BYTES)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: DocumentSizeLimitExceededException) {
                throw PortabilityException(DocumentTooLarge)
            } catch (_: Exception) {
                throw PortabilityException(ReadFailed)
            }

            val backup = try {
                backupCodec.decode(bytes)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                throw PortabilityException(InvalidBackup)
            }

            when (val validation = backupValidator.validate(backup, bytes.size.toLong())) {
                is BackupValidationResult.Valid -> BackupCandidate(backup, validation.summary)
                is BackupValidationResult.Invalid -> throw PortabilityException(validation.error)
            }
        }

    override suspend fun replaceAll(candidate: BackupCandidate): Set<Int> = withContext(dispatcher) {
        try {
            planningDataStore.replaceAll(candidate.backup)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            throw PortabilityException(RestoreFailed)
        }
    }
}

private fun PlanningBackup.summary() = BackupSummary(
    createdAtEpochMillis = createdAtEpochMillis,
    categoryCount = categories.size,
    taskCount = tasks.size,
    completedTaskCount = tasks.count { task -> task.isCompleted },
    subtaskCount = tasks.sumOf { task -> task.subtasks.size }
)
