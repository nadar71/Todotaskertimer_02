package com.indiewalkabout.nowdothis.feature.portability.domain.model

@JvmInline
value class DocumentReference(val value: String)

class BackupCandidate internal constructor(
    val backup: PlanningBackup,
    val summary: BackupSummary
)
