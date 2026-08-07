package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.FakeNetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeBlacklistRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeSettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.BlacklistedWifiRule
import fr.vbrosseau.tailscaleautorules.domain.rule.MobileNetworkRule
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetectManualOverrideUseCaseTest {
    private val observer = FakeNetworkObserver()

    private val detect =
        DetectManualOverrideUseCase(
            networkObserver = observer,
            blacklistRepository = FakeBlacklistRepository(initial = listOf("Maison")),
            settingsRepository = FakeSettingsRepository(),
            engine = RuleEngine(setOf(BlacklistedWifiRule(), MobileNetworkRule())),
        )

    private val trustedWifi =
        NetworkContext(
            transport = NetworkTransport.WIFI,
            isInternetValidated = true,
            ssid = "Maison",
        )

    private val cellular = NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true)

    private fun applied(
        state: TunnelState,
        ruleId: String,
    ) = JournalEntry(
        id = 1,
        epochMillis = 0,
        previousState = if (state == TunnelState.ENABLED) TunnelState.DISABLED else TunnelState.ENABLED,
        newState = state,
        ruleId = RuleId(ruleId),
    )

    @Test
    fun aTunnelEnabledByHandOnATrustedNetworkIsRecognized() =
        runTest {
            // La règle a coupé le tunnel — le journal l'atteste — et il est
            // pourtant actif : seul l'utilisateur a pu le rallumer.
            val override =
                detect(
                    trustedWifi,
                    TunnelState.ENABLED,
                    applied(TunnelState.DISABLED, "blacklisted-wifi"),
                )

            assertEquals(
                ManualOverride(TunnelState.ENABLED, RuleId("blacklisted-wifi")),
                override,
            )
        }

    @Test
    fun aTunnelDisabledByHandOnMobileIsRecognizedToo() =
        runTest {
            val override =
                detect(
                    cellular,
                    TunnelState.DISABLED,
                    applied(TunnelState.ENABLED, "mobile-network"),
                )

            assertEquals(
                ManualOverride(TunnelState.DISABLED, RuleId("mobile-network")),
                override,
            )
        }

    @Test
    fun aPendingCycleIsNotMistakenForAManualGesture() =
        runTest {
            // Quitter un réseau de confiance : la décision vient de passer à
            // « activer » mais le journal montre encore la coupure précédente. La
            // divergence est un cycle en attente, pas un geste de l'utilisateur.
            assertNull(
                detect(cellular, TunnelState.DISABLED, applied(TunnelState.DISABLED, "blacklisted-wifi")),
            )
        }

    @Test
    fun anAlignedTunnelIsNoOverride() =
        runTest {
            assertNull(
                detect(trustedWifi, TunnelState.DISABLED, applied(TunnelState.DISABLED, "blacklisted-wifi")),
            )
        }

    @Test
    fun anUnknownTunnelStateIsNeverAttributedToTheUser() =
        runTest {
            assertNull(
                detect(trustedWifi, TunnelState.UNKNOWN, applied(TunnelState.DISABLED, "blacklisted-wifi")),
            )
        }

    @Test
    fun anAbstentionLeavesNothingToContradict() =
        runTest {
            // Aucun réseau : aucune règle ne se prononce, aucun avis à contredire.
            val offline = NetworkContext(NetworkTransport.NONE, isInternetValidated = false)

            assertNull(detect(offline, TunnelState.ENABLED, applied(TunnelState.ENABLED, "mobile-network")))
        }

    @Test
    fun theContextFreeFormReadsTheCurrentNetworkItself() =
        runTest {
            // La forme sans contexte sert la notification, qui n'en a pas sous
            // la main : elle doit constater la même chose que la forme pleine.
            observer.emit(trustedWifi)

            assertEquals(
                ManualOverride(TunnelState.ENABLED, RuleId("blacklisted-wifi")),
                detect(TunnelState.ENABLED, applied(TunnelState.DISABLED, "blacklisted-wifi")),
            )
        }

    @Test
    fun anEmptyJournalProvesNoDecisionWasEverApplied() =
        runTest {
            assertNull(detect(trustedWifi, TunnelState.ENABLED, lastChange = null))
        }
}
