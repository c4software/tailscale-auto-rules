package fr.vbrosseau.tailscaleautorules.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.vbrosseau.tailscaleautorules.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Réveillé par le système à chaque changement de réseau.
 *
 * `goAsync()` prolonge la vie du receveur le temps de la synchronisation :
 * sans lui, le processus pourrait être arrêté avant la fin du cycle.
 *
 */
@AndroidEntryPoint
class NetworkChangeReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: AutomationCoordinator

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("Réveil réseau reçu")
        val pendingResult = goAsync()

        scope.launch {
            // Un échec ici serait autrement invisible : le receveur s'exécute
            // sans interface et sans utilisateur pour le constater. Et quel
            // qu'en soit le sort, la diffusion doit être close, sans quoi le
            // système finit par tuer le processus pour dépassement de délai.
            runCatching { coordinator.synchronize() }
                .onSuccess { Timber.i("Cycle terminé : %s", it) }
                .onFailure { Timber.e(it, "Cycle en échec") }

            pendingResult.finish()
        }
    }
}
