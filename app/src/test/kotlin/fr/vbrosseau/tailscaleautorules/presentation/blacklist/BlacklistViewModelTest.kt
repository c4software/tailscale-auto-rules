package fr.vbrosseau.tailscaleautorules.presentation.blacklist

import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkExceptionKey
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.FakeNetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeBlacklistRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeJournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeNetworkExceptionRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeSettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.AirplaneModeRule
import fr.vbrosseau.tailscaleautorules.domain.rule.BlacklistedWifiRule
import fr.vbrosseau.tailscaleautorules.domain.rule.MobileNetworkRule
import fr.vbrosseau.tailscaleautorules.domain.rule.NetworkExceptionRule
import fr.vbrosseau.tailscaleautorules.domain.rule.OtherWifiRule
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleSettings
import fr.vbrosseau.tailscaleautorules.domain.tailscale.FakeTailscaleController
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import fr.vbrosseau.tailscaleautorules.domain.usecase.EvaluateRulesUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizeTunnelUseCase
import fr.vbrosseau.tailscaleautorules.presentation.FakeSystemStatus
import fr.vbrosseau.tailscaleautorules.presentation.MainDispatcherRule
import fr.vbrosseau.tailscaleautorules.presentation.keepCollecting
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlacklistViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeBlacklistRepository()
    private val observer = FakeNetworkObserver()
    private val systemStatus = FakeSystemStatus()
    private val settings = FakeSettingsRepository()
    private val controller = FakeTailscaleController()
    private val journal = FakeJournalRepository(FakeClock())
    private val exceptions = FakeNetworkExceptionRepository()
    private val engine = RuleEngine(
        setOf(
            NetworkExceptionRule(),
            AirplaneModeRule(),
            BlacklistedWifiRule(),
            OtherWifiRule(),
            MobileNetworkRule(),
        ),
    )

    private fun TestScope.viewModel(networkObserver: NetworkObserver = observer) = BlacklistViewModel(
        repository = repository,
        exceptionRepository = exceptions,
        settingsRepository = settings,
        synchronizeTunnel = SynchronizeTunnelUseCase(
            networkObserver = networkObserver,
            settingsRepository = settings,
            evaluateRules = EvaluateRulesUseCase(repository, exceptions, settings, engine),
            controller = controller,
            journalRepository = journal,
        ),
        systemStatus = systemStatus,
        networkObserver = networkObserver,
    ).also { keepCollecting(it.uiState) }

    private fun onWifi(ssid: String?) = observer.emit(
        NetworkContext(NetworkTransport.WIFI, isInternetValidated = true, ssid = ssid),
    )

    @Test
    fun theFirstEmissionEndsTheLoadingState() = runTest {
        // Le dispatcher unconfined a déjà livré la première liste : un état
        // encore « en chargement » signifierait que le drapeau ne retombe pas.
        assertTrue(!viewModel().uiState.value.isLoading)
    }

    @Test
    fun theListDoesNotWaitForTheNetwork() = runTest {
        // Hors ligne, le flux réseau peut ne jamais émettre — aucun rappel
        // système n'arrive. La liste, elle, vient de Room : elle s'affiche.
        repository.add("Maison")
        val model = viewModel(SilentNetworkObserver())

        val state = model.uiState.value
        assertTrue(!state.isLoading)
        assertEquals(listOf("Maison"), state.entries.map { it.value })
        assertNull(state.currentSsid)
        assertTrue(!state.canAddCurrentSsid)
    }

    /** Observateur qui n'émet jamais : le cas d'un terminal sans aucun réseau. */
    private class SilentNetworkObserver : NetworkObserver {
        override fun observe(): Flow<NetworkContext> = flow { awaitCancellation() }

        override suspend fun current(): NetworkContext = NetworkContext.Disconnected
    }

    @Test
    fun theMobileRuleIsEnabledByDefault() = runTest {
        assertTrue(viewModel().uiState.value.isMobileRuleEnabled)
    }

    @Test
    fun disablingTheMobileRuleIsPersisted() = runTest {
        val model = viewModel()

        model.setMobileRuleEnabled(false)

        assertTrue(!model.uiState.value.isMobileRuleEnabled)
        assertEquals(
            false,
            settings.currentRuleSettings()[RuleId("mobile-network")]?.isEnabled,
        )
    }

    @Test
    fun enablingTheMobileRuleOnCellularStartsTheTunnelImmediately() = runTest {
        // Attendre le prochain changement de réseau serait trop tard : le
        // terminal est déjà en données mobiles au moment de la bascule.
        settings.setRuleSettings(
            RuleId("mobile-network"),
            RuleSettings(isEnabled = false, priority = 400),
        )
        observer.emit(NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true))
        val model = viewModel()

        model.setMobileRuleEnabled(true)

        assertTrue(controller.isRunning())
    }

    @Test
    fun disablingTheMobileRuleLeavesTheTunnelUntouched() = runTest {
        // Aucune règle ne se prononce plus : l'état est conservé (SPECS.md §3.2).
        observer.emit(NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true))
        val model = viewModel()
        model.setMobileRuleEnabled(true)
        assertTrue(controller.isRunning())

        model.setMobileRuleEnabled(false)

        assertTrue(controller.isRunning())
    }

    @Test
    fun anOverriddenPrioritySurvivesTheToggle() = runTest {
        settings.setRuleSettings(
            RuleId("mobile-network"),
            RuleSettings(isEnabled = true, priority = 42),
        )
        val model = viewModel()

        model.setMobileRuleEnabled(false)

        assertEquals(42, settings.currentRuleSettings()[RuleId("mobile-network")]?.priority)
    }

    @Test
    fun theListFollowsTheRepository() = runTest {
        val model = viewModel()

        model.add("Maison")

        assertEquals(listOf("Maison"), model.uiState.value.entries.map { it.value })
    }

    @Test
    fun aDuplicateIsTranslatedIntoADisplayableError() = runTest {
        val model = viewModel()
        model.add("Maison")

        model.add("  maison ")

        assertEquals(BlacklistError.DUPLICATE, model.uiState.value.error)
        assertEquals(1, model.uiState.value.entries.size)
    }

    @Test
    fun anErrorCanBeDismissed() = runTest {
        val model = viewModel()
        model.add("Maison")
        model.add("Maison")

        model.dismissError()

        assertNull(model.uiState.value.error)
    }

    @Test
    fun aSuccessfulActionClearsThePreviousError() = runTest {
        val model = viewModel()
        model.add("Maison")
        model.add("Maison")

        model.add("Bureau")

        assertNull(model.uiState.value.error)
    }

    @Test
    fun theCurrentSsidCanBeAddedInOneGesture() = runTest {
        onWifi("Aéroport")
        val model = viewModel()

        assertTrue(model.uiState.value.canAddCurrentSsid)
        model.addCurrentSsid()

        assertEquals(listOf("Aéroport"), model.uiState.value.entries.map { it.value })
    }

    @Test
    fun anUnavailableSsidDisablesTheQuickAdd() = runTest {
        onWifi(null)

        assertTrue(!viewModel().uiState.value.canAddCurrentSsid)
    }

    @Test
    fun anAlreadyListedSsidDisablesTheQuickAdd() = runTest {
        // Proposer un ajout voué à échouer serait une invitation à l'erreur.
        onWifi("Maison")
        val model = viewModel()
        model.add("maison")

        assertTrue(model.uiState.value.isCurrentSsidAlreadyListed)
        assertTrue(!model.uiState.value.canAddCurrentSsid)
    }

    @Test
    fun theQuickAddIsANoOpWhenNoSsidIsAvailable() = runTest {
        val model = viewModel()

        model.addCurrentSsid()

        assertTrue(model.uiState.value.entries.isEmpty())
        assertNull(model.uiState.value.error)
    }

    @Test
    fun renamingUpdatesTheEntry() = runTest {
        val model = viewModel()
        model.add("Maison")
        val id = model.uiState.value.entries.single().id

        model.rename(id, "Maison Fibre")

        assertEquals("Maison Fibre", model.uiState.value.entries.single().value)
    }

    @Test
    fun renamingOntoAnExistingEntryReportsADuplicate() = runTest {
        val model = viewModel()
        model.add("Maison")
        model.add("Bureau")
        val bureauId = model.uiState.value.entries.first { it.value == "Bureau" }.id

        model.rename(bureauId, "maison")

        assertEquals(BlacklistError.DUPLICATE, model.uiState.value.error)
    }

    @Test
    fun aMissingLocationPermissionIsSurfaced() = runTest {
        systemStatus.ssidReadable = false

        assertTrue(viewModel().uiState.value.needsLocationPermission)
    }

    @Test
    fun nothingIsAskedOnceTheSsidCanBeRead() = runTest {
        assertTrue(!viewModel().uiState.value.needsLocationPermission)
    }

    @Test
    fun aPermissionGrantedOutsideTheApplicationIsPickedUpOnRefresh() = runTest {
        // Elle s'accorde dans les réglages système : sans reconstat au retour,
        // l'explication resterait affichée à tort.
        systemStatus.ssidReadable = false
        val model = viewModel()
        assertTrue(model.uiState.value.needsLocationPermission)

        systemStatus.ssidReadable = true
        model.refreshSystemStatus()

        assertTrue(!model.uiState.value.needsLocationPermission)
    }

    @Test
    fun removingAnEntryUpdatesTheList() = runTest {
        val model = viewModel()
        model.add("Maison")
        val id = model.uiState.value.entries.single().id

        model.remove(id)

        assertTrue(model.uiState.value.entries.isEmpty())
    }

    @Test
    fun learnedExceptionsAreExposedMostRecentFirst() = runTest {
        exceptions.upsert(NetworkExceptionKey("wifi:maison"), "Maison", TunnelState.ENABLED)

        val model = viewModel()

        assertEquals(listOf("Maison"), model.uiState.value.exceptions.map { it.ssid })
    }

    @Test
    fun removingAnExceptionRestoresTheAutomaticBehaviourImmediately() = runTest {
        // Le geste avait coupé le tunnel en données mobiles ; supprimer
        // l'exception doit laisser la règle mobile le remonter dans la foulée,
        // pas au prochain changement de réseau (SPECS.md §6.2).
        exceptions.upsert(NetworkExceptionKey.Cellular, null, TunnelState.DISABLED)
        observer.emit(NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true))
        val model = viewModel()
        val id = model.uiState.value.exceptions.single().id

        model.removeException(id)

        assertTrue(model.uiState.value.exceptions.isEmpty())
        assertTrue(controller.isRunning())
    }
}
