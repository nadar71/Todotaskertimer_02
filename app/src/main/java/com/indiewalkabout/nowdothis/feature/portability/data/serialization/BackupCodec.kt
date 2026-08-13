package com.indiewalkabout.nowdothis.feature.portability.data.serialization

import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
        encodeDefaults = true
    }
) {
    fun encode(backup: PlanningBackup): ByteArray =
        json.encodeToString(BackupDocumentV1.fromDomain(backup)).encodeToByteArray()

    fun decode(bytes: ByteArray): PlanningBackup =
        json.decodeFromString<BackupDocumentV1>(bytes.decodeToString()).toDomain()
}
