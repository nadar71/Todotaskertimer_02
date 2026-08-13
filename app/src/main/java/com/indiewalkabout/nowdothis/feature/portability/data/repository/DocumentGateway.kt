package com.indiewalkabout.nowdothis.feature.portability.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

interface DocumentGateway {
    suspend fun write(reference: DocumentReference, bytes: ByteArray)

    suspend fun read(reference: DocumentReference, maxBytes: Long): ByteArray
}

class AndroidDocumentGateway @Inject constructor(
    @ApplicationContext context: Context
) : DocumentGateway {
    private val contentResolver: ContentResolver = context.contentResolver

    override suspend fun write(reference: DocumentReference, bytes: ByteArray) {
        contentResolver.openOutputStream(Uri.parse(reference.value), "wt")?.use { stream ->
            stream.write(bytes)
        } ?: throw IOException("Unable to open output stream")
    }

    override suspend fun read(reference: DocumentReference, maxBytes: Long): ByteArray {
        require(maxBytes >= 0)
        return contentResolver.openInputStream(Uri.parse(reference.value))?.use { stream ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count > maxBytes - total) throw DocumentSizeLimitExceededException()
                    output.write(buffer, 0, count)
                    total += count
                }
                output.toByteArray()
            }
        } ?: throw IOException("Unable to open input stream")
    }
}

internal class DocumentSizeLimitExceededException : IOException()
