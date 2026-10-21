package app.komori

import android.database.Cursor
import android.database.MatrixCursor
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/** Debug builds only. Exposes generated fixtures, never Library or production app storage. */
class BackupTestProvider : DocumentsProvider() {
    private val root get() = File(context!!.cacheDir, "backup-provider-fixture").apply { mkdirs() }
    private fun file(id: String): File {
        if (id == "root") return root
        if (!id.matches(Regex("[a-zA-Z0-9.-]+"))) throw FileNotFoundException("Unknown fixture document")
        return File(root, id)
    }
    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf("root_id", "document_id", "title", "flags"))
    private fun cursor(projection: Array<out String>?, files: List<File>): Cursor {
        val columns = projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE)
        return MatrixCursor(columns).apply {
            files.forEach { file ->
                val id = if (file == root) "root" else file.name
                addRow(columns.map { column -> when (column) {
                    Document.COLUMN_DOCUMENT_ID -> id
                    Document.COLUMN_DISPLAY_NAME -> id
                    Document.COLUMN_MIME_TYPE -> if (file.isDirectory) Document.MIME_TYPE_DIR else "application/json"
                    Document.COLUMN_FLAGS -> Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else 0
                    Document.COLUMN_SIZE -> file.length()
                    else -> null
                } }.toTypedArray())
            }
        }
    }
    override fun queryDocument(documentId: String, projection: Array<out String>?) = cursor(projection, listOf(file(documentId)))
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) = cursor(projection, file(parentDocumentId).listFiles()?.toList().orEmpty())
    override fun openDocument(documentId: String, mode: String, signal: android.os.CancellationSignal?) = ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        require(parentDocumentId == "root")
        file(displayName).writeBytes(byteArrayOf())
        return displayName
    }
    override fun isChildDocument(parentDocumentId: String, documentId: String) = parentDocumentId == "root" && documentId != "root" && file(documentId).exists()
    override fun deleteDocument(documentId: String) { require(documentId != "root"); if (!file(documentId).delete()) throw FileNotFoundException("Fixture document missing") }
}
