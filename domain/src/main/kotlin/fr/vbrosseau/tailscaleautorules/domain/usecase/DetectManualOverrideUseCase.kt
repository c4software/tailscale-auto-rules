package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEvaluation
import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
 * Le journal consigne la commande **à l'envoi**, or le client met quelques
 * secondes à l'exécuter : pendant ce court délai, l'état constaté contredit une
 * décision « déjà appliquée » sans qu'aucune main n'y soit pour rien. Une
 * entrée plus jeune que [CommandSettleGrace] n'atteste donc de rien.
 *
 * Le constat est purement dérivé : rien n'est mémorisé, rien n'est commandé.
 * L'automatisation ne combat pas ce choix — aucun cycle ne se déclenche tant
 * que le réseau ne change pas — et le prochain changement de réseau le
 * périme naturellement.
 */
class DetectManualOverrideUseCase(
    private val networkObserver: NetworkObserver,
    private val evaluateRules: EvaluateRulesUseCase,
    private val clock: Clock,
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
    ): ManualOverride? = invoke(evaluateRules(networkContext), tunnelState, lastChange)

    /**
     * Constat sur une évaluation déjà faite.
     *
     * C'est la forme qu'emploie [DescribeTunnelStatusUseCase] : la règle
     * applicable vient d'être calculée pour la notification, la réévaluer ici
     * relirait le réseau une seconde fois et pourrait livrer un contexte
     * différent de celui que la notification s'apprête à afficher.
     *
     * @param evaluation verdict des règles sur le contexte réseau courant.
     * @param tunnelState état constaté du tunnel.
     * @param lastChange dernier changement consigné au journal, ou `null`.
     * @return la divergence attribuable à l'utilisateur, ou `null` si l'état
     *   du tunnel s'explique sans lui.
     */
    operator fun invoke(
        evaluation: RuleEvaluation,
        tunnelState: TunnelState,
        lastChange: JournalEntry?,
    ): ManualOverride? {
        // Sans journal, aucune décision n'a jamais été appliquée : rien ne
        // permet d'attribuer l'état constaté à qui que ce soit. Une entrée
        // trop fraîche n'atteste de rien non plus, le tunnel n'ayant pas
        // encore eu le temps de suivre la commande. Ni une entrée d'avant le
        // dernier boot : un redémarrage remet le tunnel dans son état par
        // défaut sans qu'aucune main n'y touche, et la prendre au mot
        // transformait ce simple fait en « geste » qui écrasait la préférence
        // apprise (constaté sur appareil : « toujours actif » redevenait
        // « toujours coupé » à chaque reboot). Le premier cycle de la session
        // réatteste la décision au journal, et la détection reprend sur cette
        // entrée-là.
        if (tunnelState == TunnelState.UNKNOWN || lastChange == null || !attests(lastChange)) {
            return null
        }

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

    /**
     * L'entrée prouve qu'une décision a été appliquée **dans cette session**,
     * et depuis assez longtemps pour que le tunnel ait suivi.
     */
    private fun attests(lastChange: JournalEntry): Boolean {
        val isSettled =
            clock.nowEpochMillis() - lastChange.epochMillis >= CommandSettleGrace.inWholeMilliseconds
        return isSettled && lastChange.epochMillis >= clock.bootEpochMillis()
    }

    companion object {
        /**
         * Délai laissé au client pour exécuter une commande journalisée.
         *
         * Largement supérieur aux une à trois secondes constatées sur
         * appareil : un vrai geste manuel survient bien après, alors qu'un
         * faux positif s'affichait à chaque transition de réseau.
         */
        val CommandSettleGrace: Duration = 10.seconds
    }
}
