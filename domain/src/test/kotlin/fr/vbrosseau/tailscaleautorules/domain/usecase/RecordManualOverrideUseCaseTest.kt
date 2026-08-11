package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkExceptionKey
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeJournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeNetworkExceptionRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeSettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.NetworkExceptionRule
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordManualOverrideUseCaseTest {
    private val settings = FakeSettingsRepository()
    private val exceptions = FakeNetworkExceptionRepository()
    private val journal = FakeJournalRepository()
    private val useCase = RecordManualOverrideUseCase(settings, exceptions, journal)

    private val trustedWifi =
        NetworkContext(
            transport = NetworkTransport.WIFI,
            isInternetValidated = true,
            ssid = "Maison",
        )
    private val cellular =
        NetworkContext(
            transport = NetworkTransport.CELLULAR,
            isInternetValidated = true,
        )

    @Test
    fun aGestureOnAWifiIsMemorizedAndJournaled() =
        runTest {
            val recorded =
                useCase(
                    trustedWifi,
                    ManualOverride(TunnelState.ENABLED, RuleId("blacklisted-wifi")),
                )

            assertTrue(recorded)
            val exception = exceptions.observeAll().first().single()
            assertEquals(NetworkExceptionKey("wifi:maison"), exception.key)
            assertEquals("Maison", exception.ssid)
            assertEquals(TunnelState.ENABLED, exception.desiredState)

            // Sans cette entrée, le journal porterait encore l'ancienne cible
            // et le prochain geste sur ce réseau serait invisible.
            val entry = journal.observeRecent().first().single()
            assertEquals(NetworkExceptionRule.Id, entry.ruleId)
            assertEquals(TunnelState.DISABLED, entry.previousState)
            assertEquals(TunnelState.ENABLED, entry.newState)
        }

    @Test
    fun aGestureOnCellularIsMemorizedGlobally() =
        runTest {
            val recorded =
                useCase(
                    cellular,
                    ManualOverride(TunnelState.DISABLED, RuleId("mobile-network")),
                )

            assertTrue(recorded)
            val exception = exceptions.observeAll().first().single()
            assertEquals(NetworkExceptionKey.Cellular, exception.key)
            assertEquals(null, exception.ssid)
            assertEquals(TunnelState.DISABLED, exception.desiredState)
        }

    @Test
    fun aGestureAgainstAnExceptionReplacesIt() =
        runTest {
            // SPECS.md §3.3 : la mémoire d'un réseau est son dernier geste,
            // sans cas particulier selon la règle contredite.
            useCase(trustedWifi, ManualOverride(TunnelState.ENABLED, RuleId("blacklisted-wifi")))

            val recorded =
                useCase(
                    trustedWifi,
                    ManualOverride(TunnelState.DISABLED, NetworkExceptionRule.Id),
                )

            assertTrue(recorded)
            val exception = exceptions.observeAll().first().single()
            assertEquals(TunnelState.DISABLED, exception.desiredState)
            assertEquals(2, journal.observeRecent().first().size)
        }

    @Test
    fun nothingIsMemorizedWhenLearningIsDisabled() =
        runTest {
            settings.updateAppSettings { it.copy(isLearningEnabled = false) }

            val recorded =
                useCase(trustedWifi, ManualOverride(TunnelState.ENABLED, RuleId("blacklisted-wifi")))

            assertFalse(recorded)
            assertTrue(exceptions.observeAll().first().isEmpty())
            assertTrue(journal.observeRecent().first().isEmpty())
        }

    @Test
    fun nothingIsMemorizedWhenTheServiceIsDisabled() =
        runTest {
            settings.updateAppSettings { it.copy(isServiceEnabled = false) }

            val recorded =
                useCase(trustedWifi, ManualOverride(TunnelState.ENABLED, RuleId("blacklisted-wifi")))

            assertFalse(recorded)
            assertTrue(exceptions.observeAll().first().isEmpty())
        }

    @Test
    fun nothingIsMemorizedInAirplaneMode() =
        runTest {
            val wifiInAirplaneMode =
                NetworkContext(
                    transport = NetworkTransport.WIFI,
                    isAirplaneModeOn = true,
                    isInternetValidated = true,
                    ssid = "Maison",
                )

            val recorded =
                useCase(
                    wifiInAirplaneMode,
                    ManualOverride(TunnelState.ENABLED, RuleId("airplane-mode")),
                )

            assertFalse(recorded)
            assertTrue(exceptions.observeAll().first().isEmpty())
        }

    @Test
    fun nothingIsMemorizedWithoutANetworkKey() =
        runTest {
            val unreadableSsid =
                NetworkContext(
                    transport = NetworkTransport.WIFI,
                    isInternetValidated = true,
                    ssid = null,
                )
            val ethernet =
                NetworkContext(
                    transport = NetworkTransport.ETHERNET,
                    isInternetValidated = true,
                )

            assertFalse(useCase(unreadableSsid, ManualOverride(TunnelState.ENABLED, RuleId("other-wifi"))))
            assertFalse(useCase(ethernet, ManualOverride(TunnelState.ENABLED, RuleId("mobile-network"))))
            assertTrue(exceptions.observeAll().first().isEmpty())
        }

    @Test
    fun anUnvalidatedNetworkIsStillMemorized() =
        runTest {
            // Le geste survient typiquement pendant que le VPN monte, moment
            // où le réseau porteur perd fugacement sa validation : refuser la
            // mémorisation à cet instant la faisait rater à chaque fois.
            val unvalidatedWifi =
                NetworkContext(
                    transport = NetworkTransport.WIFI,
                    isInternetValidated = false,
                    ssid = "Maison",
                )

            assertTrue(useCase(unvalidatedWifi, ManualOverride(TunnelState.ENABLED, RuleId("blacklisted-wifi"))))
            assertEquals(
                NetworkExceptionKey("wifi:maison"),
                exceptions.observeAll().first().single().key,
            )
        }

    @Test
    fun anUnknownObservedStateIsAProgrammingError() =
        runTest {
            // La détection ne produit jamais UNKNOWN ; y parvenir malgré tout
            // doit éclater immédiatement, avant toute écriture.
            assertFailsWith<IllegalStateException> {
                useCase(trustedWifi, ManualOverride(TunnelState.UNKNOWN, RuleId("other-wifi")))
            }
            assertTrue(exceptions.observeAll().first().isEmpty())
            assertTrue(journal.observeRecent().first().isEmpty())
        }

    @Test
    fun learningIsEnabledByDefault() {
        assertTrue(AppSettings.Defaults.isLearningEnabled)
    }
}
