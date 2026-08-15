package com.indiewalkabout.nowdothis.feature.portability.domain.repository

import com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupCandidate
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityResult

interface PortabilityRepository {
    suspend fun createBackup(reference: DocumentReference): PortabilityResult.Exported

    suspend fun inspectBackup(reference: DocumentReference): BackupCandidate

    suspend fun replaceAll(candidate: BackupCandidate): Set<Int>
}
