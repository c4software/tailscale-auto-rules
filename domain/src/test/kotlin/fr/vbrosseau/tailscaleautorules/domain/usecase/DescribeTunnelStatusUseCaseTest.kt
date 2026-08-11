package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.FakeNetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeBlacklistRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeJournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeNetworkExceptionRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeSettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.BlacklistedWifiRule
import fr.vbrosseau.tailscaleautorules.domain.rule.MobileNetworkRule
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.tailscale.FakeTailscaleController
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve ce que la notification a le droit d'affirmer.
 *
 * L'enjeu tient en une phrase : la raison affichée doit décrire le réseau
 * **courant**, alors que le journal ne décrit que le dernier *changement*
 * d'état — deux choses qui divergent dès qu'une règle confirme un état déjà
 * atteint.
 */
class DescribeTunnelStatusUseCaseTest {
    private val clock = FakeClock()
    private val controller = FakeTailscaleController()
    private val observer = FakeNetworkObserver()
    private val journal = FakeJournalRepository(clock)

    private val evaluateRules =
        EvaluateRulesUseCase(
            blacklistRepository = FakeBlacklistRepository(initial = listOf("Maison")),
            networkExceptionRepository = FakeNetworkExceptionRepository(),
            settingsRepository = FakeSettingsRepository(),
            engine = RuleEngine(setOf(BlacklistedWifiRule(), MobileNetworkRule())),
        )

    private val describe =
        DescribeTunnelStatusUseCase(
            networkObserver = observer,
            evaluateRules = evaluateRules,
            detectManualOverride =
                DetectManualOverrideUseCase(
                    networkObserver = observer,
                    evaluateRules = evaluateRules,
                    clock = clock,
                ),
            journalRepository = journal,
            controller = controller,
        )

    private val cellular = NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true)
    private val trustedWifi =
        NetworkContext(NetworkTransport.WIFI, isInternetValidated = true, ssid = "Maison")

    @Test
    fun theReasonDescribesTheCurrentNetworkNotTheLastLoggedChange() =
        runTest {
            // Le tunnel a été coupé sur un Wi-Fi de confiance — seule trace au
            // journal — puis rallumé, et l'utilisateur est passé en données
            // mobiles. La règle du réseau mobile confirme un tunnel déjà actif,
            // donc n'écrit rien : lire la raison au journal afficherait
            // indéfiniment « Wi-Fi de confiance » sous un tunnel activé.
            journal.record(TunnelState.ENABLED, TunnelState.DISABLED, RuleId("blacklisted-wifi"))
            clock.advanceBy(60_000)
            controller.enable()
            observer.emit(cellular)

            val status = describe()

            assertEquals(TunnelState.ENABLED, status.state)
            assertEquals(RuleId("mobile-network"), status.ruleId)
            assertFalse(status.isManuallyOverridden)
        }

    @Test
    fun noRuleMeansNoReason() =
        runTest {
            // Sur un réseau qu'aucune règle ne reconnaît, avouer l'ignorance vaut
            // mieux que ressortir la dernière règle appliquée.
            journal.record(TunnelState.DISABLED, TunnelState.ENABLED, RuleId("mobile-network"))
            clock.advanceBy(60_000)
            controller.enable()
            observer.emit(NetworkContext.Disconnected)

            val status = describe()

            assertNull(status.ruleId)
            assertFalse(status.isManuallyOverridden)
        }

    @Test
    fun aTunnelHeldAgainstTheRuleIsReportedAsManual() =
        runTest {
            // La règle du Wi-Fi de confiance a coupé le tunnel — le journal
            // l'atteste — et le tunnel est pourtant actif : seule une main
            // extérieure a pu l'y remettre.
            journal.record(TunnelState.ENABLED, TunnelState.DISABLED, RuleId("blacklisted-wifi"))
            // Au-delà du délai de grâce : une entrée trop fraîche n'atteste de
            // rien, le tunnel n'ayant pas encore eu le temps de suivre la commande.
            clock.advanceBy(60_000)
            controller.enable()
            observer.emit(trustedWifi)

            val status = describe()

            assertEquals(RuleId("blacklisted-wifi"), status.ruleId)
            assertTrue(status.isManuallyOverridden)
        }

    @Test
    fun anUninstalledClientLeavesTheStateUnknown() =
        runTest {
            controller.available = false
            observer.emit(cellular)

            val status = describe()

            assertEquals(TunnelState.UNKNOWN, status.state)
            // La règle applicable reste dicible : elle ne dépend que du réseau.
            assertEquals(RuleId("mobile-network"), status.ruleId)
            assertFalse(status.isManuallyOverridden)
        }
}
