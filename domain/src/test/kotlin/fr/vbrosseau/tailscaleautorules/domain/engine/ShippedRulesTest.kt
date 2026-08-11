package fr.vbrosseau.tailscaleautorules.domain.engine

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.rule.AirplaneModeRule
import fr.vbrosseau.tailscaleautorules.domain.rule.Contexts
import fr.vbrosseau.tailscaleautorules.domain.rule.MobileNetworkRule
import fr.vbrosseau.tailscaleautorules.domain.rule.NetworkPreferenceRule
import fr.vbrosseau.tailscaleautorules.domain.rule.OtherWifiRule
import fr.vbrosseau.tailscaleautorules.domain.rule.Rule
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Rejoue le tableau de SPECS.md §4 avec l'ensemble réel de règles.
 *
 * Les tests unitaires de chaque règle vérifient son comportement isolé ; ce
 * test-ci vérifie qu'assemblées, elles produisent bien la table de décision
 * annoncée à l'utilisateur.
 */
class ShippedRulesTest {
    private val rules: Set<Rule> =
        setOf(
            AirplaneModeRule(),
            NetworkPreferenceRule(),
            OtherWifiRule(),
            MobileNetworkRule(),
        )
    private val engine = RuleEngine(rules)

    @Test
    fun airplaneModeDisablesTheTunnelWhateverTheRest() {
        val evaluation =
            engine.evaluate(
                Contexts.wifi(ssid = "Aéroport", airplaneMode = true),
            )

        assertEquals(RuleDecision.DISABLE, evaluation.decision)
        assertEquals(RuleId("airplane-mode"), evaluation.ruleId)
    }

    @Test
    fun aTrustedWifiDisablesTheTunnel() {
        // Le réseau de confiance d'hier : une préférence « toujours coupé ».
        val evaluation =
            engine.evaluate(
                Contexts.wifi(
                    ssid = "Maison",
                    preferences = mapOf(NetworkPreferenceKey("wifi:maison") to TunnelState.DISABLED),
                ),
            )

        assertEquals(RuleDecision.DISABLE, evaluation.decision)
        assertEquals(RuleId("network-preference"), evaluation.ruleId)
    }

    @Test
    fun anUnknownWifiEnablesTheTunnel() {
        val evaluation =
            engine.evaluate(
                Contexts.wifi(
                    ssid = "Aéroport",
                    preferences = mapOf(NetworkPreferenceKey("wifi:maison") to TunnelState.DISABLED),
                ),
            )

        assertEquals(RuleDecision.ENABLE, evaluation.decision)
        assertEquals(RuleId("other-wifi"), evaluation.ruleId)
    }

    @Test
    fun anUnreadableSsidFallsBackToProtectingTheConnection() {
        // Enchaînement complet de SPECS.md §4.2 : sans clé, la règle des
        // préférences s'abstient, et c'est bien « Wi-Fi » qui tranche.
        val evaluation =
            engine.evaluate(
                Contexts.wifi(
                    ssid = null,
                    preferences = mapOf(NetworkPreferenceKey("wifi:maison") to TunnelState.DISABLED),
                ),
            )

        assertEquals(RuleDecision.ENABLE, evaluation.decision)
        assertEquals(RuleId("other-wifi"), evaluation.ruleId)
    }

    @Test
    fun aCellularNetworkEnablesTheTunnel() {
        val evaluation = engine.evaluate(Contexts.cellular())

        assertEquals(RuleDecision.ENABLE, evaluation.decision)
        assertEquals(RuleId("mobile-network"), evaluation.ruleId)
    }

    @Test
    fun aMemorizedGestureOverridesTheMobileRule() {
        val evaluation =
            engine.evaluate(
                Contexts.cellular(
                    preferences = mapOf(NetworkPreferenceKey.Cellular to TunnelState.DISABLED),
                ),
            )

        assertEquals(RuleDecision.DISABLE, evaluation.decision)
        assertEquals(RuleId("network-preference"), evaluation.ruleId)
    }

    @Test
    fun airplaneModeStillOverridesAMemorizedGesture() {
        // Jamais de rejeu en mode avion : la priorité 100 reste au-dessus.
        val evaluation =
            engine.evaluate(
                Contexts.wifi(
                    ssid = "Maison",
                    airplaneMode = true,
                    preferences = mapOf(NetworkPreferenceKey("wifi:maison") to TunnelState.ENABLED),
                ),
            )

        assertEquals(RuleDecision.DISABLE, evaluation.decision)
        assertEquals(RuleId("airplane-mode"), evaluation.ruleId)
    }

    @Test
    fun noNetworkChangesNothing() {
        assertEquals(RuleEvaluation.NoDecision, engine.evaluate(Contexts.none()))
    }

    @Test
    fun anUnvalidatedNetworkChangesNothing() {
        assertEquals(
            RuleEvaluation.NoDecision,
            engine.evaluate(Contexts.wifi(ssid = "Aéroport", validated = false)),
        )
        assertEquals(
            RuleEvaluation.NoDecision,
            engine.evaluate(Contexts.cellular(validated = false)),
        )
    }

    @Test
    fun anUncoveredTransportChangesNothing() {
        // Ethernet et les transports exotiques ne sont pas traités en version 1 :
        // le tunnel doit rester tel quel, et non basculer par défaut.
        assertEquals(
            RuleEvaluation.NoDecision,
            engine.evaluate(Contexts.other(NetworkTransport.ETHERNET)),
        )
        assertEquals(
            RuleEvaluation.NoDecision,
            engine.evaluate(Contexts.other(NetworkTransport.OTHER)),
        )
    }

    @Test
    fun everyShippedRuleHasAUniqueIdentifierAndPriority() {
        // Deux règles partageant un identifiant écraseraient mutuellement leur
        // configuration et leurs entrées de journal.
        assertEquals(rules.size, rules.map { it.id }.toSet().size)
        assertEquals(rules.size, rules.map { it.defaultSettings.priority }.toSet().size)
    }
}
