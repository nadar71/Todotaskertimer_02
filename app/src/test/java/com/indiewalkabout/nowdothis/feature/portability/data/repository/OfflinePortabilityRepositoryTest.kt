package com.indiewalkabout.nowdothis.feature.portability.data.repository

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.portability.data.local.PlanningDataStore
import com.indiewalkabout.nowdothis.feature.portability.data.serialization.BackupCodec
import com.indiewalkabout.nowdothis.feature.portability.data.serialization.BackupValidator
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentTooLarge
import com.indiewalkabout.nowdothis.feature.portability.domain.model.InvalidBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningBackup
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningCategory
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PlanningTask
import com.indiewalkabout.nowdothis.feature.portability.domain.model.PortabilityException
import com.indiewalkabout.nowdothis.feature.portability.domain.model.RestoreFailed
import com.indiewalkabout.nowdothis.feature.portability.domain.model.UnsupportedFutureVersion
import com.indiewalkabout.nowdothis.feature.portability.domain.model.WriteFailed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflinePortabilityRepositoryTest {
    @Test
    fun createBackup_snapshotsAtInjectedTimeAndWritesEncodedBytes() = runTest {
        val store = FakePlanningDataStore(backup = validBackup())
        val documents = FakeDocumentGateway()
        val repository = repository(store, documents, now = 9_999L)

        val exported = repository.createBackup(DocumentReference("destination"))

        assertEquals(validBackup(createdAt = 9_999L), store.snapshotRequests.single())
        assertEquals(DocumentReference("destination"), documents.writes.single().first)
        assertEquals(validBackup(createdAt = 9_999L), BackupCodec().decode(documents.writes.single().second))
        assertEquals(backupSummary(createdAt = 9_999L), exported.summary)
    }

    @Test
    fun createBackup_writesPayloadAtExactSizeLimit() = runTest {
        val backup = backupWithEncodedSize(BackupValidator.MAX_DOCUMENT_SIZE_BYTES.toInt())
        val documents = FakeDocumentGateway()
        val repository = repository(FakePlanningDataStore(backup), documents, now = backup.createdAtEpochMillis)

        repository.createBackup(DocumentReference("destination"))

        assertEquals(BackupValidator.MAX_DOCUMENT_SIZE_BYTES.toInt(), documents.writes.single().second.size)
    }

    @Test
    fun createBackup_rejectsPayloadOneByteOverSizeLimitBeforeWrite() = runTest {
        val backup = backupWithEncodedSize(BackupValidator.MAX_DOCUMENT_SIZE_BYTES.toInt() + 1)
        val documents = FakeDocumentGateway()
        val repository = repository(FakePlanningDataStore(backup), documents, now = backup.createdAtEpochMillis)

        val error = runCatching { repository.createBackup(DocumentReference("destination")) }
            .exceptionOrNull() as PortabilityException

        assertSame(DocumentTooLarge, error.error)
        assertTrue(documents.writes.isEmpty())
    }

    @Test
    fun inspectBackup_readsWithinBoundDecodesAndReturnsValidatedCandidate() = runTest {
        val document = validBackup(createdAt = 777L)
        val documents = FakeDocumentGateway(readBytes = BackupCodec().encode(document))
        val repository = repository(FakePlanningDataStore(), documents)

        val candidate = repository.inspectBackup(DocumentReference("source"))

        assertEquals(document, candidate.backup)
        assertEquals(backupSummary(createdAt = 777L), candidate.summary)
        assertEquals(BackupValidator.MAX_DOCUMENT_SIZE_BYTES, documents.readLimits.single())
    }

    @Test
    fun inspectBackup_mapsMalformedDocumentToInvalidBackup() = runTest {
        val repository = repository(
            FakePlanningDataStore(),
            FakeDocumentGateway(readBytes = "not json".encodeToByteArray())
        )

        val error = runCatching { repository.inspectBackup(DocumentReference("source")) }
            .exceptionOrNull() as PortabilityException

        assertSame(InvalidBackup, error.error)
    }

    @Test
    fun inspectBackup_mapsMalformedUtf8ToInvalidBackupWithoutReplacement() = runTest {
        val store = FakePlanningDataStore()
        val repository = repository(
            store,
            FakeDocumentGateway(readBytes = byteArrayOf('{'.code.toByte(), 0xC3.toByte(), '}'.code.toByte()))
        )

        val error = runCatching { repository.inspectBackup(DocumentReference("source")) }
            .exceptionOrNull() as PortabilityException

        assertSame(InvalidBackup, error.error)
        assertTrue(store.replacementRequests.isEmpty())
    }

    @Test
    fun inspectBackup_reportsFutureVersionBeforeDecodingChangedPayload() = runTest {
        val store = FakePlanningDataStore()
        val repository = repository(
            store,
            FakeDocumentGateway(
                readBytes = """{"format":"now-do-this-backup","version":2,"items":"v2-shape"}"""
                    .encodeToByteArray()
            )
        )

        val error = runCatching { repository.inspectBackup(DocumentReference("source")) }
            .exceptionOrNull() as PortabilityException

        assertEquals(UnsupportedFutureVersion(2), error.error)
        assertTrue(store.replacementRequests.isEmpty())
    }

    @Test
    fun inspectBackup_mapsMissingFutureFormatHeaderToInvalidBackup() = runTest {
        val repository = repository(
            FakePlanningDataStore(),
            FakeDocumentGateway(readBytes = """{"version":2,"items":[]}""".encodeToByteArray())
        )

        val error = runCatching { repository.inspectBackup(DocumentReference("source")) }
            .exceptionOrNull() as PortabilityException

        assertSame(InvalidBackup, error.error)
    }

    @Test
    fun inspectBackup_mapsBoundedReadOverflowToDocumentTooLarge() = runTest {
        val repository = repository(
            FakePlanningDataStore(),
            FakeDocumentGateway(readFailure = DocumentSizeLimitExceededException())
        )

        val error = runCatching { repository.inspectBackup(DocumentReference("source")) }
            .exceptionOrNull() as PortabilityException

        assertSame(DocumentTooLarge, error.error)
    }

    @Test
    fun createBackup_mapsDestinationFailureToWriteFailed() = runTest {
        val repository = repository(
            FakePlanningDataStore(backup = validBackup()),
            FakeDocumentGateway(writeFailure = IllegalStateException())
        )

        val error = runCatching { repository.createBackup(DocumentReference("destination")) }
            .exceptionOrNull() as PortabilityException

        assertSame(WriteFailed, error.error)
    }

    @Test
    fun replaceAll_acceptsTheValidatedCandidateAndMapsReplacementFailure() = runTest {
        val store = FakePlanningDataStore(replaceFailure = IllegalStateException())
        val repository = repository(store, FakeDocumentGateway(readBytes = BackupCodec().encode(validBackup())))
        val candidate = repository.inspectBackup(DocumentReference("source"))

        val error = runCatching { repository.replaceAll(candidate) }.exceptionOrNull() as PortabilityException

        assertEquals(candidate.backup, store.replacementRequests.single())
        assertSame(RestoreFailed, error.error)
    }

    private fun repository(
        store: FakePlanningDataStore,
        documents: FakeDocumentGateway,
        now: Long = 1_000L
    ) = OfflinePortabilityRepository(
        planningDataStore = store,
        documentGateway = documents,
        backupCodec = BackupCodec(),
        backupValidator = BackupValidator(),
        clock = AppClock { now },
        dispatcher = UnconfinedTestDispatcher()
    )
}

