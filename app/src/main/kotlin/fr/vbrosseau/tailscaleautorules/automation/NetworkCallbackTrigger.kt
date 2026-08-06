package fr.vbrosseau.tailscaleautorules.automation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Réveil par diffusion, sans processus permanent.
 *
 * **Cette approche ne tient pas — elle est conservée le temps de la remplacer.**
 * L'intention était de confier l'observation au système pour que la notification
 * d'état reste optionnelle (SPECS.md §7). Deux mesures sur appareil l'ont
 * infirmée :
 *
 * 1. `registerNetworkCallback(NetworkRequest, PendingIntent)` livre l'état
 *    courant **immédiatement**, dans la milliseconde qui suit l'inscription ;
 * 2. `ConnectivityService` relâche ensuite l'inscription **cinq secondes après
 *    cette livraison** — le journal système est sans ambiguïté :
 *
 * ```
 * 22:19:00.820  REGISTER … to trigger PendingIntent{5d98ada}
 * 22:19:00.821  ConnectivityService: Sending PendingIntent{5d98ada}
 * 22:19:00.893  ConnectivityService: Finished sending PendingIntent{5d98ada}
 * 22:19:05.898  RELEASE  … callbackRequest: 57492
 * ```
 *
 * Une inscription ne vaut donc que pour un seul réveil, consommé sur-le-champ :
 * aucun changement de réseau ultérieur n'est jamais observé. Réarmer à chaque
 * réveil rétablit bien la couverture, mais chaque réarmement provoque sa propre
 * livraison immédiate — mesuré à 463 réveils en 50 secondes. Les deux issues
 * sont inacceptables.
 *
 * L'observation en arrière-plan exige donc un processus vivant. Voir TASKS.md
 * pour l'architecture qui remplace celle-ci.
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
        Timber.i("Réveil armé")
    }

    override fun disarm() {
        connectivityManager?.unregisterNetworkCallback(wakeUpIntent())
        Timber.i("Réveil désarmé")
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
