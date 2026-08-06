package fr.vbrosseau.tailscaleautorules.data.tailscale

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.vbrosseau.tailscaleautorules.di.IoDispatcher
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleController
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleUnavailableException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pilote le client Tailscale officiel par diffusion explicite.
 *
 * Le client expose volontairement un receveur destiné aux applications
 * tierces (`IPNReceiver`, `android:exported="true"`, sans permission requise) :
 *
 * ```xml
 * <receiver android:name="IPNReceiver" android:exported="true">
 *     <intent-filter>
 *         <action android:name="com.tailscale.ipn.CONNECT_VPN" />
 *         <action android:name="com.tailscale.ipn.DISCONNECT_VPN" />
 *     </intent-filter>
 * </receiver>
 * ```
 *
 * Trois conséquences dictent l'implémentation :
 *
 * 1. **Le receveur enfile un travail et rend la main.** Aucun accusé de
 *    réception n'est possible : un succès signifie « demande transmise ».
 * 2. **La diffusion doit être explicite.** Un `Intent` implicite n'atteindrait
 *    pas le receveur, et serait de surcroît restreint depuis Android 8.
 * 3. **La visibilité du paquet doit être déclarée** dans notre manifeste
 *    (`<queries>`), sans quoi Android 11+ prétend que le client est absent.
 */
@Singleton
class AndroidTailscaleController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TailscaleController {

    private val connectivityManager: ConnectivityManager?
        get() = context.getSystemService(ConnectivityManager::class.java)

    override suspend fun isAvailable(): Boolean = withContext(ioDispatcher) {
        runCatching { context.packageManager.findTailscalePackage() }.isSuccess
    }

    override suspend fun enable(): Result<Unit> = broadcast(ACTION_CONNECT)

    override suspend fun disable(): Result<Unit> = broadcast(ACTION_DISCONNECT)

    /**
     * Détecte un tunnel VPN actif.
     *
     * Android n'expose pas quelle application porte le tunnel : cette
     * détection répond donc à « un VPN est actif », et non « *ce* VPN est
     * actif ». C'est suffisant ici, l'application ne pilotant qu'un seul VPN,
     * mais l'approximation est réelle et doit rester visible.
     */
    override suspend fun isRunning(): Boolean = withContext(ioDispatcher) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            ?: return@withContext false
        val network = connectivityManager.activeNetwork ?: return@withContext false

        connectivityManager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    /**
     * Observe l'apparition et la disparition d'un tunnel VPN.
     *
     * C'est ici, et non dans l'observation du réseau, que le tunnel se
     * surveille : le `NetworkContext` du domaine écarte volontairement le VPN
     * pour que les règles voient le réseau physique. Y faire transiter l'état
     * du tunnel le rendrait invisible — deux contextes identiques, filtrés par
     * `distinctUntilChanged`.
     */
    override fun observeRunning(): Flow<Boolean> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            send(false)
            awaitClose { }
            return@callbackFlow
        }

        val active = mutableSetOf<Network>()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                active += network
                trySend(true)
            }

            override fun onLost(network: Network) {
                active -= network
                trySend(active.isNotEmpty())
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            // Un NetworkRequest exclut les VPN par défaut : sans cette levée,
            // le callback ne serait jamais appelé.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        manager.registerNetworkCallback(request, callback)
        send(isRunning())

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().flowOn(ioDispatcher)

    private suspend fun broadcast(action: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            context.packageManager.findTailscalePackage()
            context.sendBroadcast(
                Intent(action).setComponent(ComponentName(TAILSCALE_PACKAGE, IPN_RECEIVER)),
            )
        }.recoverCatching { cause ->
            throw if (cause is PackageManager.NameNotFoundException) {
                TailscaleUnavailableException()
            } else {
                cause
            }
        }
    }

    /** Lève [PackageManager.NameNotFoundException] si le client est absent. */
    private fun PackageManager.findTailscalePackage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(TAILSCALE_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(TAILSCALE_PACKAGE, 0)
        }
    }

    // Volontairement public : marquer ce companion `internal` fait planter
    // l'analyse Kotlin d'Android Lint (AGP 9.3.1) dès qu'un test y accède —
    // SymbolLightClassForClassOrObject.isRecord lève alors une exception.
    companion object {
        const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
        const val IPN_RECEIVER = "com.tailscale.ipn.IPNReceiver"
        const val ACTION_CONNECT = "com.tailscale.ipn.CONNECT_VPN"
        const val ACTION_DISCONNECT = "com.tailscale.ipn.DISCONNECT_VPN"
    }
}