private class FakePlanningDataStore(
    private val backup: PlanningBackup = validBackup(),
    private val replaceFailure: Throwable? = null
) : PlanningDataStore {
    val snapshotRequests = mutableListOf<PlanningBackup>()
    val replacementRequests = mutableListOf<PlanningBackup>()

    override suspend fun snapshot(createdAtEpochMillis: Long): PlanningBackup =
        backup.copy(createdAtEpochMillis = createdAtEpochMillis).also(snapshotRequests::add)

    override suspend fun replaceAll(backup: PlanningBackup): Set<Int> {
        replacementRequests += backup
        replaceFailure?.let { throw it }
        return setOf(13)
    }
}

private class FakeDocumentGateway(
    private val readBytes: ByteArray = ByteArray(0),
    private val readFailure: Throwable? = null,
    private val writeFailure: Throwable? = null
) : DocumentGateway {
    val writes = mutableListOf<Pair<DocumentReference, ByteArray>>()
    val readLimits = mutableListOf<Long>()

    override suspend fun write(reference: DocumentReference, bytes: ByteArray) {
        writeFailure?.let { throw it }
        writes += reference to bytes
    }

    override suspend fun read(reference: DocumentReference, maxBytes: Long): ByteArray {
        readLimits += maxBytes
        readFailure?.let { throw it }
        return readBytes
    }
}

private fun validBackup(createdAt: Long = 100L) = PlanningBackup(
    format = "now-do-this-backup",
    version = 1,
    createdAtEpochMillis = createdAt,
    categories = listOf(PlanningCategory(1, "Home", null, "GREEN", 0, 5L)),
    tasks = listOf(
        PlanningTask(
            id = 13,
            title = "Plan",
            description = "",
            priority = "HIGH",
            categoryId = 1,
            isCompleted = false,
            completedAt = null,
            dueAt = null,
            reminderAt = null,
            reminderStatus = "NONE",
            recurrence = "NONE",
            recurrenceEndAt = null,
            seriesId = null,
            createdAt = 10L,
            updatedAt = 11L,
            subtasks = emptyList()
        )
    )
)

private fun backupSummary(createdAt: Long) =
    com.indiewalkabout.nowdothis.feature.portability.domain.model.BackupSummary(
        createdAtEpochMillis = createdAt,
        categoryCount = 1,
        taskCount = 1,
        completedTaskCount = 0,
        subtaskCount = 0
    )

private fun backupWithEncodedSize(targetSize: Int): PlanningBackup {
    val codec = BackupCodec()
    val base = validBackup()
    val baseSize = codec.encode(base).size
    return base.copy(
        tasks = base.tasks.map { task -> task.copy(description = "x".repeat(targetSize - baseSize)) }
    ).also { backup -> assertEquals(targetSize, codec.encode(backup).size) }
}
