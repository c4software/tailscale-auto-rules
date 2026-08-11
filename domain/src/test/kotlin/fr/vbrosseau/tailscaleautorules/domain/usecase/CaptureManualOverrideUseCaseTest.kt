package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkExceptionKey
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.FakeNetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeBlacklistRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeJournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeNetworkExceptionRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeSettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.BlacklistedWifiRule
import fr.vbrosseau.tailscaleautorules.domain.rule.MobileNetworkRule
import fr.vbrosseau.tailscaleautorules.domain.rule.NetworkExceptionRule
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.tailscale.FakeTailscaleController
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Éprouve le cycle complet du geste avec les vraies règles : détection sur
 * l'état courant, mémorisation, rejeu par la règle des exceptions, et
 * remplacement par un nouveau geste — la boucle que la documentation promet.
 */
class CaptureManualOverrideUseCaseTest {
    private val observer = FakeNetworkObserver()

    // Les entrées de journal posées à l'instant initial doivent être plus
    // vieilles que le délai de grâce pour que la détection s'exprime.
    private val clock = FakeClock(0)
    private val controller = FakeTailscaleController()
    private val journal = FakeJournalRepository(clock)
    private val settings = FakeSettingsRepository()
    private val exceptions = FakeNetworkExceptionRepository(clock)

    private val evaluateRules =
        EvaluateRulesUseCase(
            blacklistRepository = FakeBlacklistRepository(initial = listOf("Maison")),
            networkExceptionRepository = exceptions,
            settingsRepository = settings,
            engine =
                RuleEngine(
                    setOf(NetworkExceptionRule(), BlacklistedWifiRule(), MobileNetworkRule()),
                ),
        )

    private val capture =
        CaptureManualOverrideUseCase(
            networkObserver = observer,
            controller = controller,
            journalRepository = journal,
            detectManualOverride = DetectManualOverrideUseCase(observer, evaluateRules, clock),
            recordManualOverride = RecordManualOverrideUseCase(settings, exceptions, journal),
        )

    private val trustedWifi =
        NetworkContext(
            transport = NetworkTransport.WIFI,
            isInternetValidated = true,
            ssid = "Maison",
        )

    /** La règle blacklist a coupé le tunnel, puis l'utilisateur l'a rallumé. */
    private suspend fun aTunnelReenabledByHandOnTrustedWifi() {
        journal.record(TunnelState.ENABLED, TunnelState.DISABLED, RuleId("blacklisted-wifi"))
        clock.advanceBy(60_000)
        controller.enable()
        observer.emit(trustedWifi)
    }

    @Test
    fun aManualGestureIsMemorizedWithItsJournalEntry() =
        runTest {
            aTunnelReenabledByHandOnTrustedWifi()

            assertTrue(capture())

            val exception = exceptions.observeAll().first().single()
            assertEquals(NetworkExceptionKey("wifi:maison"), exception.key)
            assertEquals(TunnelState.ENABLED, exception.desiredState)
            assertEquals(NetworkExceptionRule.Id, journal.observeRecent().first().first().ruleId)
        }

    @Test
    fun onceMemorizedTheStateExplainsItselfAndNothingMoreIsRecorded() =
        runTest {
            aTunnelReenabledByHandOnTrustedWifi()
            capture()
            // Au-delà de la grâce : ce qui est vérifié ici est bien l'accord
            // entre l'exception et l'état, pas le délai.
            clock.advanceBy(60_000)

            // L'exception décide désormais l'état constaté : plus de
            // divergence, donc plus rien à mémoriser — le battement de secours
            // ne combat pas le geste.
            assertFalse(capture())
            assertEquals(1, exceptions.observeAll().first().size)
        }

    @Test
    fun aNewGestureReplacesTheMemory() =
        runTest {
            // La boucle de rétroaction : c'est l'entrée de journal écrite à la
            // mémorisation qui rend ce second geste détectable.
            aTunnelReenabledByHandOnTrustedWifi()
            capture()

            clock.advanceBy(60_000)
            controller.disable()

            assertTrue(capture())
            val exception = exceptions.observeAll().first().single()
            assertEquals(TunnelState.DISABLED, exception.desiredState)
        }

    @Test
    fun aGestureOnAnUnvalidatedNetworkIsStillCaptured() =
        runTest {
            // Le geste survient pendant que le VPN monte : le réseau porteur
            // perd fugacement sa validation. La capture ne doit pas le rater.
            journal.record(TunnelState.ENABLED, TunnelState.DISABLED, RuleId("blacklisted-wifi"))
            clock.advanceBy(60_000)
            controller.enable()
            observer.emit(
                NetworkContext(
                    transport = NetworkTransport.WIFI,
                    isInternetValidated = false,
                    ssid = "Maison",
                ),
            )

            assertTrue(capture())
            assertEquals(
                NetworkExceptionKey("wifi:maison"),
                exceptions.observeAll().first().single().key,
            )
        }

    @Test
    fun anEchoWithinTheGraceWindowIsNotMemorized() =
        runTest {
            // La commande vient d'être journalisée : la divergence est la
            // latence du client, pas un geste.
            journal.record(TunnelState.ENABLED, TunnelState.DISABLED, RuleId("blacklisted-wifi"))
            controller.enable()
            observer.emit(trustedWifi)

            assertFalse(capture())
            assertTrue(exceptions.observeAll().first().isEmpty())
        }

    @Test
    fun anAbsentClientMemorizesNothing() =
        runTest {
            aTunnelReenabledByHandOnTrustedWifi()
            controller.available = false

            assertFalse(capture())
            assertTrue(exceptions.observeAll().first().isEmpty())
        }

    @Test
    fun aDetectedGestureIsNotMemorizedWhenLearningIsOff() =
        runTest {
            aTunnelReenabledByHandOnTrustedWifi()
            settings.updateAppSettings { it.copy(isLearningEnabled = false) }

            assertFalse(capture(), "Constaté ne suffit pas : il faut aussi mémorisé.")
            assertTrue(exceptions.observeAll().first().isEmpty())
        }
}
