package fr.vbrosseau.tailscaleautorules.domain.rule

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkExceptionKey
import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkExceptionRuleTest {
    private val rule = NetworkExceptionRule()

    @Test
    fun aMemorizedEnableIsReplayedOnItsWifi() {
        val context =
            Contexts.wifi(
                ssid = "Maison",
                exceptions = mapOf(NetworkExceptionKey("wifi:maison") to TunnelState.ENABLED),
            )

        assertEquals(RuleDecision.ENABLE, rule.evaluate(context))
    }

    @Test
    fun aMemorizedDisableIsReplayedOnItsWifi() {
        val context =
            Contexts.wifi(
                ssid = "Aéroport",
                exceptions = mapOf(NetworkExceptionKey("wifi:aéroport") to TunnelState.DISABLED),
            )

        assertEquals(RuleDecision.DISABLE, rule.evaluate(context))
    }

    @Test
    fun theKeyComparisonIgnoresCaseAndSurroundingSpaces() {
        // Même canonicalisation que la blacklist (SPECS.md §4.5) : un geste
        // fait sur « Maison » doit rejouer sur «  MAISON  ».
        val context =
            Contexts.wifi(
                ssid = "  MAISON ",
                exceptions = mapOf(NetworkExceptionKey("wifi:maison") to TunnelState.ENABLED),
            )

        assertEquals(RuleDecision.ENABLE, rule.evaluate(context))
    }

    @Test
    fun aCellularExceptionCoversAllMobileData() {
        val context =
            Contexts.cellular(
                exceptions = mapOf(NetworkExceptionKey.Cellular to TunnelState.DISABLED),
            )

        assertEquals(RuleDecision.DISABLE, rule.evaluate(context))
    }

    @Test
    fun anotherNetworkExceptionDoesNotLeak() {
        val context =
            Contexts.wifi(
                ssid = "Bureau",
                exceptions = mapOf(NetworkExceptionKey("wifi:maison") to TunnelState.ENABLED),
            )

        assertEquals(RuleDecision.NO_DECISION, rule.evaluate(context))
    }

    @Test
    fun withoutAnyExceptionTheRuleAbstains() {
        assertEquals(RuleDecision.NO_DECISION, rule.evaluate(Contexts.wifi(ssid = "Maison")))
        assertEquals(RuleDecision.NO_DECISION, rule.evaluate(Contexts.cellular()))
    }

    @Test
    fun anUnreadableSsidAbstains() {
        // Sans identité stable, rejouer risquerait de viser un autre réseau
        // que celui du geste d'origine.
        val context =
            Contexts.wifi(
                ssid = null,
                exceptions = mapOf(NetworkExceptionKey("wifi:maison") to TunnelState.ENABLED),
            )

        assertEquals(RuleDecision.NO_DECISION, rule.evaluate(context))
    }

    @Test
    fun anUnvalidatedNetworkAbstains() {
        val context =
            Contexts.wifi(
                ssid = "Maison",
                validated = false,
                exceptions = mapOf(NetworkExceptionKey("wifi:maison") to TunnelState.ENABLED),
            )

        assertEquals(RuleDecision.NO_DECISION, rule.evaluate(context))
    }

    @Test
    fun noNetworkAbstains() {
        assertEquals(RuleDecision.NO_DECISION, rule.evaluate(Contexts.none()))
    }

    @Test
    fun anUnknownMemorizedStateAbstains() {
        // Le modèle interdit de persister UNKNOWN ; si une carte artisanale en
        // porte un malgré tout, la règle doit s'abstenir plutôt que décider.
        val context =
            Contexts.cellular(
                exceptions = mapOf(NetworkExceptionKey.Cellular to TunnelState.UNKNOWN),
            )

        assertEquals(RuleDecision.NO_DECISION, rule.evaluate(context))
    }

    @Test
    fun itIsEnabledByDefaultAndSitsBetweenAirplaneModeAndBlacklist() {
        assertEquals(true, rule.defaultSettings.isEnabled)
        assertEquals(Priorities.NETWORK_EXCEPTION, rule.defaultSettings.priority)
        assertEquals(NetworkExceptionRule.Id, rule.id)
        check(Priorities.AIRPLANE_MODE < Priorities.NETWORK_EXCEPTION) {
            "Le mode avion doit primer sur les exceptions."
        }
        check(Priorities.NETWORK_EXCEPTION < Priorities.BLACKLISTED_WIFI) {
            "Une exception doit primer sur la blacklist."
        }
    }
}
