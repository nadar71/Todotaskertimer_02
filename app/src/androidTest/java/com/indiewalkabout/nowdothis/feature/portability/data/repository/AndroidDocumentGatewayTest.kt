package com.indiewalkabout.nowdothis.feature.portability.data.repository

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indiewalkabout.nowdothis.feature.portability.domain.model.DocumentReference
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDocumentGatewayTest {
    @Test
    fun write_truncatesExistingProviderDocumentBeforeWritingShorterBackup() = runTest {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        val document = File(applicationContext.cacheDir, "portability-provider-backup.json")
        document.writeBytes("long stale backup bytes".encodeToByteArray())
        val resolver = ContentResolver.wrap(FileDocumentProvider(document))
        val context = object : ContextWrapper(applicationContext) {
            override fun getContentResolver(): ContentResolver = resolver
        }

        AndroidDocumentGateway(context).write(
            reference = DocumentReference("content://$AUTHORITY/backup.json"),
            bytes = "short".encodeToByteArray()
        )

        assertArrayEquals("short".encodeToByteArray(), document.readBytes())
    }

    private class FileDocumentProvider(
        private val document: File
    ) : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val flags = ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_WRITE_ONLY or
                if ('t' in mode) ParcelFileDescriptor.MODE_TRUNCATE else 0
            return ParcelFileDescriptor.open(document, flags)
        }

        override fun getType(uri: Uri): String = "application/json"
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }

    private companion object {
        const val AUTHORITY = "com.indiewalkabout.nowdothis.test.documents"
    }
}
