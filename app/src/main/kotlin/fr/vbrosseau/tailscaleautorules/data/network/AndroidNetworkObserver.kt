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
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.vbrosseau.tailscaleautorules.di.IoDispatcher
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.network.stabilized
import fr.vbrosseau.tailscaleautorules.presentation.SystemStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Observe le réseau via [ConnectivityManager.NetworkCallback] et le mode avion
 * via la diffusion système correspondante.
 *
 * **Le SSID ne s'obtient que par le callback.** `getNetworkCapabilities()`
 * renvoie des capacités *expurgées* de tout ce qui est sensible à la
 * localisation : le `transportInfo` y est vide, même permission accordée. Seules
 * les capacités livrées à un callback enregistré avec
 * `FLAG_INCLUDE_LOCATION_INFO` portent le SSID. C'est pourquoi cette classe
 * construit son contexte à partir des capacités **reçues**, et ne les redemande
 * jamais.
 */
@Singleton
class AndroidNetworkObserver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val systemStatus: SystemStatus,
) : NetworkObserver {

    private val connectivityManager: ConnectivityManager?
        get() = context.getSystemService(ConnectivityManager::class.java)

    override fun observe(): Flow<NetworkContext> = rawContexts().stabilized()

    /**
     * Contexte courant, sans attendre la fenêtre de stabilisation complète.
     *
     * On ouvre brièvement une observation plutôt que d'interroger le système :
     * c'est le seul moyen d'obtenir un SSID. Le système livre l'état courant dès
     * l'enregistrement, la réponse est donc quasi immédiate — le temps que la
     * rafale retombe, voir `SETTLE_WINDOW`.
     *
     * Le repli couvre le cas où aucun réseau ne correspond — hors ligne, mode
     * avion — auquel cas aucun callback n'arrive et attendre indéfiniment
     * bloquerait la synchronisation manuelle.
     */
    override suspend fun current(): NetworkContext = withContext(ioDispatcher) {
        val context = withTimeoutOrNull(CURRENT_TIMEOUT) {
            rawContexts().debounce(SETTLE_WINDOW).first()
        } ?: redactedContext()
        Timber.i("Contexte capturé : %s", context)
        context
    }

    /** Flux brut, sans stabilisation : le debounce appartient au domaine. */
    private fun rawContexts(): Flow<NetworkContext> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            send(NetworkContext.Disconnected)
            awaitClose { }
            return@callbackFlow
        }

        // Capacités par réseau, telles que le système nous les livre. Les
        // conserver est indispensable : elles ne sont pas relisibles ensuite.
        val known = mutableMapOf<Network, NetworkCapabilities>()

        fun publish() {
            trySend(contextFrom(known.values))
        }

        val events = object : NetworkEvents {
            override fun onCapabilities(network: Network, capabilities: NetworkCapabilities) {
                known[network] = capabilities
                publish()
            }

            override fun onLost(network: Network) {
                known.remove(network)
                publish()
            }
        }

        val networkCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LocationAwareCallback(events)
        } else {
            PlainCallback(events)
        }

        val airplaneModeReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) = publish()
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

        awaitClose {
            manager.unregisterNetworkCallback(networkCallback)
            context.unregisterReceiver(airplaneModeReceiver)
        }
    }.flowOn(ioDispatcher)

    /**
     * Construit le contexte à partir du réseau physique.
     *
     * Le tunnel est écarté : une fois monté, il devient le réseau actif et
     * masquerait le Wi-Fi ou la 4G sous-jacents. Les règles décideraient alors
     * sur « autre transport » et s'abstiendraient toutes.
     *
     * Le réseau retenu parmi les autres est celui que désigne [PreferredNetwork] :
     * plusieurs coexistent pendant — et longtemps après — une bascule.
     */
    private fun contextFrom(capabilities: Collection<NetworkCapabilities>): NetworkContext {
        val physical = capabilities
            .filterNot { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
            .maxWithOrNull(PreferredNetwork)

        val transport = physical.toTransport()
        val ssid = if (transport == NetworkTransport.WIFI) readSsid(physical) else null

        return NetworkContext(
            transport = transport,
            isAirplaneModeOn = isAirplaneModeOn(),
            isInternetValidated = physical
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            ssid = ssid,
            isSsidRedacted = isRedacted(transport, ssid),
        )
    }

    /**
     * Contexte de repli, sans SSID.
     *
     * Les capacités obtenues ici sont expurgées ; le transport et le mode avion
     * restent exacts, ce qui suffit à toutes les règles sauf celle des réseaux
     * de confiance.
     */
    private fun redactedContext(): NetworkContext {
        val manager = connectivityManager
        val capabilities = manager?.activeNetwork?.let(manager::getNetworkCapabilities)
        val transport = capabilities.toTransport()

        return NetworkContext(
            transport = transport,
            isAirplaneModeOn = isAirplaneModeOn(),
            isInternetValidated = capabilities
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            isSsidRedacted = isRedacted(transport, ssid = null),
        )
    }

    /**
     * Permission accordée mais aucun SSID livré : la lecture a été expurgée par
     * le contexte d'exécution, pas refusée par l'utilisateur. Les règles Wi-Fi
     * s'abstiennent alors plutôt que de décider sans identité (SPECS.md §4.2).
     */
    private fun isRedacted(transport: NetworkTransport, ssid: String?): Boolean =
        transport == NetworkTransport.WIFI && ssid == null && systemStatus.canReadSsid()

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
     * Lit le SSID, ou `null` s'il est indisponible.
     *
     * À partir d'Android 12, il voyage dans les capacités reçues du callback.
     * Avant, seul `WifiManager.getConnectionInfo()` le fournit — API dépréciée
     * depuis, d'où la restriction de portée explicite.
     *
     * Sans permission de localisation, le système renvoie une valeur de repli,
     * traitée ici comme une indisponibilité (SPECS.md §4.2).
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

    private companion object {
        /**
         * Laisse la rafale d'inscription retomber.
         *
         * Le système livre chaque réseau correspondant séparément, en quelques
         * millisecondes. Prendre la première émission retiendrait donc le
         * premier réseau livré — souvent le cellulaire encore actif après une
         * bascule vers le Wi-Fi — plutôt que celui qui porte la connexion.
         */
        val SETTLE_WINDOW = 300.milliseconds

        val CURRENT_TIMEOUT = 2.seconds
    }
}

