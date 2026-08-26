package com.indiewalkabout.nowdothis.feature.portability.data.serialization

import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningCategory
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningSubtask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import com.indiewalkabout.nowdothis.feature.task.domain.model.IntervalUnit
import com.indiewalkabout.nowdothis.feature.task.domain.model.MonthlyOrdinalValue
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceBasis
import com.indiewalkabout.nowdothis.feature.task.domain.model.RecurrenceRule
import java.nio.charset.CharacterCodingException
import java.time.DayOfWeek
import java.time.Instant
import java.util.TimeZone
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    private val codec = BackupCodec()

    @Test
    fun encode_usesV2AndRoundTripsEveryRecurrenceRuleWithoutLoss() {
        val rules = listOf(
            RecurrenceRule.None,
            RecurrenceRule.Interval(IntervalUnit.DAYS, 3, RecurrenceBasis.COMPLETION_DATE),
            RecurrenceRule.Interval(IntervalUnit.WEEKS, 2, RecurrenceBasis.SCHEDULED_DATE),
            RecurrenceRule.SelectedWeekdays(
                setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY),
                RecurrenceBasis.COMPLETION_DATE
            ),
            RecurrenceRule.MonthlyDay(
                anchorDay = 31,
                everyMonths = 4,
                basis = RecurrenceBasis.SCHEDULED_DATE
            ),
            RecurrenceRule.MonthlyOrdinal(
                ordinal = MonthlyOrdinalValue.LAST,
                weekday = DayOfWeek.THURSDAY,
                everyMonths = 6,
                basis = RecurrenceBasis.COMPLETION_DATE
            )
        )
        val backup = sampleBackup().copy(
            tasks = rules.mapIndexed { index, rule ->
                task(id = index + 1).copy(
                    recurrenceRule = rule,
                    dueAt = if (rule == RecurrenceRule.None) null else 30,
                    recurrenceEndAt = if (rule == RecurrenceRule.None) null else 40
                )
            }
        )

        val encoded = codec.encode(backup)
        val document = Json.parseToJsonElement(encoded.decodeToString()).jsonObject
        val decoded = codec.decode(encoded)

        assertEquals(2, document.getValue("version").jsonPrimitive.int)
        assertEquals(
            listOf(
                """{"kind":"NONE"}""",
                """{"kind":"INTERVAL","unit":"DAYS","every":3,"basis":"COMPLETION_DATE"}""",
                """{"kind":"INTERVAL","unit":"WEEKS","every":2,"basis":"SCHEDULED_DATE"}""",
                """{"kind":"SELECTED_WEEKDAYS","basis":"COMPLETION_DATE","weekdays":["MONDAY","FRIDAY"]}""",
                """{"kind":"MONTHLY_DAY","basis":"SCHEDULED_DATE","anchorDay":31,"everyMonths":4}""",
                """{"kind":"MONTHLY_ORDINAL","basis":"COMPLETION_DATE","ordinal":"LAST","weekday":"THURSDAY","everyMonths":6}"""
            ).map(Json::parseToJsonElement),
            document.getValue("tasks").jsonArray.map { task ->
                task.jsonObject.getValue("recurrence")
            }
        )
        assertEquals(
            backup.copy(format = "now-do-this-backup", version = 2).sorted(),
            decoded
        )
    }

    @Test
    fun encode_preservesFieldsWithExplicitNullsAndDeterministicOrdering() {
        val backup = sampleBackup().copy(
            format = "untrusted-format",
            version = 99,
            categories = listOf(
                category(id = 9, position = 2, customName = "Later"),
                category(id = 4, position = 0, defaultKey = "WORK")
            ),
            tasks = listOf(
                task(
                    id = 8,
                    subtasks = listOf(
                        subtask(id = 9, taskId = 8, position = 2),
                        subtask(id = 7, taskId = 8, position = 0)
                    )
                ).copy(
                    categoryId = null,
                    isCompleted = false,
                    completedAt = null,
                    dueAt = null,
                    reminderAt = null,
                    reminderStatus = "NONE",
                    recurrenceRule = RecurrenceRule.None,
                    recurrenceEndAt = null,
                    seriesId = null
                ),
                task(id = 3, subtasks = listOf(subtask(id = 6, taskId = 3, position = 0)))
            )
        )

        val encoded = codec.encode(backup)
        val document = Json.parseToJsonElement(encoded.decodeToString()).jsonObject
        val decoded = codec.decode(encoded)
        val nullableTask = document.getValue("tasks").jsonArray.last().jsonObject

        assertEquals("now-do-this-backup", document.getValue("format").jsonPrimitive.content)
        assertEquals(2, document.getValue("version").jsonPrimitive.int)
        assertEquals(JsonNull, nullableTask.getValue("categoryId"))
        assertEquals(JsonNull, nullableTask.getValue("completedAt"))
        assertEquals(JsonNull, nullableTask.getValue("dueAt"))
        assertEquals(JsonNull, nullableTask.getValue("reminderAt"))
        assertEquals(JsonNull, nullableTask.getValue("recurrenceEndAt"))
        assertEquals(JsonNull, nullableTask.getValue("seriesId"))
        assertEquals(listOf(4, 9), decoded.categories.map(PlanningCategory::id))
        assertEquals(listOf(3, 8), decoded.tasks.map(PlanningTask::id))
        assertEquals(listOf(7, 9), decoded.tasks.last().subtasks.map(PlanningSubtask::id))
        assertEquals(codec.encode(backup).toList(), codec.encode(backup.sorted()).toList())
    }

    @Test
    fun decode_ignoresUnknownOuterKeysButRejectsUnusedRecurrenceParameters() {
        val encoded = codec.encode(sampleBackup()).decodeToString()
            .replace("\"format\":", "\"futureRootField\":true,\"format\":")
            .replace("\"title\":\"Task\"", "\"futureTaskField\":42,\"title\":\"Task\"")
            .replace("\"title\":\"First\"", "\"futureSubtaskField\":\"value\",\"title\":\"First\"")

        assertEquals(
            sampleBackup().copy(version = 2).sorted(),
            codec.decode(encoded.encodeToByteArray())
        )

        val unusedParameter = encoded.replace(
            "\"kind\":\"INTERVAL\"",
            "\"kind\":\"INTERVAL\",\"anchorDay\":12"
        )
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(unusedParameter.encodeToByteArray())
        }
    }

    @Test
    fun decodeV1_convertsEveryLegacyRecurrenceToTypedRules() {
        val previousTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val dueAt = Instant.parse("2026-01-31T09:00:00Z").toEpochMilli()
            val decoded = codec.decode(v1Fixture(dueAt).encodeToByteArray())

            assertEquals(
                listOf(
                    RecurrenceRule.None,
                    RecurrenceRule.Interval(IntervalUnit.DAYS, 1, RecurrenceBasis.SCHEDULED_DATE),
                    RecurrenceRule.Interval(IntervalUnit.WEEKS, 1, RecurrenceBasis.SCHEDULED_DATE),
                    RecurrenceRule.MonthlyDay(
                        anchorDay = 31,
                        everyMonths = 1,
                        basis = RecurrenceBasis.SCHEDULED_DATE
                    )
                ),
                decoded.tasks.map(PlanningTask::recurrenceRule)
            )
            assertEquals(1, decoded.version)
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }

    @Test
    fun decode_dispatchesOnlyVersionsOneAndTwoAfterReadingEnvelope() {
        val v2 = codec.encode(sampleBackup()).decodeToString()

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(
                """{"format":"now-do-this-backup","version":3,"items":"future-shape"}"""
                    .encodeToByteArray()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(v2.replace("\"version\":2", "\"version\":0").encodeToByteArray())
        }
    }

    @Test
    fun decode_rejectsUnknownKindsInvalidParametersAndTruncatedInput() {
        val encoded = codec.encode(sampleBackup()).decodeToString()

        listOf(
            encoded.replace("\"kind\":\"INTERVAL\"", "\"kind\":\"YEARLY\""),
            encoded.replace("\"every\":1", "\"every\":0"),
            encoded.replace("\"basis\":\"SCHEDULED_DATE\"", "\"basis\":\"CREATED_DATE\""),
            encoded.dropLast(1)
        ).forEach(::assertDecodeFails)
    }

    @Test
    fun decode_rejectsMissingHeadersAndMalformedUtf8() {
        val encoded = codec.encode(sampleBackup()).decodeToString()

        assertDecodeFails(encoded.replaceFirst("\"format\":\"now-do-this-backup\",", ""))
        assertDecodeFails(encoded.replaceFirst("\"version\":2,", ""))

        val malformed = codec.encode(sampleBackup())
        val titleStart = malformed.indexOfSubsequence("Task".encodeToByteArray())
        malformed[titleStart] = 0xC3.toByte()
        assertDecodeFails(malformed)
    }

    @Test
    fun decode_v1RejectsUnknownLegacyRecurrence() {
        val document = v1Fixture(dueAt = 30)
            .replace("\"recurrence\":\"WEEKLY\"", "\"recurrence\":\"YEARLY\"")

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(document.encodeToByteArray())
        }
    }

    private fun assertDecodeFails(document: String) {
        assertDecodeFails(document.encodeToByteArray())
    }

    private fun assertDecodeFails(document: ByteArray) {
        try {
            codec.decode(document)
            fail("Expected document decoding to fail")
        } catch (_: SerializationException) {
        } catch (_: CharacterCodingException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun ByteArray.indexOfSubsequence(value: ByteArray): Int =
        indices.first { start ->
            start + value.size <= size && value.indices.all { offset -> this[start + offset] == value[offset] }
        }

    private fun sampleBackup() = PlanningBackup(
        format = "now-do-this-backup",
        version = 2,
        createdAtEpochMillis = 1_726_000_000_000,
        categories = listOf(category(id = 4, position = 0, defaultKey = "WORK")),
        tasks = listOf(
            task(
                id = 3,
                categoryId = 4,
                subtasks = listOf(
                    subtask(id = 6, taskId = 3, position = 0, completed = true),
                    subtask(id = 7, taskId = 3, position = 1)
                )
            )
        )
    )

    private fun category(
        id: Int,
        position: Int,
        customName: String? = null,
        defaultKey: String? = null
    ) = PlanningCategory(id, customName, defaultKey, "BLUE", position, createdAt = 10)

    private fun task(
        id: Int,
        categoryId: Int? = null,
        subtasks: List<PlanningSubtask> = emptyList()
    ) = PlanningTask(
        id = id,
        title = "Task",
        description = "Detailed description",
        priority = "HIGH",
        categoryId = categoryId,
        isCompleted = true,
        completedAt = 21,
        dueAt = 30,
        reminderAt = 25,
        reminderStatus = "SCHEDULED",
        recurrenceRule = RecurrenceRule.Interval(
            IntervalUnit.WEEKS,
            1,
            RecurrenceBasis.SCHEDULED_DATE
        ),
        recurrenceEndAt = 40,
        seriesId = "weekly-series",
        createdAt = 11,
        updatedAt = 12,
        subtasks = subtasks
    )

    private fun subtask(
        id: Int,
        taskId: Int,
        position: Int,
        completed: Boolean = false
    ) = PlanningSubtask(
        id = id,
        taskId = taskId,
        title = if (position == 0) "First" else "Second",
        isCompleted = completed,
        completedAt = if (completed) 20 else null,
        position = position
    )

    private fun PlanningBackup.sorted() = copy(
        categories = categories.sortedWith(compareBy(PlanningCategory::position, PlanningCategory::id)),
        tasks = tasks.sortedBy(PlanningTask::id).map { task ->
            task.copy(subtasks = task.subtasks.sortedWith(compareBy(PlanningSubtask::position, PlanningSubtask::id)))
        }
    )

    private fun v1Fixture(dueAt: Long): String {
        val recurrences = listOf("NONE", "DAILY", "WEEKLY", "MONTHLY")
        val tasks = recurrences.mapIndexed { index, recurrence ->
            val activeDueAt = if (recurrence == "NONE") "null" else dueAt.toString()
            """
            {
              "id":${index + 1},"title":"$recurrence","description":"","priority":"MEDIUM",
              "categoryId":null,"isCompleted":false,"completedAt":null,"dueAt":$activeDueAt,
              "reminderAt":null,"reminderStatus":"NONE","recurrence":"$recurrence",
              "recurrenceEndAt":null,"seriesId":null,"createdAt":1,"updatedAt":1,"subtasks":[]
            }
            """.trimIndent()
        }.joinToString(",")
        return """
            {"format":"now-do-this-backup","version":1,"createdAtEpochMillis":1,
             "categories":[],"tasks":[$tasks]}
        """.trimIndent()
    }
}
