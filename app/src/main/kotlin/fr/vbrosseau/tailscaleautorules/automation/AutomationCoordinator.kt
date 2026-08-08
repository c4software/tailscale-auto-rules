package fr.vbrosseau.tailscaleautorules.automation

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.usecase.DescribeTunnelStatusUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizationOutcome
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizeTunnelUseCase
import fr.vbrosseau.tailscaleautorules.notification.TunnelNotifier
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
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
    private val describeTunnelStatus: DescribeTunnelStatusUseCase,
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

    /**
     * Redémarre l'observation continue après l'octroi de la localisation.
     *
     * Les types du service de premier plan — dont « localisation », qui
     * conditionne la lecture du SSID en arrière-plan — sont figés à son
     * démarrage. Un octroi survenu service déjà lancé resterait donc lettre
     * morte : le SSID demeurerait expurgé et la règle des réseaux de confiance
     * muette hors de l'écran, jusqu'au prochain redémarrage du service. Le
     * cycle complet arrêt-démarrage réenregistre au passage l'observation
     * réseau, dont les capacités mémorisées avaient été livrées expurgées.
     */
    suspend fun onLocationPermissionGranted() {
        if (!settingsRepository.currentAppSettings().isServiceEnabled) return

        trigger.disarm()
        trigger.arm()
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

    private suspend fun refreshNotification() {
        val status = describeTunnelStatus()

        Timber.i(
            "Notification : état %s, règle %s, geste manuel %b",
            status.state,
            status.ruleId?.value ?: "aucune",
            status.isManuallyOverridden,
        )

        notifier.show(status.state, status.ruleId, status.isManuallyOverridden)
    }
}
