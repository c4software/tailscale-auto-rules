package fr.vbrosseau.tailscaleautorules.domain.engine

import fr.vbrosseau.tailscaleautorules.domain.model.RuleDecision
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId

/**
 * Verdict du moteur : une décision, et la règle qui l'a rendue.
 *
 * L'identifiant permet au journal et à l'interface de dire **pourquoi** le
 * tunnel a bougé. Le domaine ne porte volontairement aucun libellé : la
 * traduction d'un [RuleId] en texte lisible appartient à la présentation, qui
 * seule connaît la langue de l'utilisateur.
 */
data class RuleEvaluation(
    val decision: RuleDecision,
    val ruleId: RuleId?,
) {
    init {
        require(decision.isFirm == (ruleId != null)) {
            "Une décision ferme désigne toujours sa règle, et une abstention n'en désigne jamais."
        }
    }

    companion object {
        /** Aucune règle ne s'est prononcée : l'état du tunnel reste inchangé. */
        val NoDecision = RuleEvaluation(RuleDecision.NO_DECISION, null)
    }
}
