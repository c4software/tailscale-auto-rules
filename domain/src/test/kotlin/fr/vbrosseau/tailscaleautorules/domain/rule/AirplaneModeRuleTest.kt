package fr.vbrosseau.tailscaleautorules.domain.rule

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision
import kotlin.test.Test
import kotlin.test.assertEquals

class AirplaneModeRuleTest {
    private val rule = AirplaneModeRule()

    @Test
    fun anActiveAirplaneModeDisablesTheTunnel() {
        assertEquals(RuleDecision.DISABLE, rule.evaluate(Contexts.none(airplaneMode = true)))
    }

    @Test
    fun anActiveAirplaneModeDecidesWhateverTheTransport() {
        // Le Wi-Fi peut rester actif en mode avion : la règle doit trancher
        // quand même, sinon une autre règle activerait le tunnel.
        assertEquals(
            RuleDecision.DISABLE,
            rule.evaluate(Contexts.wifi(ssid = "Maison", airplaneMode = true)),
        )
        assertEquals(
            RuleDecision.DISABLE,
            rule.evaluate(Contexts.cellular(airplaneMode = true)),
        )
    }

    @Test
    fun anInactiveAirplaneModeAbstainsRatherThanEnabling() {
        // Renvoyer ENABLE ici ferait décider cette règle à la place de toutes
        // les autres, qui ne seraient jamais évaluées.
        NetworkTransport.entries.forEach { transport ->
            val context =
                when (transport) {
                    NetworkTransport.NONE -> Contexts.none()
                    NetworkTransport.WIFI -> Contexts.wifi()
                    NetworkTransport.CELLULAR -> Contexts.cellular()
                    else -> Contexts.other(transport)
                }
            assertEquals(
                RuleDecision.NO_DECISION,
                rule.evaluate(context),
                "Le transport $transport ne doit rien déclencher hors mode avion.",
            )
        }
    }

    @Test
    fun itIsEnabledAndMostPriorityByDefault() {
        assertEquals(true, rule.defaultSettings.isEnabled)
        assertEquals(Priorities.AIRPLANE_MODE, rule.defaultSettings.priority)
    }
}
