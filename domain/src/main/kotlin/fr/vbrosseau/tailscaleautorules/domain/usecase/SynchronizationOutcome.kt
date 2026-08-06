package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId

/**
 * Issue d'un cycle de synchronisation.
 *
 * Chaque cas est distinct parce qu'il se raconte différemment à l'utilisateur :
 * « rien à faire » et « impossible d'agir » ne sont pas la même information,
 * même si le tunnel n'a bougé dans aucun des deux.
 */
sealed interface SynchronizationOutcome {
    /** L'automatisation est désactivée dans les paramètres. */
    data object ServiceDisabled : SynchronizationOutcome

    /** Aucune règle ne s'est prononcée : l'état est laissé tel quel. */
    data object NoDecision : SynchronizationOutcome

    /** Aucun client Tailscale pilotable n'est installé. */
    data object TailscaleUnavailable : SynchronizationOutcome

    /** Une règle a décidé, mais le tunnel est déjà dans l'état visé. */
    data class AlreadyInTargetState(
        val ruleId: RuleId,
        val state: TunnelState,
    ) : SynchronizationOutcome

    /** La commande a été transmise au client et consignée au journal. */
    data class Applied(
        val ruleId: RuleId,
        val previousState: TunnelState,
        val newState: TunnelState,
    ) : SynchronizationOutcome

    /** La commande a échoué : rien n'a été consigné. */
    data class Failed(
        val ruleId: RuleId,
        val cause: Throwable,
    ) : SynchronizationOutcome
}
