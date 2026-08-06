package fr.vbrosseau.tailscaleautorules.automation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Choisit le mécanisme d'observation selon le mode demandé.
 *
 * Les deux modes s'excluent : laisser tourner le service *et* le travail
 * périodique doublerait les cycles sans rien apporter. Chaque armement arrête
 * donc explicitement l'autre, plutôt que de supposer qu'il ne tourne pas.
 *
 * Toutes les opérations sont idempotentes : démarrer un service déjà démarré ne
 * fait que lui renvoyer une commande, et le travail périodique est enregistré
 * sous un nom unique.
 */
@Singleton
class AndroidAutomationTrigger @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AutomationTrigger {

    override fun arm(immediate: Boolean) {
        if (immediate) {
            PeriodicSyncWorker.cancel(context)
            TunnelWatchService.start(context)
        } else {
            TunnelWatchService.stop(context)
            PeriodicSyncWorker.schedule(context)
        }

        Timber.i("Observation armée (mode %s)", if (immediate) "immédiat" else "économe")
    }

    override fun disarm() {
        TunnelWatchService.stop(context)
        PeriodicSyncWorker.cancel(context)
        Timber.i("Observation désarmée")
    }
}
