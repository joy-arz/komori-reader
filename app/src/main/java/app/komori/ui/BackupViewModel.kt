package app.komori.ui

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.komori.BuildConfig
import app.komori.KomoriApp
import app.komori.data.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KomoriApp
    private val repository = BackupRepository(app.database)
    val busy = MutableStateFlow(false)
    val preview = MutableStateFlow<BackupPreview?>(null)
    val message = MutableStateFlow<String?>(null)

    fun export(uri: Uri) = operation {
        try {
            val bytes = repository.export(BuildConfig.VERSION_NAME)
            withContext(Dispatchers.IO) {
                val output =
                    app.contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("The selected document cannot be written.")
                output.use {
                    it.write(bytes)
                    it.flush()
                }
            }
            message.value = "Library backup saved."
        } catch (e: Exception) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }
            }
            throw e
        }
    }

    fun inspect(uri: Uri) = operation {
        preview.value = null
        val backup =
            withContext(Dispatchers.IO) {
                val input =
                    app.contentResolver.openInputStream(uri)
                        ?: throw IOException("The selected document cannot be opened.")
                input.use(BackupCodec::read)
            }
        preview.value = repository.preview(backup)
    }

    fun dismissPreview() {
        if (!busy.value) preview.value = null
    }

    fun restore(useBackupValues: Boolean) {
        val pending = preview.value ?: return
        operation {
            repository.restore(pending.backup, useBackupValues)
            preview.value = null
            message.value =
                if (useBackupValues) "Library restored. Matching entries use the backup values."
                else "Library restored. Existing values kept."
        }
    }

    private fun operation(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        message.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value =
                    when (e) {
                        is InvalidBackup -> e.message
                        is SecurityException ->
                            "Access to this document was denied. Choose it again in the file picker."
                        else ->
                            "Could not complete the backup operation. Check document access and free storage, then retry."
                    }
            } finally {
                busy.value = false
            }
        }
    }
}
