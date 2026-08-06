package fr.vbrosseau.tailscaleautorules.domain.rule

import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision

/**
 * Mode avion actif → tunnel désactivé.
 *
 * Priorité maximale : aucune autre considération ne prime sur elle.
 *
 * Elle ne se prononce **que** lorsque le mode avion est actif. Renvoyer
 * `ENABLE` dans le cas contraire lui ferait décider à la place de toutes les
 * autres règles, qui ne seraient alors jamais évaluées.
 */
class AirplaneModeRule : Rule {
    override val id = RuleId("airplane-mode")

    override val defaultSettings =
        RuleSettings(
            isEnabled = true,
            priority = Priorities.AIRPLANE_MODE,
        )

    override fun evaluate(context: RuleContext): RuleDecision =
        if (context.network.isAirplaneModeOn) RuleDecision.DISABLE else RuleDecision.NO_DECISION
}
