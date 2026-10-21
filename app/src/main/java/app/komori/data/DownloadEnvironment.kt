package app.komori.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DownloadEnvironment(val connected: Boolean = false, val unmetered: Boolean = false, val lowStorage: Boolean = false)

fun downloadEnvironment(context: Context) = callbackFlow {
    val app = context.applicationContext
    val manager = app.getSystemService(ConnectivityManager::class.java)
    val lowStorage = AtomicBoolean(false)
    fun publish() {
        val network = manager.activeNetwork
        val internet = network != null && manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        trySend(DownloadEnvironment(internet, internet && !manager.isActiveNetworkMetered, lowStorage.get() || app.filesDir.usableSpace < 64L * 1024 * 1024))
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()
        override fun onLost(network: Network) = publish()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publish()
    }
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            lowStorage.set(intent?.action == Intent.ACTION_DEVICE_STORAGE_LOW)
            publish()
        }
    }
    manager.registerDefaultNetworkCallback(callback)
    val sticky = ContextCompat.registerReceiver(app, receiver, IntentFilter().apply {
        addAction(Intent.ACTION_DEVICE_STORAGE_LOW); addAction(Intent.ACTION_DEVICE_STORAGE_OK)
    }, ContextCompat.RECEIVER_EXPORTED)
    lowStorage.set(sticky?.action == Intent.ACTION_DEVICE_STORAGE_LOW)
    val ticker = launch { while (isActive) { publish(); delay(10000) } }
    awaitClose { ticker.cancel(); manager.unregisterNetworkCallback(callback); app.unregisterReceiver(receiver) }
}.distinctUntilChanged().flowOn(Dispatchers.IO)

fun downloadDisplayState(download: Download, environment: DownloadEnvironment, wifiOnly: Boolean): String = when {
    download.state !in app.komori.domain.DownloadPolicy.activeStates -> download.state
    environment.lowStorage || download.state == "Waiting for storage" -> "Waiting for storage"
    !environment.connected -> "Waiting for network"
    wifiOnly && !environment.unmetered -> "Waiting for Wi-Fi"
    else -> download.state
}
