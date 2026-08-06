package fr.vbrosseau.tailscaleautorules.automation

import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleController
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizationOutcome
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizeTunnelUseCase
import fr.vbrosseau.tailscaleautorules.notification.TunnelNotifier
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applique les préférences à la mécanique Android et exécute les réveils.
 *
 * C'est la charnière entre ce que l'utilisateur a demandé — automatisation
 * active, notification visible — et ce que la plateforme doit faire. Le cas
 * d'usage reste ignorant de tout cela : il décide, ce coordinateur exécute.
 */
@Singleton
class AutomationCoordinator @Inject constructor(
    private val trigger: AutomationTrigger,
    private val settingsRepository: SettingsRepository,
    private val synchronizeTunnel: SynchronizeTunnelUseCase,
    private val controller: TailscaleController,
    private val journalRepository: JournalRepository,
    private val notifier: TunnelNotifier,
) {

    /**
     * Aligne la plateforme sur les préférences courantes.
     *
     * Appelée au démarrage de l'application, après un redémarrage du terminal,
     * et à chaque modification des paramètres.
     */
    suspend fun applySettings(settings: AppSettings) {
        if (settings.isServiceEnabled) trigger.arm() else trigger.disarm()

        if (settings.showPersistentNotification && settings.isServiceEnabled) {
            refreshNotification()
        } else {
            // Désactiver l'automatisation retire aussi la notification :
            // laisser un état affiché que plus rien ne met à jour serait pire
            // que ne rien afficher.
            notifier.hide()
        }
    }

    /** Exécute un cycle et met à jour la notification si elle est visible. */
    suspend fun synchronize(): SynchronizationOutcome {
        val outcome = synchronizeTunnel()

        if (settingsRepository.currentAppSettings().showPersistentNotification) {
            refreshNotification()
        }

        return outcome
    }

    private suspend fun refreshNotification() {
        val state = when {
            !controller.isAvailable() -> TunnelState.UNKNOWN
            controller.isRunning() -> TunnelState.ENABLED
            else -> TunnelState.DISABLED
        }

        // La raison affichée vient du journal, donc du dernier changement
        // réellement appliqué — et non de la dernière décision envisagée.
        notifier.show(state, journalRepository.observeRecent().first().firstOrNull()?.ruleId)
    }
}
