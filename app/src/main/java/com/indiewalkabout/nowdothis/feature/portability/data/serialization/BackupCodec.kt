package com.indiewalkabout.nowdothis.feature.portability.data.serialization

import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
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

    internal fun decodeEnvelope(bytes: ByteArray): BackupEnvelope =
        json.decodeFromString(bytes.decodeToString(throwOnInvalidSequence = true))

    fun decode(bytes: ByteArray): PlanningBackup =
        json.decodeFromString<BackupDocumentV1>(
            bytes.decodeToString(throwOnInvalidSequence = true)
        ).toDomain()

    internal companion object {
        const val SUPPORTED_FORMAT = BackupDocumentV1.FORMAT
        const val SUPPORTED_VERSION = BackupDocumentV1.VERSION
    }
}

@Serializable
internal data class BackupEnvelope(
    val format: String,
    val version: Int
)
