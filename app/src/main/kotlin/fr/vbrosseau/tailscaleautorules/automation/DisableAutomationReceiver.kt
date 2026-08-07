package fr.vbrosseau.tailscaleautorules.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.vbrosseau.tailscaleautorules.di.ApplicationScope
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Désactive l'automatisation depuis l'action de la notification.
 *
 * Il n'arrête pas le service lui-même : il bascule la **préférence**, seule
 * source de vérité, et l'observation des réglages tenue par l'application
 * désarme alors le service et retire la notification — exactement comme si
 * l'interrupteur de l'écran des paramètres avait été actionné. Court-circuiter
 * la préférence laisserait un réglage « actif » pour un service arrêté, et
 * l'automatisation ressusciterait au prochain démarrage.
 */
@AndroidEntryPoint
class DisableAutomationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISABLE_AUTOMATION) return

        val pendingResult = goAsync()

        scope.launch {
            try {
                Timber.i("Automatisation désactivée depuis la notification")
                settingsRepository.updateAppSettings { it.copy(isServiceEnabled = false) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DISABLE_AUTOMATION =
            "fr.vbrosseau.tailscaleautorules.action.DISABLE_AUTOMATION"

        /** Intention explicite : seule cette application peut la recevoir. */
        fun intent(context: Context): Intent =
            Intent(context, DisableAutomationReceiver::class.java)
                .setAction(ACTION_DISABLE_AUTOMATION)
    }
}
