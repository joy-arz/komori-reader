package app.komori

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.komori.ui.KomoriScreen

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        androidx.lifecycle
            .ViewModelProvider(this)[app.komori.ui.UpdateViewModel::class.java]
            .check()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KomoriScreen(viewModel()) }
    }
}
