package fr.vbrosseau.tailscaleautorules.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.vbrosseau.tailscaleautorules.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Réveillé par le système à chaque changement de réseau.
 *
 * `goAsync()` prolonge la vie du receveur le temps de la synchronisation :
 * sans lui, le processus pourrait être arrêté avant la fin du cycle.
 */
@AndroidEntryPoint
class NetworkChangeReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: AutomationCoordinator

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        scope.launch {
            try {
                coordinator.synchronize()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
