package app.komori

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.komori.ui.KomoriScreen

class MainActivity : ComponentActivity() {
    var volumeNavigation: ((Int) -> Unit)? = null

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val direction = when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> 1
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> -1
            else -> return super.onKeyDown(keyCode, event)
        }
        val handler = volumeNavigation ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) handler(direction)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (volumeNavigation != null && keyCode in listOf(android.view.KeyEvent.KEYCODE_VOLUME_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_UP)) return true
        return super.onKeyUp(keyCode, event)
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KomoriScreen(viewModel()) }
    }
}
