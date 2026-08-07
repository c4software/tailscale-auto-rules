package fr.vbrosseau.tailscaleautorules.presentation.home

import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.FakeNetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeBlacklistRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeJournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeSettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.MobileNetworkRule
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.tailscale.FakeTailscaleController
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizeTunnelUseCase
import fr.vbrosseau.tailscaleautorules.presentation.MainDispatcherRule
import fr.vbrosseau.tailscaleautorules.presentation.keepCollecting
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val controller = FakeTailscaleController()
    private val observer = FakeNetworkObserver()
    private val journal = FakeJournalRepository(FakeClock(1_000))
    private val settings = FakeSettingsRepository()

    private fun TestScope.viewModel() = HomeViewModel(
        networkObserver = observer,
        journalRepository = journal,
        controller = controller,
        synchronizeTunnel = SynchronizeTunnelUseCase(
            networkObserver = observer,
            blacklistRepository = FakeBlacklistRepository(),
            settingsRepository = settings,
            engine = RuleEngine(setOf(MobileNetworkRule())),
            controller = controller,
            journalRepository = journal,
        ),
        settingsRepository = settings,
    ).also { keepCollecting(it.uiState) }

    private val cellular = NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true)

    @Test
    fun theInitialStateReflectsTheObservedNetworkAndTunnel() = runTest {
        observer.emit(cellular)

        val state = viewModel().uiState.value

        assertEquals(NetworkTransport.CELLULAR, state.transport)
        assertEquals(TunnelState.DISABLED, state.tunnelState)
        assertNull(state.ssid)
        assertNull(state.lastChange)
        assertTrue(!state.isLoading, "Le premier constat complet met fin au chargement.")
    }

    @Test
    fun theSsidIsExposedWhenAvailable() = runTest {
        observer.emit(
            NetworkContext(NetworkTransport.WIFI, isInternetValidated = true, ssid = "Maison"),
        )

        assertEquals("Maison", viewModel().uiState.value.ssid)
    }

    @Test
    fun anAbsentClientYieldsAnUnknownStateRatherThanDisabled() = runTest {
        // Confondre « pas installé » et « désactivé » ferait croire à
        // l'utilisateur que l'application veille alors qu'elle est inerte.
        controller.available = false

        val state = viewModel().uiState.value

        assertEquals(TunnelState.UNKNOWN, state.tunnelState)
        assertTrue(!state.isTailscaleInstalled)
    }

    @Test
    fun synchronizingAppliesTheDecisionAndUpdatesTheDisplayedState() = runTest {
        observer.emit(cellular)
        val model = viewModel()

        model.synchronize()

        val state = model.uiState.value
        assertEquals(TunnelState.ENABLED, state.tunnelState)
        assertEquals(RuleId("mobile-network"), state.lastChange?.ruleId)
        assertTrue(!state.isSynchronizing, "L'indicateur retombe une fois le cycle fini.")
    }

    @Test
    fun theDisplayedStateIsObservedNotDeduced() = runTest {
        // Le tunnel bouge sans passer par l'application : l'accueil doit
        // refléter la réalité, pas la dernière décision connue.
        val model = viewModel()
        assertEquals(TunnelState.DISABLED, model.uiState.value.tunnelState)

        controller.enable()
        observer.emit(cellular)

        assertEquals(TunnelState.ENABLED, model.uiState.value.tunnelState)
    }

    @Test
    fun theLastChangeFollowsTheJournal() = runTest {
        val model = viewModel()

        journal.record(TunnelState.DISABLED, TunnelState.ENABLED, RuleId("mobile-network"))

        assertEquals(RuleId("mobile-network"), model.uiState.value.lastChange?.ruleId)
    }

    @Test
    fun disablingTheAutomationOnlyFlipsThePreference() = runTest {
        // L'arrêt du service et le retrait de la notification appartiennent à
        // l'observation applicative : ici, seule la préférence doit bouger, et
        // l'écran doit la refléter.
        val model = viewModel()

        model.disableAutomation()

        assertTrue(!settings.currentAppSettings().isServiceEnabled)
        assertTrue(!model.uiState.value.isAutomationEnabled)
    }

    @Test
    fun aStableNetworkDoesNotProduceRepeatedCommands() = runTest {
        observer.emit(cellular)
        val model = viewModel()

        model.synchronize()
        model.synchronize()

        // Le second cycle constate que l'état visé est déjà atteint.
        assertEquals(1, controller.enableCount)
    }
}
