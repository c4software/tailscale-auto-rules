package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.FakeNetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeJournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeNetworkPreferenceRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeSettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.AirplaneModeRule
import fr.vbrosseau.tailscaleautorules.domain.rule.MobileNetworkRule
import fr.vbrosseau.tailscaleautorules.domain.rule.NetworkPreferenceRule
import fr.vbrosseau.tailscaleautorules.domain.rule.OtherWifiRule
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleSettings
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import fr.vbrosseau.tailscaleautorules.domain.tailscale.FakeTailscaleController
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Éprouve l'orchestration complète avec les vraies règles et des Fakes pour
 * tout le reste : c'est le seul niveau où l'enchaînement décision → application
 * → journalisation est vérifiable de bout en bout.
 */
class SynchronizeTunnelUseCaseTest {
    private val clock = FakeClock(1_000)
    private val controller = FakeTailscaleController()
    private val journal = FakeJournalRepository(clock)
    private val preferences = FakeNetworkPreferenceRepository(clock)
    private val settings = FakeSettingsRepository()
    private val observer = FakeNetworkObserver()

    private val useCase =
        SynchronizeTunnelUseCase(
            networkObserver = observer,
            settingsRepository = settings,
            evaluateRules =
                EvaluateRulesUseCase(
                    networkPreferenceRepository = preferences,
                    settingsRepository = settings,
                    engine =
                        RuleEngine(
                            setOf(AirplaneModeRule(), NetworkPreferenceRule(), OtherWifiRule(), MobileNetworkRule()),
                        ),
                ),
            controller = controller,
            journalRepository = journal,
        )

    private val cellular = NetworkContext(NetworkTransport.CELLULAR, isInternetValidated = true)

    private fun wifi(ssid: String?) =
        NetworkContext(
            transport = NetworkTransport.WIFI,
            isInternetValidated = true,
            ssid = ssid,
        )

    @Test
    fun aDecisionIsAppliedAndRecorded() =
        runTest {
            val outcome = useCase(cellular)

            assertEquals(
                SynchronizationOutcome.Applied(
                    ruleId = RuleId("mobile-network"),
                    previousState = TunnelState.DISABLED,
                    newState = TunnelState.ENABLED,
                ),
                outcome,
            )
            assertTrue(controller.isRunning())

            val entry = journal.observeRecent().first().single()
            assertEquals(RuleId("mobile-network"), entry.ruleId)
            assertEquals(1_000, entry.epochMillis)
        }

    @Test
    fun nothingHappensWhenTheTunnelIsAlreadyInTheTargetState() =
        runTest {
            useCase(cellular)
            journal.clear()

            val outcome = useCase(cellular)

            assertEquals(
                SynchronizationOutcome.AlreadyInTargetState(
                    RuleId("mobile-network"),
                    TunnelState.ENABLED,
                ),
                outcome,
            )
            // Ni commande superflue, ni entrée de journal : c'est ce qui évite de
            // saturer l'historique sur un réseau stable.
            assertEquals(1, controller.enableCount)
            assertTrue(journal.observeRecent().first().isEmpty())
        }

    @Test
    fun anAbstentionLeavesEverythingUntouched() =
        runTest {
            controller.enable()
            journal.clear()

            val outcome = useCase(NetworkContext.Disconnected)

            assertEquals(SynchronizationOutcome.NoDecision, outcome)
            assertTrue(controller.isRunning(), "Aucune règle ne s'étant prononcée, rien ne bouge.")
            assertTrue(journal.observeRecent().first().isEmpty())
        }

    @Test
    fun aDisabledServiceShortCircuitsTheWholeCycle() =
        runTest {
            settings.updateAppSettings { it.copy(isServiceEnabled = false) }

            val outcome = useCase(cellular)

            assertEquals(SynchronizationOutcome.ServiceDisabled, outcome)
            assertEquals(0, controller.enableCount, "Le contrôleur n'est même pas sollicité.")
        }

