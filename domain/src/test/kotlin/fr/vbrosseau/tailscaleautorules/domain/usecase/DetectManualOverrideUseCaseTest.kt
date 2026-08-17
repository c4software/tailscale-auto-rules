package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.FakeNetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeNetworkPreferenceRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeSettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.MobileNetworkRule
import fr.vbrosseau.tailscaleautorules.domain.rule.NetworkPreferenceRule
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetectManualOverrideUseCaseTest {
    private val observer = FakeNetworkObserver()

    // Les entrées de journal des tests datent de l'instant 0 : l'horloge
    // démarre bien au-delà du délai de grâce pour que ce dernier ne masque pas
    // ce que chaque test veut constater.
    private val clock = FakeClock(60_000)

    /** Le réseau de confiance d'hier : une préférence « toujours coupé ». */
    private val trustedHome =
        FakeNetworkPreferenceRepository().apply {
            seed(NetworkPreferenceKey.forWifi("Maison"), "Maison", TunnelState.DISABLED)
        }

    private val detect =
        DetectManualOverrideUseCase(
            networkObserver = observer,
            evaluateRules =
                EvaluateRulesUseCase(
                    networkPreferenceRepository = trustedHome,
                    settingsRepository = FakeSettingsRepository(),
                    engine = RuleEngine(setOf(NetworkPreferenceRule(), MobileNetworkRule())),
                ),
            clock = clock,
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
                    applied(TunnelState.DISABLED, "network-preference"),
                )

            assertEquals(
                ManualOverride(TunnelState.ENABLED, RuleId("network-preference")),
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
                detect(cellular, TunnelState.DISABLED, applied(TunnelState.DISABLED, "network-preference")),
            )
        }

    @Test
    fun anAlignedTunnelIsNoOverride() =
        runTest {
            assertNull(
                detect(trustedWifi, TunnelState.DISABLED, applied(TunnelState.DISABLED, "network-preference")),
            )
        }

    @Test
    fun anUnknownTunnelStateIsNeverAttributedToTheUser() =
        runTest {
            assertNull(
                detect(trustedWifi, TunnelState.UNKNOWN, applied(TunnelState.DISABLED, "network-preference")),
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
                ManualOverride(TunnelState.ENABLED, RuleId("network-preference")),
                detect(TunnelState.ENABLED, applied(TunnelState.DISABLED, "network-preference")),
            )
        }

    @Test
    fun aJustIssuedCommandIsNotMistakenForAManualGesture() =
        runTest {
            // La commande est journalisée à l'envoi, mais le client met
            // quelques secondes à l'exécuter : tant que ce délai court, la
            // divergence est la latence du tunnel, pas un geste.
            clock.setTo(2_000)

            assertNull(
                detect(
                    trustedWifi,
                    TunnelState.ENABLED,
                    applied(TunnelState.DISABLED, "network-preference"),
                ),
            )
        }

    @Test
    fun anEmptyJournalProvesNoDecisionWasEverApplied() =
        runTest {
            assertNull(detect(trustedWifi, TunnelState.ENABLED, lastChange = null))
        }

    @Test
    fun anAttestationFromBeforeTheBootProvesNothing() =
        runTest {
            // Un redémarrage remet le tunnel dans son état par défaut sans
            // qu'aucune main n'y touche : l'entrée de la session précédente ne
            // doit pas transformer ce simple fait en geste. Constaté sur
            // appareil : une préférence « toujours actif » redevenait
            // « toujours coupé » à chaque reboot.
            clock.bootMillis = 30_000

            assertNull(
                detect(
                    trustedWifi,
                    TunnelState.ENABLED,
                    applied(TunnelState.DISABLED, "network-preference"),
                ),
            )
        }

    @Test
    fun anAttestationFromTheCurrentSessionStillProves() =
        runTest {
            // Le premier cycle de la session réatteste la décision au journal :
            // la détection doit reprendre sur cette entrée-là.
            clock.bootMillis = 30_000
            val reattested =
                applied(TunnelState.DISABLED, "network-preference").copy(epochMillis = 40_000)

            assertEquals(
                ManualOverride(TunnelState.ENABLED, RuleId("network-preference")),
                detect(trustedWifi, TunnelState.ENABLED, reattested),
            )
        }
}
