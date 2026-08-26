package com.indiewalkabout.nowdothis.feature.portability.data.serialization

import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
            BackupDocumentV2.VERSION -> {
                validateV2RecurrenceShapes(document)
                json.decodeFromString<BackupDocumentV2>(document).toDomain()
            }
            else -> throw IllegalArgumentException("Unsupported backup version: ${envelope.version}")
        }
    }

    private fun validateV2RecurrenceShapes(document: String) {
        val root = json.parseToJsonElement(document).jsonObject
        val tasks = root.getValue("tasks").jsonArray
        tasks.forEach { taskElement ->
            val recurrence = taskElement.jsonObject.getValue("recurrence").jsonObject
            val kind = recurrence.getValue("kind").jsonPrimitive.content
            val expectedKeys = when (kind) {
                "NONE" -> setOf("kind")
                "INTERVAL" -> setOf("kind", "unit", "every", "basis")
                "SELECTED_WEEKDAYS" -> setOf("kind", "basis", "weekdays")
                "MONTHLY_DAY" -> setOf("kind", "basis", "anchorDay", "everyMonths")
                "MONTHLY_ORDINAL" -> setOf(
                    "kind",
                    "basis",
                    "ordinal",
                    "weekday",
                    "everyMonths"
                )
                else -> throw IllegalArgumentException("Unsupported v2 recurrence kind: $kind")
            }
            require(recurrence.keys == expectedKeys) {
                "V2 recurrence $kind must contain exactly $expectedKeys"
            }
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
