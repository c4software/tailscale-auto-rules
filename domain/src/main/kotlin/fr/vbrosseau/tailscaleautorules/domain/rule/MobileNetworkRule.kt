package fr.vbrosseau.tailscaleautorules.domain.rule

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision

/**
 * Réseau mobile → tunnel activé.
 *
 * Aucune distinction de génération : la 4G et la 5G posent le même problème de
 * confidentialité sur un réseau que l'on ne maîtrise pas. Distinguer NR de LTE
 * serait un paramètre d'évolution, pas une condition d'entrée (SPECS.md §4.3).
 */
class MobileNetworkRule : Rule {
    override val id = RuleId("mobile-network")

    override val defaultSettings =
        RuleSettings(
            isEnabled = true,
            priority = Priorities.MOBILE_NETWORK,
        )

    override fun evaluate(context: RuleContext): RuleDecision =
        if (context.network.transport == NetworkTransport.CELLULAR && context.network.isUsable) {
            RuleDecision.ENABLE
        } else {
            RuleDecision.NO_DECISION
        }
}
