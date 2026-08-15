package com.indiewalkabout.nowdothis.feature.portability.domain.usecase

import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityException
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityResult
import com.indiewalkabout.nowdothis.feature.portability.domain.model.WriteFailed
import com.indiewalkabout.nowdothis.feature.portability.domain.repository.PortabilityRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class CreateBackup @Inject constructor(
    private val repository: PortabilityRepository
) {
    suspend operator fun invoke(reference: DocumentReference): PortabilityResult = try {
        repository.createBackup(reference)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: PortabilityException) {
        PortabilityResult.Failed(failure.error)
    } catch (_: Exception) {
        PortabilityResult.Failed(WriteFailed)
    }
}
