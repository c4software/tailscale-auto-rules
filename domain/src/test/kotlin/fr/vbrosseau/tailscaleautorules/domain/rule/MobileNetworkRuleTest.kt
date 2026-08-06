package fr.vbrosseau.tailscaleautorules.domain.rule

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision
import kotlin.test.Test
import kotlin.test.assertEquals

class MobileNetworkRuleTest {
    private val rule = MobileNetworkRule()

    @Test
    fun aUsableCellularNetworkEnablesTheTunnel() {
        assertEquals(RuleDecision.ENABLE, rule.evaluate(Contexts.cellular()))
    }

    @Test
    fun anUnvalidatedCellularNetworkAbstains() {
        assertEquals(
            RuleDecision.NO_DECISION,
            rule.evaluate(Contexts.cellular(validated = false)),
        )
    }

    @Test
    fun aNonCellularTransportAbstains() {
        NetworkTransport.entries
            .filter { it != NetworkTransport.CELLULAR }
            .forEach { transport ->
                val context =
                    when (transport) {
                        NetworkTransport.NONE -> Contexts.none()
                        NetworkTransport.WIFI -> Contexts.wifi()
                        else -> Contexts.other(transport)
                    }
                assertEquals(RuleDecision.NO_DECISION, rule.evaluate(context), "Transport $transport")
            }
    }

    @Test
    fun itIsEnabledByDefaultAndRunsLast() {
        assertEquals(true, rule.defaultSettings.isEnabled)
        assertEquals(Priorities.MOBILE_NETWORK, rule.defaultSettings.priority)
    }
}
