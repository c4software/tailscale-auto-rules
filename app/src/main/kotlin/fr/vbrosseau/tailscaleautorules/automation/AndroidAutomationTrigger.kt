package fr.vbrosseau.tailscaleautorules.automation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Démarre et arrête le service qui observe le réseau.
 *
 * Une seule mécanique, et c'est voulu. Un mode « économe » par travail
 * périodique avait été envisagé pour éviter la notification permanente : il ne
 * pouvait pas tenir la promesse de l'application, la plateforme n'acceptant pas
 * de période plus courte que quinze minutes — un changement de réseau serait
 * resté sans effet un quart d'heure durant.
 *
 * Les opérations sont idempotentes : démarrer un service déjà démarré ne fait
 * que lui transmettre une commande de plus.
 */
@Singleton
class AndroidAutomationTrigger @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AutomationTrigger {

    override fun arm() {
        TunnelWatchService.start(context)
        Timber.i("Observation armée")
    }

    override fun disarm() {
        TunnelWatchService.stop(context)
        Timber.i("Observation désarmée")
    }
}
