package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.BlacklistRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleContext
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId

/**
 * Le tunnel a été mis dans cet état à la main, contre l'avis d'une règle.
 *
 * @param observedState état constaté du tunnel, celui que l'utilisateur a choisi.
 * @param ruleId règle dont la décision est contredite — celle qui reprendra la
 *   main au prochain cycle.
 */
data class ManualOverride(
    val observedState: TunnelState,
    val ruleId: RuleId,
)

/**
 * Reconnaît une intervention manuelle sur le tunnel.
 *
 * Une divergence entre l'état constaté et la décision des règles ne suffit
 * pas : elle est **normale** pendant les quelques secondes qui séparent un
 * changement de réseau de l'application de la décision. La signature d'un
 * geste manuel est plus précise : la décision courante **a déjà été
 * appliquée** — le journal en atteste — et le tunnel est pourtant dans l'état
 * opposé. Seule une main extérieure a pu l'y mettre.
 *
 * Le constat est purement dérivé : rien n'est mémorisé, rien n'est commandé.
 * L'automatisation ne combat pas ce choix — aucun cycle ne se déclenche tant
 * que le réseau ne change pas — et le prochain changement de réseau le
 * périme naturellement.
 */
class DetectManualOverrideUseCase(
    private val networkObserver: NetworkObserver,
    private val blacklistRepository: BlacklistRepository,
    private val settingsRepository: SettingsRepository,
    private val engine: RuleEngine,
) {
    /**
     * Constat sur le contexte réseau courant — la forme qu'emploie la
     * notification, qui n'a pas de contexte sous la main.
     */
    suspend operator fun invoke(
        tunnelState: TunnelState,
        lastChange: JournalEntry?,
    ): ManualOverride? = invoke(networkObserver.current(), tunnelState, lastChange)

    /**
     * @param networkContext contexte sur lequel les règles sont évaluées.
     * @param tunnelState état constaté du tunnel.
     * @param lastChange dernier changement consigné au journal, ou `null`.
     * @return la divergence attribuable à l'utilisateur, ou `null` si l'état
     *   du tunnel s'explique sans lui.
     */
    suspend operator fun invoke(
        networkContext: NetworkContext,
        tunnelState: TunnelState,
        lastChange: JournalEntry?,
    ): ManualOverride? {
        // Sans journal, aucune décision n'a jamais été appliquée : rien ne
        // permet d'attribuer l'état constaté à qui que ce soit.
        if (tunnelState == TunnelState.UNKNOWN || lastChange == null) return null

        val evaluation =
            engine.evaluate(
                RuleContext(
                    network = networkContext,
                    blacklistedSsids = blacklistRepository.currentSsids(),
                    settings = settingsRepository.currentRuleSettings(),
                ),
            )

        val ruleId = evaluation.ruleId
        val targetState = evaluation.decision.asTunnelState()

        // Tant que le journal ne confirme pas que la décision courante a été
        // appliquée, la divergence est un cycle en attente, pas un geste de
        // l'utilisateur. Une abstention (`targetState` nul) échoue d'elle-même
        // à la comparaison avec le journal : rien à contredire.
        return if (ruleId != null && targetState != tunnelState && lastChange.newState == targetState) {
            ManualOverride(observedState = tunnelState, ruleId = ruleId)
        } else {
            null
        }
    }
}
