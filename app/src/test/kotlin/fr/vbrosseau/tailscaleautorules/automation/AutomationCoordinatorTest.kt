package fr.vbrosseau.tailscaleautorules.automation

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.tailscaleautorules.R
import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.FakeNetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeJournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeNetworkPreferenceRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeSettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.MobileNetworkRule
import fr.vbrosseau.tailscaleautorules.domain.rule.NetworkPreferenceRule
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import fr.vbrosseau.tailscaleautorules.domain.tailscale.FakeTailscaleController
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import fr.vbrosseau.tailscaleautorules.domain.usecase.CaptureManualOverrideUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.DescribeTunnelStatusUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.DetectManualOverrideUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.EvaluateRulesUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.RecordManualOverrideUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizationOutcome
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizeTunnelUseCase
import fr.vbrosseau.tailscaleautorules.notification.TunnelNotifier
import fr.vbrosseau.tailscaleautorules.presentation.FakeSystemStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve la charnière entre les préférences et la mécanique Android.
 *
 * Le déclencheur est remplacé par un double inspectable : ce qui est vérifié
 * ici, c'est la décision d'armer ou non, pas la mécanique de
 * `ConnectivityManager`.
 */
@RunWith(RobolectricTestRunner::class)
class AutomationCoordinatorTest {

    private class FakeAutomationTrigger : AutomationTrigger {
        var isArmed: Boolean = false
            private set
        var armCount: Int = 0
            private set

        override fun arm() {
            isArmed = true
            armCount++
        }

        override fun disarm() {
            isArmed = false
        }
    }

    private lateinit var context: Context
    private val trigger = FakeAutomationTrigger()
    private val systemStatus = FakeSystemStatus()
    private val controller = FakeTailscaleController()
    private val settings = FakeSettingsRepository()
    private val clock = FakeClock()
    private val journal = FakeJournalRepository(clock)
    private val observer = FakeNetworkObserver()
    private val exceptions = FakeNetworkPreferenceRepository(clock)
    private lateinit var coordinator: AutomationCoordinator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(context as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val engine = RuleEngine(setOf(NetworkPreferenceRule(), MobileNetworkRule()))
        val evaluateRules = EvaluateRulesUseCase(exceptions, settings, engine)
        val detectManualOverride = DetectManualOverrideUseCase(
            networkObserver = observer,
            evaluateRules = evaluateRules,
            clock = clock,
        )

        coordinator = AutomationCoordinator(
            trigger = trigger,
            settingsRepository = settings,
            synchronizeTunnel = SynchronizeTunnelUseCase(
                networkObserver = observer,
                settingsRepository = settings,
                evaluateRules = evaluateRules,
                controller = controller,
                journalRepository = journal,
            ),
            captureManualOverride = CaptureManualOverrideUseCase(
                networkObserver = observer,
                controller = controller,
                journalRepository = journal,
                detectManualOverride = detectManualOverride,
                recordManualOverride = RecordManualOverrideUseCase(settings, exceptions, journal),
            ),
            describeTunnelStatus = DescribeTunnelStatusUseCase(
                networkObserver = observer,
                evaluateRules = evaluateRules,
                detectManualOverride = detectManualOverride,
                journalRepository = journal,
                controller = controller,
            ),
            notifier = TunnelNotifier(context),
            systemStatus = systemStatus,
        )
    }

    private fun postedNotification() = Shadows.shadowOf(
        context.getSystemService(NotificationManager::class.java),
    ).getNotification(TunnelNotifier.NOTIFICATION_ID)

    private fun postedReminder() = Shadows.shadowOf(
        context.getSystemService(NotificationManager::class.java),
    ).getNotification(TunnelNotifier.STARTUP_REMINDER_ID)

    @Test
    fun anEnabledServiceArmsTheTrigger() = runTest {
        coordinator.applySettings(AppSettings(isServiceEnabled = true))

        assertTrue(trigger.isArmed)
    }

    @Test
    fun aDisabledServiceDisarmsTheTrigger() = runTest {
        coordinator.applySettings(AppSettings(isServiceEnabled = true))

        coordinator.applySettings(AppSettings(isServiceEnabled = false))

        assertTrue(!trigger.isArmed)
    }

    @Test
    fun anActiveAutomationAlwaysShowsTheNotification() = runTest {
        // Elle n'est pas une option : Android l'impose au service qui observe
        // le réseau. L'afficher sans que l'utilisateur l'ait demandée est donc
        // le comportement attendu, pas un excès de zèle.
        coordinator.applySettings(AppSettings(isServiceEnabled = true))

        assertNotNull(postedNotification())
    }

    @Test
    fun disablingTheServiceAlsoRemovesTheNotification() = runTest {
        // Laisser affiché un état que plus rien ne met à jour serait pire que
        // ne rien afficher.
        coordinator.applySettings(AppSettings(isServiceEnabled = true))
        assertNotNull(postedNotification())

        coordinator.applySettings(AppSettings(isServiceEnabled = false))

        assertNull(postedNotification())
    }

