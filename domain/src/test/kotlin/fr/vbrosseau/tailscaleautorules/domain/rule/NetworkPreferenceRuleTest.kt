package fr.vbrosseau.tailscaleautorules.domain.rule

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkPreferenceRuleTest {
    private val rule = NetworkPreferenceRule()

    @Test
    fun aMemorizedEnableIsReplayedOnItsWifi() {
        val context =
            Contexts.wifi(
                ssid = "Maison",
                preferences = mapOf(NetworkPreferenceKey("wifi:maison") to TunnelState.ENABLED),
            )

        assertEquals(RuleDecision.ENABLE, rule.evaluate(context))
    }

    @Test
    fun aMemorizedDisableIsReplayedOnItsWifi() {
        val context =
            Contexts.wifi(
                ssid = "Aéroport",
                preferences = mapOf(NetworkPreferenceKey("wifi:aéroport") to TunnelState.DISABLED),
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
                preferences = mapOf(NetworkPreferenceKey("wifi:maison") to TunnelState.ENABLED),
            )

        assertEquals(RuleDecision.ENABLE, rule.evaluate(context))
    }

    @Test
    fun aCellularExceptionCoversAllMobileData() {
        val context =
            Contexts.cellular(
                preferences = mapOf(NetworkPreferenceKey.Cellular to TunnelState.DISABLED),
            )

        assertEquals(RuleDecision.DISABLE, rule.evaluate(context))
    }

    @Test
    fun anotherNetworkPreferenceDoesNotLeak() {
        val context =
            Contexts.wifi(
                ssid = "Bureau",
                preferences = mapOf(NetworkPreferenceKey("wifi:maison") to TunnelState.ENABLED),
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
                preferences = mapOf(NetworkPreferenceKey("wifi:maison") to TunnelState.ENABLED),
            )

        assertEquals(RuleDecision.NO_DECISION, rule.evaluate(context))
    }

    @Test
    fun anUnvalidatedNetworkStillReplaysItsException() {
        // Même logique que la blacklist (SPECS.md §4.2) : le choix de
        // l'utilisateur ne dépend pas de l'accès Internet — et le VPN qui
        // monte fait fugacement perdre sa validation au réseau porteur.
        val context =
            Contexts.wifi(
                ssid = "Maison",
                validated = false,
                preferences = mapOf(NetworkPreferenceKey("wifi:maison") to TunnelState.ENABLED),
            )

        assertEquals(RuleDecision.ENABLE, rule.evaluate(context))
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
                preferences = mapOf(NetworkPreferenceKey.Cellular to TunnelState.UNKNOWN),
            )

        assertEquals(RuleDecision.NO_DECISION, rule.evaluate(context))
    }

    @Test
    fun itIsEnabledByDefaultAndSitsBetweenAirplaneModeAndBlacklist() {
        assertEquals(true, rule.defaultSettings.isEnabled)
        assertEquals(Priorities.NETWORK_EXCEPTION, rule.defaultSettings.priority)
        assertEquals(NetworkPreferenceRule.Id, rule.id)
        check(Priorities.AIRPLANE_MODE < Priorities.NETWORK_EXCEPTION) {
            "Le mode avion doit primer sur les exceptions."
        }
        check(Priorities.NETWORK_EXCEPTION < Priorities.BLACKLISTED_WIFI) {
            "Une exception doit primer sur la blacklist."
        }
    }
}
