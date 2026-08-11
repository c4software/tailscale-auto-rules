package fr.vbrosseau.tailscaleautorules.domain.rule

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import kotlin.test.Test
import kotlin.test.assertEquals

class OtherWifiRuleTest {
    private val rule = OtherWifiRule()

    @Test
    fun aUsableWifiEnablesTheTunnel() {
        assertEquals(RuleDecision.ENABLE, rule.evaluate(Contexts.wifi(ssid = "Aéroport")))
    }

    @Test
    fun anUnavailableSsidStillEnablesTheTunnel() {
        // Un SSID illisible ne doit pas priver l'utilisateur de protection.
        assertEquals(RuleDecision.ENABLE, rule.evaluate(Contexts.wifi(ssid = null)))
    }

    @Test
    fun theRuleIgnoresTheNetworkPreferencesEntirely() {
        // NetworkPreferenceRule, plus prioritaire, a déjà tranché ce cas.
        // Vérifier à nouveau ici dupliquerait une connaissance qui pourrait
        // diverger.
        val context =
            Contexts.wifi(
                ssid = "Maison",
                preferences = mapOf(NetworkPreferenceKey("wifi:maison") to TunnelState.DISABLED),
            )

        assertEquals(RuleDecision.ENABLE, rule.evaluate(context))
    }

    @Test
    fun anUnvalidatedWifiAbstains() {
        // Activer un tunnel sur un réseau inexploitable n'a aucun effet utile.
        assertEquals(
            RuleDecision.NO_DECISION,
            rule.evaluate(Contexts.wifi(ssid = "Aéroport", validated = false)),
        )
    }

    @Test
    fun aNonWifiTransportAbstains() {
        NetworkTransport.entries
            .filter { it != NetworkTransport.WIFI }
            .forEach { transport ->
                val context =
                    when (transport) {
                        NetworkTransport.NONE -> Contexts.none()
                        NetworkTransport.CELLULAR -> Contexts.cellular()
                        else -> Contexts.other(transport)
                    }
                assertEquals(RuleDecision.NO_DECISION, rule.evaluate(context), "Transport $transport")
            }
    }

    @Test
    fun itIsEnabledByDefaultAndRunsAfterTheBlacklist() {
        assertEquals(true, rule.defaultSettings.isEnabled)
        assertEquals(Priorities.OTHER_WIFI, rule.defaultSettings.priority)
    }
}
