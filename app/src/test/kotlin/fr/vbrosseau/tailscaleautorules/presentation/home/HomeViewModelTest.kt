package fr.vbrosseau.tailscaleautorules.presentation.home

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
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.tailscale.FakeTailscaleController
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import fr.vbrosseau.tailscaleautorules.domain.usecase.DetectManualOverrideUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.EvaluateRulesUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.SessionAttestation
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
    private val clock = FakeClock(1_000)
    private val sessionAttestation = SessionAttestation(clock)
    private val journal = FakeJournalRepository(clock)
    private val settings = FakeSettingsRepository()
    private val preferences = FakeNetworkPreferenceRepository().apply {
        seed(NetworkPreferenceKey.forWifi("Maison"), "Maison", TunnelState.DISABLED)
    }
    private val engine = RuleEngine(setOf(MobileNetworkRule(), NetworkPreferenceRule()))
    private val evaluateRules = EvaluateRulesUseCase(preferences, settings, engine)

    private fun TestScope.viewModel() = HomeViewModel(
        networkObserver = observer,
        journalRepository = journal,
        controller = controller,
        synchronizeTunnel = SynchronizeTunnelUseCase(
            networkObserver = observer,
            settingsRepository = settings,
            evaluateRules = evaluateRules,
            controller = controller,
            journalRepository = journal,
            sessionAttestation = sessionAttestation,
        ),
        detectManualOverride = DetectManualOverrideUseCase(
            networkObserver = observer,
            evaluateRules = evaluateRules,
            clock = clock,
            sessionAttestation = sessionAttestation,
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
    fun aManualActivationOnATrustedNetworkIsCalledOut() = runTest {
        // La règle a coupé le tunnel sur ce réseau — le journal l'atteste —
        // puis l'utilisateur l'a rallumé depuis le client officiel. L'accueil
        // doit nommer ce geste au lieu d'attribuer l'état à une règle.
        journal.record(TunnelState.ENABLED, TunnelState.DISABLED, RuleId("network-preference"))
        // Le geste survient après le délai de grâce : la coupure journalisée a
        // eu tout le temps de s'exécuter, la divergence ne peut pas être elle.
        clock.advanceBy(60_000)
        controller.enable()
        observer.emit(
            NetworkContext(NetworkTransport.WIFI, isInternetValidated = true, ssid = "Maison"),
        )

        val override = viewModel().uiState.value.manualOverride

        assertEquals(TunnelState.ENABLED, override?.observedState)
        assertEquals(RuleId("network-preference"), override?.ruleId)
    }

    @Test
    fun aPendingCycleIsNotPresentedAsAManualGesture() = runTest {
        // Retour sur le réseau mobile : la décision vient de passer à
        // « activer » mais le cycle n'a pas encore couru. Cette divergence
        // transitoire ne doit pas s'afficher comme un geste de l'utilisateur.
        journal.record(TunnelState.ENABLED, TunnelState.DISABLED, RuleId("network-preference"))
        observer.emit(cellular)

        assertNull(viewModel().uiState.value.manualOverride)
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

    @Test
    fun theOverrideCardKnowsWhetherTheGestureWillBeMemorized() = runTest {
        // Réseau identifiable et apprentissage actif : la carte peut annoncer
        // la mémorisation. Apprentissage coupé, elle doit revenir au texte
        // « respecté jusqu'au prochain changement de réseau ».
        observer.emit(cellular)
        val model = viewModel()
        assertTrue(model.uiState.value.willMemorizeManualGesture)

        settings.updateAppSettings { it.copy(isLearningEnabled = false) }

        assertTrue(!model.uiState.value.willMemorizeManualGesture)
    }
}
