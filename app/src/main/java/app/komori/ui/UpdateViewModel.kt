package app.komori.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.komori.BuildConfig
import app.komori.KomoriApp
import app.komori.data.AppUpdates
import kotlinx.coroutines.flow.first

class UpdateViewModel @JvmOverloads constructor(application: Application, val updater: AppUpdates = AppUpdates(application)) : AndroidViewModel(application) {
    private val operations = UpdateOperations(viewModelScope, updater, BuildConfig.VERSION_NAME,
        { getApplication<KomoriApp>().settings.flow.first().autoUpdateDownload }, SystemClock::elapsedRealtime)
    val state = operations.state
    fun check(manual: Boolean = false) = operations.check(manual)
    fun download(unmeteredOnly: Boolean = false) = operations.download(unmeteredOnly)
    fun channel(value: String) = operations.channel(value)
    fun snooze() = operations.snooze()
    fun dismiss() = operations.dismiss()
    fun show() = operations.show()
    fun message(text: String) = operations.message(text)
    override fun onCleared() { operations.close(); super.onCleared() }
}
