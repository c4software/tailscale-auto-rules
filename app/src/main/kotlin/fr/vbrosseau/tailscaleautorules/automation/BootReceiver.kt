package fr.vbrosseau.tailscaleautorules.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.vbrosseau.tailscaleautorules.di.ApplicationScope
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Réarme l'automatisation après un redémarrage du terminal.
 *
 * Les rappels réseau enregistrés par une session précédente ne survivent pas au
 * redémarrage : sans ce receveur, l'application resterait muette jusqu'à sa
 * prochaine ouverture manuelle.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: AutomationCoordinator

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()

        scope.launch {
            try {
                val settings = settingsRepository.currentAppSettings()
                // Le démarrage automatique est une préférence : la respecter
                // ici évite de réarmer une automatisation que l'utilisateur a
                // volontairement laissée au repos.
                if (settings.startOnBoot) {
                    coordinator.applySettingsAfterBoot(settings)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
