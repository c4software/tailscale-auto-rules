package fr.vbrosseau.tailscaleautorules.automation

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.network.FakeNetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeBlacklistRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeJournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeSettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.MobileNetworkRule
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import fr.vbrosseau.tailscaleautorules.domain.tailscale.FakeTailscaleController
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizationOutcome
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizeTunnelUseCase
import fr.vbrosseau.tailscaleautorules.notification.TunnelNotifier
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
    private val controller = FakeTailscaleController()
    private val settings = FakeSettingsRepository()
    private val journal = FakeJournalRepository()
    private val observer = FakeNetworkObserver()
    private lateinit var coordinator: AutomationCoordinator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(context as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        coordinator = AutomationCoordinator(
            trigger = trigger,
            settingsRepository = settings,
            synchronizeTunnel = SynchronizeTunnelUseCase(
                networkObserver = observer,
                blacklistRepository = FakeBlacklistRepository(),
                settingsRepository = settings,
                engine = RuleEngine(setOf(MobileNetworkRule())),
                controller = controller,
                journalRepository = journal,
            ),
            controller = controller,
            journalRepository = journal,
            notifier = TunnelNotifier(context),
        )
    }

    private fun postedNotification() = Shadows.shadowOf(
        context.getSystemService(NotificationManager::class.java),
    ).getNotification(TunnelNotifier.NOTIFICATION_ID)

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
    fun reapplyingIdenticalSettingsStaysArmedWithoutPilingUp() = runTest {
        val enabled = AppSettings(isServiceEnabled = true)

        coordinator.applySettings(enabled)
        coordinator.applySettings(enabled)

        assertTrue(trigger.isArmed)
        assertEquals(2, trigger.armCount, "Le réarmement est idempotent côté système.")
    }
}
