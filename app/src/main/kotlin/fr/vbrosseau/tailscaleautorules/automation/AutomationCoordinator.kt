package fr.vbrosseau.tailscaleautorules.automation

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.usecase.CaptureManualOverrideUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.DescribeTunnelStatusUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizationOutcome
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizeTunnelUseCase
import fr.vbrosseau.tailscaleautorules.notification.TunnelNotifier
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import fr.vbrosseau.tailscaleautorules.presentation.SystemStatus
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
    private val systemStatus: SystemStatus,
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
     * Appelée au démarrage de chaque processus, après un redémarrage du
     * terminal, et à chaque modification des paramètres.
     *
     * L'armement est conditionné par [isWatchStartableNow], la garde commune
     * à tous les points d'armement : ici, et au retour à l'écran dans
     * [onApplicationForeground] ; [onLocationPermissionGranted] réarme depuis
     * un écran, donc toujours au premier plan, et la satisfait d'office. Une
     * localisation limitée à « pendant l'utilisation » fait rejeter par
     * Android le service de type « localisation », celui qu'impose la lecture
     * du SSID, dès qu'il part de l'arrière-plan. Tenter quand même le ferait
     * mourir à la naissance (constaté au boot sur appareil). La garde ne peut pas vivre
     * dans le receveur de boot : l'observation des réglages arme depuis
     * n'importe quel démarrage de processus, y compris celui créé pour livrer
     * le boot, avant même que le receveur s'exécute.
     *
     * Le refus est silencieux : à l'ouverture à froid de l'application, ce
     * chemin court quelques instants avant la première activité, et publier
     * le rappel ici le ferait clignoter à chaque lancement. Le boot, seul
     * moment où personne ne viendra armer ensuite, publie le sien via
     * [applySettingsAfterBoot].
     */
    suspend fun applySettings(settings: AppSettings) {
        when {
            !settings.isServiceEnabled -> trigger.disarm()
            isWatchStartableNow -> trigger.arm()
            else -> Timber.i("Service non démarrable depuis l'arrière-plan : armement décliné")
        }

        applyNotification(settings)
    }

    /**
     * Chemin du redémarrage du terminal : applique, puis invite si besoin.
     *
     * Quand l'armement a été décliné, la notification de rappel est le seul
     * signe que l'automatisation attend l'ouverture de l'application : aucun
     * autre passage au premier plan ne viendra armer de lui-même. Le cycle
     * final vaut pour les règles lisibles sans SSID, données mobiles en tête,
     * et réatteste la décision courante au journal de cette session.
     */
    suspend fun applySettingsAfterBoot(settings: AppSettings) {
        applySettings(settings)

        if (settings.isServiceEnabled && !isWatchStartableNow) {
            Timber.i("Rappel de démarrage publié")
            notifier.showStartupReminder()
        }

        synchronize()
    }

    /**
     * Réarme au retour de l'application au premier plan.
     *
     * C'est la contrepartie du rappel : l'observation des réglages ne réémet
     * pas au simple retour à l'écran (`distinctUntilChanged`), donc personne
     * d'autre n'honorerait l'invitation quand le processus a survécu. Sans
     * effet si l'observation tourne déjà : démarrer un service démarré ne fait
     * que lui transmettre une commande de plus.
     */
    suspend fun onApplicationForeground() {
        if (!settingsRepository.currentAppSettings().isServiceEnabled) return
        if (!isWatchStartableNow) return

        trigger.arm()
    }

    /**
     * Le blocage ne vient que d'une localisation accordée, depuis
     * l'arrière-plan : sans localisation du tout, le service part sans le type
     * en cause, et au premier plan le démarrage est éligible quelle que soit
     * la permission.
     */
    private val isWatchStartableNow: Boolean
        get() = !systemStatus.canReadSsid() || systemStatus.isAppInForeground()

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
        if (!settings.notificationIsVisible) {
            // Désactiver l'automatisation retire aussi la notification :
            // laisser un état affiché que plus rien ne met à jour serait pire
            // que ne rien afficher.
            notifier.hide()
            return
        }

        val status = describeTunnelStatus()

        Timber.i(
            "Notification : état %s, règle %s, geste manuel %b",
            status.state,
            status.ruleId?.value ?: "aucune",
            status.isManuallyOverridden,
        )

        notifier.show(status.state, status.ruleId, status.isManuallyOverridden)
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

}
