package fr.vbrosseau.tailscaleautorules.automation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Réveille l'application par diffusion, sans processus permanent.
 *
 * `ConnectivityManager.registerNetworkCallback(NetworkRequest, PendingIntent)`
 * confie l'observation au système : il réveille [NetworkChangeReceiver] quand
 * le réseau change, et l'application ne consomme rien entre-temps.
 *
 * C'est ce qui permet à la notification d'état de rester **optionnelle**
 * (SPECS.md §7). Un service de premier plan, seule alternative pour observer en
 * continu, imposerait une notification permanente sur Android 8 et suivants.
 *
 * **Écart assumé.** Le debounce du domaine ne s'applique pas ici : chaque
 * réveil déclenche une synchronisation. Ce n'est pas un problème en pratique,
 * le cas d'usage n'agissant que si l'état visé diffère de l'état constaté —
 * une rafale d'événements produit donc au plus une commande, et une seule
 * entrée de journal.
 */
@Singleton
class NetworkCallbackTrigger @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AutomationTrigger {

    private val connectivityManager: ConnectivityManager?
        get() = context.getSystemService(ConnectivityManager::class.java)

    override fun arm() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        connectivityManager?.registerNetworkCallback(request, wakeUpIntent())
    }

    override fun disarm() {
        connectivityManager?.unregisterNetworkCallback(wakeUpIntent())
    }

    /**
     * Diffusion explicite vers notre propre receveur.
     *
     * `FLAG_MUTABLE` est requis : le système renseigne lui-même les extras
     * décrivant le réseau concerné.
     */
    private fun wakeUpIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, NetworkChangeReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 1
    }
}
