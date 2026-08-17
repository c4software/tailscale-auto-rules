package fr.vbrosseau.tailscaleautorules.automation

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.usecase.CaptureManualOverrideUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.DescribeTunnelStatusUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizationOutcome
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizeTunnelUseCase
import fr.vbrosseau.tailscaleautorules.notification.TunnelNotifier
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val captureManualOverride: CaptureManualOverrideUseCase,
    private val describeTunnelStatus: DescribeTunnelStatusUseCase,
    private val notifier: TunnelNotifier,
) : NotificationRefresher {

    /**
     * Sérialise cycles et capture de geste.
     *
     * Sans lui, un battement de secours pourrait basculer le tunnel entre le
     * constat d'un geste et sa mémorisation — l'exception enregistrerait alors
     * l'état posé par l'automatisation, pas celui choisi par l'utilisateur.
     */
    private val cycleMutex = Mutex()

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
     * Réarme l'automatisation après un redémarrage du terminal — ou explique
     * pourquoi elle ne peut pas repartir seule.
     *
     * Une localisation limitée à « pendant l'utilisation » est une permission
     * de premier plan : Android rejette alors le démarrage, depuis
     * l'arrière-plan, d'un service de type « localisation » — celui-là même
     * que la lecture du SSID impose. Tenter quand même ferait mourir le
     * service à la naissance ; à la place, une notification invite à ouvrir
     * l'application, seul geste qui rende le démarrage à nouveau permis.
     *
     * @param isStartableFromBackground faux uniquement quand la localisation
     *   est accordée sans l'être « toujours » : sans localisation du tout, le
     *   service part sans le type en cause et rien ne le bloque.
     */
    suspend fun applySettingsAfterBoot(settings: AppSettings, isStartableFromBackground: Boolean) {
        if (settings.isServiceEnabled && !isStartableFromBackground) {
            Timber.i("Service non démarrable depuis le boot : rappel publié")
            notifier.showStartupReminder()
            return
        }

        applySettings(settings)
        synchronize()
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
        val outcome =
            cycleMutex.withLock {
                captureBeforeCycle()
                synchronizeTunnel()
            }
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
        val outcome =
            cycleMutex.withLock {
                captureBeforeCycle()
                synchronizeTunnel(networkContext)
            }
        refreshNotificationIfEnabled()
        return outcome
    }

    /**
     * Constate un mouvement du tunnel survenu hors cycle, une fois l'état posé.
     *
     * Un geste manuel avéré est mémorisé comme exception dynamique
     * (SPECS.md §3.3), puis la notification est réalignée — qu'il y ait eu
     * geste ou simple écho d'une commande.
     */
    suspend fun onTunnelStateSettled() {
        cycleMutex.withLock { captureBeforeCycle() }
        refreshNotificationIfEnabled()
    }

    /**
     * Constate et mémorise un éventuel geste en attente, sans jamais faire
     * échouer le cycle qui suit.
     *
     * Appelée aussi **avant chaque cycle**, pas seulement quand le tunnel
     * bouge : l'instantané pris à la stabilisation peut être perturbé — le
     * VPN qui monte bouscule le réseau — et un geste raté à cet instant-là
     * serait sinon combattu par le battement de secours au lieu d'être
     * mémorisé.
     */
    private suspend fun captureBeforeCycle() {
        runCatching { captureManualOverride() }
            .onSuccess { recorded ->
                if (recorded) Timber.i("Geste manuel mémorisé pour le réseau courant")
            }
            .onFailure { Timber.e(it, "Constat du geste manuel en échec") }
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
