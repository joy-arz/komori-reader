package app.komori

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.komori.ui.KomoriScreen

class MainActivity : ComponentActivity() {
    var volumeNavigation: ((Int) -> Unit)? = null

    private fun routeVolumeKey(event: android.view.KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> 1
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> -1
            else -> return false
        }
        val handler = volumeNavigation ?: return false
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            handler(direction)
        }
        return event.action == android.view.KeyEvent.ACTION_DOWN || event.action == android.view.KeyEvent.ACTION_UP
    }

    override fun onStart() {
        super.onStart()
        androidx.lifecycle
            .ViewModelProvider(this)[app.komori.ui.UpdateViewModel::class.java]
            .check()
        if (intent.getBooleanExtra("showUpdates", false)) {
            val reader = androidx.lifecycle.ViewModelProvider(this)[app.komori.ui.ReaderViewModel::class.java]
            reader.closeReader()
            reader.selected.value = null
            reader.currentTab.value = 0
            reader.updatesVisible.value = true
            intent.removeExtra("showUpdates")
        }
        if (intent.getBooleanExtra("showDownloads", false)) {
            val reader = androidx.lifecycle.ViewModelProvider(this)[app.komori.ui.ReaderViewModel::class.java]
            reader.closeReader(); reader.selected.value = null; reader.currentTab.value = 3
            intent.removeExtra("showDownloads")
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("showDownloads", false)) {
            val reader = androidx.lifecycle.ViewModelProvider(this)[app.komori.ui.ReaderViewModel::class.java]
            reader.closeReader(); reader.selected.value = null; reader.currentTab.value = 3
            intent.removeExtra("showDownloads")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val originalCallback = window.callback
        window.callback = object : android.view.Window.Callback by originalCallback {
            override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
                routeVolumeKey(event) || originalCallback.dispatchKeyEvent(event)
        }
        enableEdgeToEdge()
        setContent { KomoriScreen(viewModel()) }
    }
}
