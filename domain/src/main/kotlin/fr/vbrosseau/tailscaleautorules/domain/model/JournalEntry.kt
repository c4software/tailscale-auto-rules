package fr.vbrosseau.tailscaleautorules.domain.model

import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId

/**
 * Trace d'un changement d'état effectif du tunnel.
 *
 * Une entrée n'existe que si l'état a **réellement** changé : une décision qui
 * confirme l'état courant ne produit rien. Le journal répond donc à « que
 * s'est-il passé », pas à « qu'a-t-on envisagé ».
 *
 * La raison lisible attendue par SPECS.md §6.4 n'est pas stockée : elle se
 * déduit de [ruleId] au moment de l'affichage. La stocker figerait la langue et
 * la formulation dans la base, et les rendrait incohérentes après traduction.
 */
data class JournalEntry(
    val id: Long,
    val epochMillis: Long,
    val previousState: TunnelState,
    val newState: TunnelState,
    val ruleId: RuleId,
) {
    init {
        require(previousState != newState) {
            "Une entrée de journal atteste d'un changement : $previousState → $newState n'en est pas un."
        }
    }
}
