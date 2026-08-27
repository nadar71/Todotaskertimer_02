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
        json.encodeToString(BackupDocumentV2.fromDomain(backup)).encodeToByteArray()

    internal fun decodeEnvelope(bytes: ByteArray): BackupEnvelope =
        json.decodeFromString(bytes.decodeToString(throwOnInvalidSequence = true))

    fun decode(bytes: ByteArray): PlanningBackup {
        val document = bytes.decodeToString(throwOnInvalidSequence = true)
        val envelope = json.decodeFromString<BackupEnvelope>(document)
        require(envelope.format == SUPPORTED_FORMAT) { "Unsupported backup format" }
        return when (envelope.version) {
            BackupDocumentV1.VERSION -> json.decodeFromString<BackupDocumentV1>(document).toDomain()
            BackupDocumentV2.VERSION -> json.decodeFromString<BackupDocumentV2>(document).toDomain()
            else -> throw IllegalArgumentException("Unsupported backup version: ${envelope.version}")
        }
    }

    internal companion object {
        const val SUPPORTED_FORMAT = PlanningBackup.FORMAT
        const val SUPPORTED_VERSION = PlanningBackup.CURRENT_VERSION
    }
}

@Serializable
internal data class BackupEnvelope(
    val format: String,
    val version: Int
)
