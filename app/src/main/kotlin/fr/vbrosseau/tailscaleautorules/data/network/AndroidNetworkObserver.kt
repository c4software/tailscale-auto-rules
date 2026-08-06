package fr.vbrosseau.tailscaleautorules.data.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.vbrosseau.tailscaleautorules.di.IoDispatcher
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.network.stabilized
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observe le réseau via [ConnectivityManager.NetworkCallback] et le mode avion
 * via la diffusion système correspondante.
 *
 * Le flux émis est brut côté callbacks puis stabilisé par l'opérateur du
 * domaine : la logique de debounce reste ainsi testable hors Android.
 */
@Singleton
class AndroidNetworkObserver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NetworkObserver {

    private val connectivityManager: ConnectivityManager?
        get() = context.getSystemService(ConnectivityManager::class.java)

    override fun observe(): Flow<NetworkContext> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            send(NetworkContext.Disconnected)
            awaitClose { }
            return@callbackFlow
        }

        // Toute notification, quelle qu'elle soit, provoque une relecture
        // complète de l'état : c'est plus simple, et plus sûr, que d'essayer de
        // reconstituer le contexte à partir de l'événement reçu.
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(readContext())
            }

            override fun onLost(network: Network) {
                trySend(readContext())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(readContext())
            }
        }

        val airplaneModeReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                trySend(readContext())
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        manager.registerNetworkCallback(request, networkCallback)
        context.registerReceiver(
            airplaneModeReceiver,
            IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
        )

        // Valeur initiale : sans elle, un collecteur resterait muet jusqu'au
        // premier changement de réseau.
        send(readContext())

        awaitClose {
            manager.unregisterNetworkCallback(networkCallback)
            context.unregisterReceiver(airplaneModeReceiver)
        }
    }.flowOn(ioDispatcher).stabilized()

    override suspend fun current(): NetworkContext = withContext(ioDispatcher) { readContext() }

    private fun readContext(): NetworkContext {
        val manager = connectivityManager
        val network = manager?.activeNetwork
        val capabilities = network?.let(manager::getNetworkCapabilities)
        val transport = capabilities.toTransport()

        return NetworkContext(
            transport = transport,
            isAirplaneModeOn = isAirplaneModeOn(),
            isInternetValidated = capabilities
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            ssid = if (transport == NetworkTransport.WIFI) readSsid(capabilities) else null,
        )
    }

    private fun NetworkCapabilities?.toTransport(): NetworkTransport = when {
        this == null -> NetworkTransport.NONE
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
        else -> NetworkTransport.OTHER
    }

    private fun isAirplaneModeOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    /**
     * Lit le SSID courant, ou `null` s'il est indisponible.
     *
     * Deux chemins selon la plateforme :
     *
     * - À partir d'Android 12, le SSID se lit sur les capacités du réseau via
     *   [NetworkCapabilities.getTransportInfo]. C'est la voie officielle.
     * - Avant, seul `WifiManager.getConnectionInfo()` le fournit. L'API est
     *   dépréciée depuis Android 12, d'où la restriction de portée explicite.
     *
     * Dans les deux cas, l'absence de permission de localisation fait renvoyer
     * au système une valeur de repli (`<unknown ssid>`), traitée ici comme une
     * indisponibilité — c'est ce que SPECS.md §4.2 impose.
     */
    private fun readSsid(capabilities: NetworkCapabilities?): String? {
        val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capabilities?.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(WifiManager::class.java)?.connectionInfo
        }

        return wifiInfo?.ssid
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
    }
}