    @Test
    fun synchronizingRunsTheCycle() = runTest {
        observer.emit(NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true))

        val outcome = coordinator.synchronize()

        assertIs<SynchronizationOutcome.Applied>(outcome)
        assertTrue(controller.isRunning())
    }

    @Test
    fun synchronizingRefreshesTheNotification() = runTest {
        observer.emit(NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true))

        coordinator.synchronize()

        assertNotNull(postedNotification())
    }

    @Test
    fun synchronizingPostsNothingWhenAutomationIsOff() = runTest {
        settings.updateAppSettings { it.copy(isServiceEnabled = false) }
        observer.emit(NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true))

        coordinator.synchronize()

        assertNull(postedNotification())
    }

    @Test
    fun theNotificationCallsOutAManuallyChangedTunnel() = runTest {
        // Sur réseau mobile, la règle a activé le tunnel, puis l'utilisateur
        // l'a coupé depuis le client officiel. La notification doit le dire,
        // au lieu d'attribuer l'état constaté à la dernière règle appliquée.
        observer.emit(NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true))
        coordinator.synchronize()

        clock.advanceBy(60_000)
        controller.disable()
        coordinator.refreshNotificationIfEnabled()

        val notification = assertNotNull(postedNotification())
        assertEquals(
            context.getString(R.string.notification_manual_override),
            notification.extras.getString(Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun aDivergenceInsideTheGraceWindowStaysAttributedToTheRule() = runTest {
        // Le journal consigne la commande à l'envoi ; le tunnel met quelques
        // secondes à suivre. Pendant ce délai, chaque transition affichait à
        // tort « Modifié manuellement » : la notification doit s'en tenir à
        // la règle.
        observer.emit(NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true))
        coordinator.synchronize()

        controller.disable()
        coordinator.refreshNotificationIfEnabled()

        val notification = assertNotNull(postedNotification())
        assertNotEquals(
            context.getString(R.string.notification_manual_override),
            notification.extras.getString(Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun theReasonFollowsTheCurrentNetwork() = runTest {
        // Le cas signalé : le tunnel a été coupé sur un réseau de confiance —
        // seule trace au journal — puis rallumé à la main, et l'utilisateur est
        // passé en données mobiles. La règle du réseau mobile confirme un
        // tunnel déjà actif, donc n'écrit rien : la raison lue au journal
        // restait celle d'un changement révolu sous un titre « Tunnel activé ».
        exceptions.upsert(NetworkPreferenceKey.forWifi("Maison"), "Maison", TunnelState.DISABLED)
        observer.emit(NetworkContext(NetworkTransport.WIFI, isInternetValidated = true, ssid = "Maison"))
        controller.enable()
        coordinator.synchronize()

        clock.advanceBy(60_000)
        controller.enable()
        observer.emit(NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true))
        coordinator.refreshNotificationIfEnabled()

        val notification = assertNotNull(postedNotification())
        assertEquals(
            context.getString(R.string.notification_reason, context.getString(R.string.rule_mobile_network)),
            notification.extras.getString(Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun aSettledManualGestureIsMemorizedAndThenRespectedByTheCycles() = runTest {
        // Sur réseau mobile la règle a activé le tunnel, puis l'utilisateur l'a
        // coupé depuis le client officiel. Une fois l'état posé, le geste est
        // mémorisé — et le cycle suivant le respecte au lieu de le combattre.
        observer.emit(NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true))
        coordinator.synchronize()
        clock.advanceBy(60_000)
        controller.disable()

        coordinator.onTunnelStateSettled()

        val exception = exceptions.observeAll().first().single()
        assertEquals(TunnelState.DISABLED, exception.desiredState)

        val outcome = coordinator.synchronize()
        assertIs<SynchronizationOutcome.AlreadyInTargetState>(outcome)
        assertTrue(!controller.isRunning(), "Le battement ne rallume pas un tunnel coupé à la main.")
    }

    @Test
    fun aCycleMemorizesAPendingGestureInsteadOfFightingIt() = runTest {
        // Le constat pris à la stabilisation du tunnel peut être perturbé par
        // le VPN qui monte, et n'avoir rien mémorisé. Le cycle suivant —
        // battement de secours compris — doit alors mémoriser le geste encore
        // constaté, pas rallumer le tunnel que l'utilisateur vient de couper.
        observer.emit(NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true))
        coordinator.synchronize()
        clock.advanceBy(60_000)
        controller.disable()

        val outcome = coordinator.synchronize()

        assertEquals(TunnelState.DISABLED, exceptions.observeAll().first().single().desiredState)
        assertIs<SynchronizationOutcome.AlreadyInTargetState>(outcome)
        assertTrue(!controller.isRunning(), "Le geste est mémorisé, pas combattu.")
    }

    @Test
    fun anEchoOfAFreshCommandMemorizesNothingWhenTheTunnelSettles() = runTest {
        // Juste après un cycle, la divergence encore visible est la latence du
        // client : le passage du tunnel à l'état commandé ne doit pas créer
        // d'exception.
        observer.emit(NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true))
        coordinator.synchronize()
        controller.disable()

        coordinator.onTunnelStateSettled()

        assertTrue(exceptions.observeAll().first().isEmpty())
    }

    @Test
    fun aBackgroundStartBlockedByLocationPostsAReminderInsteadOfArming() = runTest {
        // Une localisation « pendant l'utilisation » fait rejeter le service
        // de type « localisation » démarré depuis l'arrière-plan : tenter
        // quand même le ferait mourir à la naissance. Le rappel est alors le
        // seul signe que l'automatisation attend l'ouverture de l'application.
        systemStatus.ssidReadable = true
        systemStatus.ssidReadableInBackground = false
        systemStatus.appInForeground = false

        coordinator.applySettings(AppSettings(isServiceEnabled = true))

        assertTrue(!trigger.isArmed)
        assertNotNull(postedReminder())
    }

    @Test
    fun aForegroundProcessArmsDespiteAForegroundOnlyLocation() = runTest {
        // Au premier plan, le démarrage est éligible quelle que soit la
        // permission : c'est précisément le geste que le rappel demande.
        systemStatus.ssidReadable = true
        systemStatus.ssidReadableInBackground = false
        systemStatus.appInForeground = true

        coordinator.applySettings(AppSettings(isServiceEnabled = true))

        assertTrue(trigger.isArmed)
        assertNull(postedReminder())
    }

    @Test
    fun aBackgroundStartWithoutAnyLocationArms() = runTest {
        // Sans localisation, le service part sans le type en cause : rien ne
        // bloque, et un rappel serait une fausse alerte.
        systemStatus.ssidReadable = false
        systemStatus.ssidReadableInBackground = false
        systemStatus.appInForeground = false

        coordinator.applySettings(AppSettings(isServiceEnabled = true))

        assertTrue(trigger.isArmed)
        assertNull(postedReminder())
    }

    @Test
    fun aBackgroundStartWithAlwaysAllowedLocationArms() = runTest {
        systemStatus.ssidReadable = true
        systemStatus.ssidReadableInBackground = true
        systemStatus.appInForeground = false

        coordinator.applySettings(AppSettings(isServiceEnabled = true))

        assertTrue(trigger.isArmed)
        assertNull(postedReminder())
    }

    @Test
    fun aDisabledAutomationNeverPostsAReminder() = runTest {
        // Sans automatisation, il n'y a rien à démarrer : inviter à ouvrir
        // l'application promettrait une synchronisation qui n'aura pas lieu.
        systemStatus.ssidReadable = true
        systemStatus.ssidReadableInBackground = false
        systemStatus.appInForeground = false

        coordinator.applySettings(AppSettings(isServiceEnabled = false))

        assertTrue(!trigger.isArmed)
        assertNull(postedReminder())
    }

    @Test
    fun comingToTheForegroundArmsAnEnabledAutomation() = runTest {
        // C'est la contrepartie du rappel : l'observation des réglages ne
        // réémet pas au retour à l'écran, personne d'autre n'honorerait
        // l'invitation quand le processus a survécu.
        systemStatus.ssidReadable = true
        systemStatus.ssidReadableInBackground = false
        systemStatus.appInForeground = false
        coordinator.applySettings(AppSettings(isServiceEnabled = true))
        assertTrue(!trigger.isArmed)

        systemStatus.appInForeground = true
        coordinator.onApplicationForeground()

        assertTrue(trigger.isArmed)
    }

    @Test
    fun comingToTheForegroundLeavesADisabledAutomationAlone() = runTest {
        settings.updateAppSettings { it.copy(isServiceEnabled = false) }

        coordinator.onApplicationForeground()

        assertTrue(!trigger.isArmed)
        assertEquals(0, trigger.armCount)
    }

    @Test
    fun aFreshLocationGrantRestartsAnEnabledService() = runTest {
        // Les types du service — dont « localisation », qui conditionne la
        // lecture du SSID en arrière-plan — sont figés à son démarrage. Sans
        // redémarrage, un octroi tardif laisserait la règle des réseaux de
        // confiance muette jusqu'au prochain reboot.
        coordinator.applySettings(AppSettings(isServiceEnabled = true))

        coordinator.onLocationPermissionGranted()

        assertTrue(trigger.isArmed)
        assertEquals(2, trigger.armCount, "Un cycle complet arrêt-démarrage a eu lieu.")
    }

    @Test
    fun aLocationGrantLeavesADisabledAutomationAlone() = runTest {
        settings.updateAppSettings { it.copy(isServiceEnabled = false) }

        coordinator.onLocationPermissionGranted()

        assertTrue(!trigger.isArmed)
        assertEquals(0, trigger.armCount)
    }

    @Test
    fun reapplyingIdenticalSettingsStaysArmedWithoutPilingUp() = runTest {
        val enabled = AppSettings(isServiceEnabled = true)

        coordinator.applySettings(enabled)
        coordinator.applySettings(enabled)

        assertTrue(trigger.isArmed)
        assertEquals(2, trigger.armCount, "Le réarmement est idempotent côté système.")
    }
}
