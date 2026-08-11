package fr.vbrosseau.tailscaleautorules.presentation.networks

import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.FakeNetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeJournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeNetworkPreferenceRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeSettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.AirplaneModeRule
import fr.vbrosseau.tailscaleautorules.domain.rule.MobileNetworkRule
import fr.vbrosseau.tailscaleautorules.domain.rule.NetworkPreferenceRule
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

class NetworksViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observer = FakeNetworkObserver()
    private val systemStatus = FakeSystemStatus()
    private val settings = FakeSettingsRepository()
    private val controller = FakeTailscaleController()
    private val journal = FakeJournalRepository(FakeClock())
    private val preferences = FakeNetworkPreferenceRepository()
    private val engine = RuleEngine(
        setOf(
            NetworkPreferenceRule(),
            AirplaneModeRule(),
            OtherWifiRule(),
            MobileNetworkRule(),
        ),
    )

    private fun TestScope.viewModel(networkObserver: NetworkObserver = observer) = NetworksViewModel(
        preferenceRepository = preferences,
        settingsRepository = settings,
        synchronizeTunnel = SynchronizeTunnelUseCase(
            networkObserver = networkObserver,
            settingsRepository = settings,
            evaluateRules = EvaluateRulesUseCase(preferences, settings, engine),
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
        assertTrue(!viewModel().uiState.value.isLoading)
    }

    @Test
    fun theListDoesNotWaitForTheNetwork() = runTest {
        // Hors ligne, le flux réseau peut ne jamais émettre — aucun rappel
        // système n'arrive. La liste, elle, vient de Room : elle s'affiche.
        preferences.seed(NetworkPreferenceKey.forWifi("Maison"), "Maison", TunnelState.DISABLED)
        val model = viewModel(SilentNetworkObserver())

        val state = model.uiState.value
        assertTrue(!state.isLoading)
        assertEquals(listOf("Maison"), state.preferences.map { it.ssid })
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
    fun addingATrustedNetworkCutsTheTunnelImmediately() = runTest {
        // Le geste de confiance d'hier : ajouter « coupé » en étant connecté
        // au réseau doit produire son effet sur-le-champ (SPECS.md §5).
        controller.enable()
        onWifi("Maison")
        val model = viewModel()

        model.add("Maison", tunnelEnabled = false)

        assertEquals(TunnelState.DISABLED, model.uiState.value.preferences.single().desiredState)
        assertTrue(!controller.isRunning())
    }

    @Test
    fun addingAnAlwaysOnNetworkIsPossibleToo() = runTest {
        onWifi("Maison")
        val model = viewModel()

        model.add("Maison", tunnelEnabled = true)

        assertEquals(TunnelState.ENABLED, model.uiState.value.preferences.single().desiredState)
    }

    @Test
    fun addingAKnownNetworkReplacesItsWill() = runTest {
        // Comme un geste : la dernière volonté gagne, sans erreur de doublon.
        val model = viewModel()
        model.add("Maison", tunnelEnabled = false)

        model.add("  maison ", tunnelEnabled = true)

        val preference = model.uiState.value.preferences.single()
        assertEquals(TunnelState.ENABLED, preference.desiredState)
        assertNull(model.uiState.value.error)
    }

    @Test
    fun aBlankSsidIsRefused() = runTest {
        val model = viewModel()

        model.add("   ", tunnelEnabled = false)

        assertEquals(NetworksError.BLANK, model.uiState.value.error)
        assertTrue(model.uiState.value.preferences.isEmpty())
    }

    @Test
    fun togglingAPreferenceKeepsItsIdentityAndSynchronizes() = runTest {
        onWifi("Maison")
        val model = viewModel()
        model.add("Maison", tunnelEnabled = false)
        val before = model.uiState.value.preferences.single()
        assertTrue(!controller.isRunning())

        model.setPreferenceEnabled(before, tunnelEnabled = true)

        val after = model.uiState.value.preferences.single()
        assertEquals(before.id, after.id)
        assertEquals(TunnelState.ENABLED, after.desiredState)
        assertTrue(controller.isRunning(), "La bascule déclenche un cycle immédiat.")
    }

    @Test
    fun renamingUpdatesTheNetworkIdentity() = runTest {
        val model = viewModel()
        model.add("Maison", tunnelEnabled = false)
        val id = model.uiState.value.preferences.single().id

        model.rename(id, "Maison Fibre")

        val preference = model.uiState.value.preferences.single()
        assertEquals("Maison Fibre", preference.ssid)
        assertEquals(NetworkPreferenceKey.forWifi("Maison Fibre"), preference.key)
    }

    @Test
    fun renamingOntoAnExistingNetworkReportsADuplicate() = runTest {
        val model = viewModel()
        model.add("Maison", tunnelEnabled = false)
        model.add("Bureau", tunnelEnabled = false)
        val bureauId = model.uiState.value.preferences.first { it.ssid == "Bureau" }.id

        model.rename(bureauId, "maison")

        assertEquals(NetworksError.DUPLICATE, model.uiState.value.error)
        assertTrue(model.uiState.value.preferences.any { it.ssid == "Bureau" })
    }

    @Test
    fun aSuccessfulActionClearsThePreviousError() = runTest {
        val model = viewModel()
        model.add("  ", tunnelEnabled = false)
        assertEquals(NetworksError.BLANK, model.uiState.value.error)

        model.add("Maison", tunnelEnabled = false)

        assertNull(model.uiState.value.error)
    }

    @Test
    fun anErrorCanBeDismissed() = runTest {
        val model = viewModel()
        model.add("  ", tunnelEnabled = false)

        model.dismissError()

        assertNull(model.uiState.value.error)
    }

    @Test
    fun removingAPreferenceRestoresTheAutomatismImmediately() = runTest {
        // Le réseau redevient automatique : sur un Wi-Fi inconnu, le tunnel
        // remonte dans la foulée, pas au prochain changement de réseau.
        onWifi("Maison")
        val model = viewModel()
        model.add("Maison", tunnelEnabled = false)
        val id = model.uiState.value.preferences.single().id
        assertTrue(!controller.isRunning())

        model.remove(id)

        assertTrue(model.uiState.value.preferences.isEmpty())
        assertTrue(controller.isRunning())
    }

    @Test
    fun theCurrentSsidCanBeAddedInOneGestureAsTrusted() = runTest {
        onWifi("Aéroport")
        val model = viewModel()

        model.addCurrentSsid()

        val preference = model.uiState.value.preferences.single()
        assertEquals("Aéroport", preference.ssid)
        assertEquals(TunnelState.DISABLED, preference.desiredState)
    }

    @Test
    fun anUnavailableSsidDisablesTheQuickAdd() = runTest {
        onWifi(null)

        assertTrue(!viewModel().uiState.value.canAddCurrentSsid)
    }

    @Test
    fun anAlreadyListedSsidDisablesTheQuickAdd() = runTest {
        preferences.seed(NetworkPreferenceKey.forWifi("Maison"), "Maison", TunnelState.DISABLED)
        onWifi("  MAISON ")

        assertTrue(!viewModel().uiState.value.canAddCurrentSsid)
    }

    @Test
    fun theQuickAddIsANoOpWhenNoSsidIsAvailable() = runTest {
        onWifi(null)
        val model = viewModel()

        model.addCurrentSsid()

        assertTrue(model.uiState.value.preferences.isEmpty())
    }

    @Test
    fun aMissingLocationPermissionIsSurfaced() = runTest {
        systemStatus.ssidReadable = false

        assertTrue(viewModel().uiState.value.needsLocationPermission)
    }

    @Test
    fun aPermissionGrantedOutsideTheApplicationIsPickedUpOnRefresh() = runTest {
        systemStatus.ssidReadable = false
        val model = viewModel()
        assertTrue(model.uiState.value.needsLocationPermission)

        systemStatus.ssidReadable = true
        model.refreshSystemStatus()

        assertTrue(!model.uiState.value.needsLocationPermission)
    }
}