    @Test
    fun anAbsentClientIsReportedWithoutTouchingTheJournal() =
        runTest {
            controller.available = false

            val outcome = useCase(cellular)

            assertEquals(SynchronizationOutcome.TailscaleUnavailable, outcome)
            assertTrue(journal.observeRecent().first().isEmpty())
        }

    @Test
    fun aFailedCommandIsNotRecorded() =
        runTest {
            // Le journal atteste de ce qui a eu lieu : une commande refusée n'a
            // rien changé, elle n'a donc rien à y faire.
            val cause = IllegalStateException("diffusion refusée")
            controller.nextFailure = cause

            val outcome = useCase(cellular)

            val failure = assertIs<SynchronizationOutcome.Failed>(outcome)
            assertEquals(cause, failure.cause)
            assertEquals(RuleId("mobile-network"), failure.ruleId)
            assertTrue(journal.observeRecent().first().isEmpty())
            assertTrue(!controller.isRunning())
        }

    @Test
    fun thePreferencesAreReadAtEachCycle() =
        runTest {
            // Une préférence déclarée doit produire son effet à la
            // synchronisation suivante, sans redémarrage ni invalidation.
            assertIs<SynchronizationOutcome.Applied>(useCase(wifi("Maison")))
            assertTrue(controller.isRunning())

            preferences.upsert(NetworkPreferenceKey.forWifi("Maison"), "Maison", TunnelState.DISABLED)

            val outcome = useCase(wifi("Maison"))
            assertEquals(
                SynchronizationOutcome.Applied(
                    ruleId = RuleId("network-preference"),
                    previousState = TunnelState.ENABLED,
                    newState = TunnelState.DISABLED,
                ),
                outcome,
            )
        }

    @Test
    fun userRuleSettingsAreReadAtEachCycle() =
        runTest {
            preferences.upsert(NetworkPreferenceKey.forWifi("Maison"), "Maison", TunnelState.DISABLED)
            settings.setRuleSettings(
                RuleId("network-preference"),
                RuleSettings(isEnabled = false, priority = 150),
            )

            // La règle des préférences étant désactivée, « Wi-Fi » reprend la main.
            val outcome = assertIs<SynchronizationOutcome.Applied>(useCase(wifi("Maison")))
            assertEquals(RuleId("other-wifi"), outcome.ruleId)
        }

    @Test
    fun airplaneModeWinsOverEveryOtherSituation() =
        runTest {
            controller.enable()

            val outcome =
                assertIs<SynchronizationOutcome.Applied>(
                    useCase(cellular.copy(isAirplaneModeOn = true)),
                )

            assertEquals(RuleId("airplane-mode"), outcome.ruleId)
            assertTrue(!controller.isRunning())
        }

    @Test
    fun theArgumentLessFormReadsTheCurrentContext() =
        runTest {
            observer.emit(cellular)

            val outcome = assertIs<SynchronizationOutcome.Applied>(useCase())

            assertEquals(RuleId("mobile-network"), outcome.ruleId)
            assertEquals(1, observer.currentCount)
        }

    @Test
    fun aProvidedContextIsUsedWithoutReReadingTheNetwork() =
        runTest {
            // L'observation continue fournit un contexte déjà stabilisé : le relire
            // perdrait le bénéfice du debounce.
            observer.emit(NetworkContext.Disconnected)

            assertIs<SynchronizationOutcome.Applied>(useCase(cellular))
            assertEquals(0, observer.currentCount)
        }

    @Test
    fun successiveChangesAccumulateInTheJournalMostRecentFirst() =
        runTest {
            useCase(cellular)
            clock.advanceBy(60_000)
            useCase(cellular.copy(isAirplaneModeOn = true))

            val entries = journal.observeRecent().first()
            assertEquals(2, entries.size)
            assertEquals(RuleId("airplane-mode"), entries.first().ruleId)
            assertEquals(RuleId("mobile-network"), entries.last().ruleId)
        }

    @Test
    fun defaultSettingsLeaveTheServiceEnabled() =
        runTest {
            // Une installation neuve automatise sans réglage préalable.
            assertTrue(AppSettings.Defaults.isServiceEnabled)
            assertIs<SynchronizationOutcome.Applied>(useCase(cellular))
        }
}
