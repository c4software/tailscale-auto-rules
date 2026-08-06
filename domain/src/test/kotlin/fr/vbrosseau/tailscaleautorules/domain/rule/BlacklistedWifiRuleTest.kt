package fr.vbrosseau.tailscaleautorules.domain.rule

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision
import kotlin.test.Test
import kotlin.test.assertEquals

class BlacklistedWifiRuleTest {
    private val rule = BlacklistedWifiRule()

    @Test
    fun aBlacklistedSsidDisablesTheTunnel() {
        val context = Contexts.wifi(ssid = "Maison", blacklist = setOf("Maison"))

        assertEquals(RuleDecision.DISABLE, rule.evaluate(context))
    }

    @Test
    fun theComparisonIgnoresCaseAndSurroundingSpaces() {
        // SPECS.md §4.2 : un « Maison » enregistré doit reconnaître un
        // « maison » diffusé, et inversement.
        val broadcastInUpperCase = Contexts.wifi(ssid = "  MAISON ", blacklist = setOf("maison"))
        assertEquals(RuleDecision.DISABLE, rule.evaluate(broadcastInUpperCase))

        val storedWithSpaces = Contexts.wifi(ssid = "maison", blacklist = setOf(" Maison  "))
        assertEquals(RuleDecision.DISABLE, rule.evaluate(storedWithSpaces))
    }

    @Test
    fun anSsidAbsentFromTheListAbstains() {
        val context = Contexts.wifi(ssid = "Aéroport", blacklist = setOf("Maison", "Bureau"))

        assertEquals(RuleDecision.NO_DECISION, rule.evaluate(context))
    }

    @Test
    fun anEmptyListNeverMatches() {
        assertEquals(RuleDecision.NO_DECISION, rule.evaluate(Contexts.wifi(ssid = "Maison")))
    }

    @Test
    fun anUnavailableSsidIsTreatedAsNotBlacklisted() {
        // Dans le doute, on protège la connexion : la règle s'abstient et
        // laisse OtherWifiRule activer le tunnel.
        val context = Contexts.wifi(ssid = null, blacklist = setOf("Maison"))

        assertEquals(RuleDecision.NO_DECISION, rule.evaluate(context))
    }

    @Test
    fun aNonWifiTransportNeverConcernsThisRule() {
        NetworkTransport.entries
            .filter { it != NetworkTransport.WIFI }
            .forEach { transport ->
                val context =
                    when (transport) {
                        NetworkTransport.NONE -> Contexts.none()
                        NetworkTransport.CELLULAR -> Contexts.cellular()
                        else -> Contexts.other(transport)
                    }
                assertEquals(
                    RuleDecision.NO_DECISION,
                    rule.evaluate(context),
                    "Le transport $transport n'a pas de SSID à comparer.",
                )
            }
    }

    @Test
    fun anUnvalidatedBlacklistedWifiStillDisables() {
        // La confiance accordée au réseau ne dépend pas de son accès Internet.
        val context = Contexts.wifi(ssid = "Maison", validated = false, blacklist = setOf("Maison"))

        assertEquals(RuleDecision.DISABLE, rule.evaluate(context))
    }

    @Test
    fun itIsEnabledByDefaultAndRunsAfterAirplaneMode() {
        assertEquals(true, rule.defaultSettings.isEnabled)
        assertEquals(Priorities.BLACKLISTED_WIFI, rule.defaultSettings.priority)
    }
}
