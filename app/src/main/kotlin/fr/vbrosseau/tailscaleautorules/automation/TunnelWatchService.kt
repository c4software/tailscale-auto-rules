package fr.vbrosseau.tailscaleautorules.automation

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import fr.vbrosseau.tailscaleautorules.di.IoDispatcher
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleController
import fr.vbrosseau.tailscaleautorules.notification.TunnelNotifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Observe le réseau en continu et synchronise le tunnel à chaque changement.
 *
 * **Pourquoi un service de premier plan.** L'observation du réseau exige un
 * processus vivant. Le mécanisme qui permettait de s'en passer —
 * `registerNetworkCallback(NetworkRequest, PendingIntent)` — s'est révélé
 * inopérant à la mesure : le système ne livre qu'un seul réveil, immédiat, puis
 * relâche l'inscription cinq secondes plus tard. L'automatisation cessait donc
 * dès le premier changement de réseau. Voir [PeriodicSyncWorker] pour le mode
 * économe, et TASKS.md étape 17 pour les relevés.
 *
 * La notification permanente qu'Android impose ici n'est pas une gêne subie :
 * c'est la notification d'état de l'application, et elle porte l'information
 * utile — état du tunnel et règle qui l'a décidé.
 *
 * Le contexte réseau arrive **stabilisé** par le domaine : une rafale de
 * changements — typiquement une bascule Wi-Fi vers cellulaire, qui en produit
 * plusieurs — ne déclenche qu'un seul cycle.
 */
@AndroidEntryPoint
class TunnelWatchService : Service() {

    @Inject
    lateinit var coordinator: AutomationCoordinator

    @Inject
    lateinit var networkObserver: NetworkObserver

    @Inject
    lateinit var controller: TailscaleController

    @Inject
    lateinit var notifier: TunnelNotifier

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    /**
     * Portée propre au service, close avec lui.
     *
     * Volontairement distincte de la portée applicative : ce qui est observé
     * ici n'a pas de sens une fois le service arrêté, et doit s'arrêter avec.
     */
    private val scope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }

    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Impérativement synchrone et en tête : la plateforme tue un service de
        // premier plan qui n'a pas produit sa notification dans les secondes
        // qui suivent son démarrage.
        startInForeground()

        if (watchJob == null) {
            watchJob = scope.launch { watchNetwork() }
            scope.launch { watchTunnel() }
            Timber.i("Observation continue démarrée")
        }

        // Redémarré s'il est tué : l'automatisation doit survivre à la pression
        // mémoire, sans quoi elle s'arrêterait silencieusement.
        return START_STICKY
    }

    override fun onDestroy() {
        Timber.i("Observation continue arrêtée")
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun watchNetwork() {
        // `collectLatest` abandonne un cycle encore en cours si le réseau
        // rebascule : c'est le contexte le plus récent qui doit décider.
        networkObserver.observe().collectLatest { networkContext ->
            Timber.i("Contexte observé : %s", networkContext)
            runCatching { coordinator.synchronize(networkContext) }
                .onSuccess { Timber.i("Cycle terminé : %s", it) }
                .onFailure { Timber.e(it, "Cycle en échec") }
        }
    }

    /**
     * Réaligne la notification quand le tunnel bouge **sans** cycle : coupé
     * depuis le client officiel, ou activé à la main sur un réseau de
     * confiance. Aucun changement de réseau physique n'accompagne ces gestes,
     * et la notification resterait sinon figée sur un état périmé.
     *
     * Le rafraîchissement attend que l'état se pose : au moment même de
     * l'événement, le réseau actif est encore en retard sur la bascule, et
     * l'état relu figerait dans la notification un constat déjà faux — un
     * « tunnel activé » qui vient pourtant d'être coupé. `collectLatest`
     * remet l'attente à zéro si le tunnel rebascule entre-temps.
     */
    private suspend fun watchTunnel() {
        controller.observeRunning().collectLatest {
            delay(TUNNEL_SETTLE_WINDOW)
            runCatching { coordinator.refreshNotificationIfEnabled() }
                .onFailure { Timber.e(it, "Rafraîchissement de la notification en échec") }
        }
    }

    /**
     * Publie la notification exigée par la plateforme.
     *
     * L'état affiché est provisoirement inconnu : le lire demanderait une
     * suspension, or l'appel doit être immédiat. Le premier cycle, quelques
     * instants plus tard, le corrige.
     */
    private fun startInForeground() {
        val notification = notifier.build(TunnelState.UNKNOWN, ruleId = null)

        ServiceCompat.startForeground(
            this,
            TunnelNotifier.NOTIFICATION_ID,
            notification,
            foregroundServiceTypes(),
        )
    }

    /**
     * Types déclarés au démarrage, dont « localisation » **seulement** si la
     * permission est accordée.
     *
     * Le type conditionne l'accès au SSID : sans lui, le nom du réseau revient
     * vide en arrière-plan et la règle des réseaux de confiance ne s'applique
     * jamais. Mais le déclarer sans détenir la permission fait rejeter le
     * démarrage du service — l'automatisation ne partirait alors pas du tout,
     * pour un réglage dont l'utilisateur n'a peut-être aucun besoin.
     */
    private fun foregroundServiceTypes(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return 0

        val canReadSsid = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        Timber.i("Service démarré (lecture du SSID : %b)", canReadSsid)

        return if (canReadSsid) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
    }

    companion object {
        /** Laisse la bascule du tunnel se terminer avant de relire son état. */
        private val TUNNEL_SETTLE_WINDOW = 2.seconds

        /** Démarre l'observation continue, sans effet si elle tourne déjà. */
        fun start(context: Context) {
            val intent = Intent(context, TunnelWatchService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TunnelWatchService::class.java))
        }
    }
}
