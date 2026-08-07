package fr.vbrosseau.tailscaleautorules.automation

import android.app.Notification
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleController
import fr.vbrosseau.tailscaleautorules.domain.usecase.DetectManualOverrideUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizationOutcome
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizeTunnelUseCase
import fr.vbrosseau.tailscaleautorules.notification.TunnelNotifier
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import kotlinx.coroutines.flow.first
import timber.log.Timber
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
    private val detectManualOverride: DetectManualOverrideUseCase,
    private val controller: TailscaleController,
    private val journalRepository: JournalRepository,
    private val notifier: TunnelNotifier,
) : NotificationRefresher {

    /**
     * Aligne la plateforme sur les préférences courantes.
     *
     * Appelée au démarrage de l'application, après un redémarrage du terminal,
     * et à chaque modification des paramètres.
     */
    suspend fun applySettings(settings: AppSettings) {
        if (settings.isServiceEnabled) trigger.arm() else trigger.disarm()

        applyNotification(settings)
    }

    /**
     * Aligne la seule notification, sans toucher au réveil.
     *
     * L'interface s'en sert au retour à l'écran : l'utilisateur peut avoir
     * accordé la permission de notification entre-temps. Réappliquer *tous*
     * les réglages à cette occasion réenregistrerait le réveil auprès du
     * système à chaque reprise, ce qui le faisait churner inutilement.
     */
    override suspend fun refreshNotificationIfEnabled() =
        applyNotification(settingsRepository.currentAppSettings())

    /**
     * Les réglages sont reçus en paramètre plutôt que relus.
     *
     * [applySettings] arme le réveil d'après ceux qu'on lui donne : lire le
     * dépôt ici ferait décider les deux moitiés d'un même appel sur des états
     * potentiellement différents.
     */
    private suspend fun applyNotification(settings: AppSettings) {
        if (settings.notificationIsVisible) {
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
        refreshNotificationIfEnabled()
        return outcome
    }

    /**
     * Cycle sur un contexte déjà stabilisé.
     *
     * C'est la forme qu'emploie l'observation continue : le contexte qui a
     * déclenché le cycle est celui sur lequel on décide. Le relire perdrait le
     * bénéfice du debounce et pourrait livrer un état différent.
     */
    suspend fun synchronize(networkContext: NetworkContext): SynchronizationOutcome {
        val outcome = synchronizeTunnel(networkContext)
        refreshNotificationIfEnabled()
        return outcome
    }

    /**
     * Notification décrivant l'état courant, sans la publier.
     *
     * Le service de premier plan doit en fournir une à `startForeground` dès
     * son démarrage, avant tout cycle.
     */
    suspend fun currentNotification(): Notification =
        currentStatus().let { notifier.build(it.state, it.ruleId, it.isManuallyOverridden) }

    private suspend fun refreshNotification() {
        Timber.d("Rafraîchissement de la notification")
        currentStatus().let { notifier.show(it.state, it.ruleId, it.isManuallyOverridden) }
    }

    /**
     * Ce que la notification raconte : l'état constaté, la dernière règle
     * appliquée — lue au journal, donc jamais une décision seulement
     * envisagée — et l'éventuelle intervention manuelle qui contredit cette
     * règle. Sans ce dernier constat, la notification attribuerait à une règle
     * un état qu'elle n'a pas produit.
     */
    private suspend fun currentStatus(): TunnelStatus {
        val state = tunnelState()
        val lastChange = journalRepository.observeRecent().first().firstOrNull()
        val override = detectManualOverride(state, lastChange)

        return TunnelStatus(state, lastChange?.ruleId, override != null)
    }

    private suspend fun tunnelState(): TunnelState = when {
        !controller.isAvailable() -> TunnelState.UNKNOWN
        controller.isRunning() -> TunnelState.ENABLED
        else -> TunnelState.DISABLED
    }

    private data class TunnelStatus(
        val state: TunnelState,
        val ruleId: RuleId?,
        val isManuallyOverridden: Boolean,
    )
}