/**
 * Départage les réseaux simultanés : le plus grand est celui qui porte
 * réellement la connexion.
 *
 * **Plusieurs réseaux coexistent bien au-delà d'une bascule.** En passant du
 * cellulaire au Wi-Fi, Android conserve le cellulaire actif et validé plusieurs
 * minutes avant de le démonter. Départager sur la seule validation laissait
 * alors gagner le cellulaire — simplement parce qu'il avait été livré en
 * premier — et le tunnel ne réagissait au Wi-Fi qu'à la disparition effective
 * du réseau mobile, très longtemps après.
 *
 * L'ordre reproduit celui d'Android pour son réseau par défaut : un réseau
 * validé prime toujours, puis le filaire devance le Wi-Fi, qui devance le
 * cellulaire.
 */
private object PreferredNetwork : Comparator<NetworkCapabilities> {

    override fun compare(first: NetworkCapabilities, second: NetworkCapabilities): Int =
        compareValuesBy(
            first,
            second,
            { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) },
            { it.transportRank() },
        )

    private fun NetworkCapabilities.transportRank(): Int = when {
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ETHERNET
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> WIFI
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> CELLULAR
        else -> OTHER
    }

    private const val ETHERNET = 3
    private const val WIFI = 2
    private const val CELLULAR = 1
    private const val OTHER = 0
}

/**
 * Ce que l'observateur retient d'un callback réseau.
 *
 * Existe parce que le constructeur `NetworkCallback(int)` — celui qui demande
 * un SSID non expurgé — n'apparaît qu'avec Android 12. Le choix du constructeur
 * se fait à la déclaration de la classe : deux sous-classes sont donc
 * inévitables, mais le comportement, lui, n'est écrit qu'une fois et leur est
 * délégué.
 */
private interface NetworkEvents {
    fun onCapabilities(network: Network, capabilities: NetworkCapabilities)

    fun onLost(network: Network)
}

/**
 * `FLAG_INCLUDE_LOCATION_INFO` demande au système de ne pas expurger le SSID
 * des capacités livrées.
 */
@RequiresApi(Build.VERSION_CODES.S)
private class LocationAwareCallback(
    private val events: NetworkEvents,
) : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {

    override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities,
    ) = events.onCapabilities(network, networkCapabilities)

    override fun onLost(network: Network) = events.onLost(network)
}

/** Avant Android 12, le SSID passe par `WifiManager` plutôt que par ce callback. */
private class PlainCallback(
    private val events: NetworkEvents,
) : ConnectivityManager.NetworkCallback() {

    override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities,
    ) = events.onCapabilities(network, networkCapabilities)

    override fun onLost(network: Network) = events.onLost(network)
}
