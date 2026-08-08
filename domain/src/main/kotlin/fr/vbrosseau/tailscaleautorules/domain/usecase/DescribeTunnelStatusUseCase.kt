package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleController
import kotlinx.coroutines.flow.first

/**
 * Ce que la notification d'état a besoin de dire.
 *
 * @param state état **constaté** du tunnel, jamais celui qu'on a demandé.
 * @param ruleId règle qui s'applique **au réseau courant**, ou `null` si aucune
 *   ne se prononce.
 * @param isManuallyOverridden l'état constaté vient d'un geste de
 *   l'utilisateur, pas d'une règle.
 */
data class TunnelStatus(
    val state: TunnelState,
    val ruleId: RuleId?,
    val isManuallyOverridden: Boolean,
)

/**
 * Décrit l'état affichable du tunnel.
 *
 * **La raison affichée est la règle applicable maintenant, pas la dernière
 * consignée au journal.** La distinction n'est pas cosmétique : le cycle
 * n'écrit au journal que lorsqu'il *change* l'état du tunnel
 * ([SynchronizeTunnelUseCase] renvoie `AlreadyInTargetState` sans rien
 * consigner). Une règle qui confirme un état déjà atteint — le passage d'un
 * Wi-Fi inconnu aux données mobiles, tunnel actif de part et d'autre — ne
 * laisse donc aucune trace, et la notification restait indéfiniment sur la
 * raison d'un changement révolu, jusqu'à afficher « tunnel activé » sous une
 * raison qui le désactive.
 *
 * Le journal garde son rôle : attester de ce qui a **eu lieu**, ce dont le
 * constat d'intervention manuelle a besoin.
 *
 * Le réseau n'est lu qu'une fois, et les règles évaluées une seule fois : les
 * trois informations décrivent ainsi le même instant.
 */
class DescribeTunnelStatusUseCase(
    private val networkObserver: NetworkObserver,
    private val evaluateRules: EvaluateRulesUseCase,
    private val detectManualOverride: DetectManualOverrideUseCase,
    private val journalRepository: JournalRepository,
    private val controller: TailscaleController,
) {
    suspend operator fun invoke(): TunnelStatus {
        val state = tunnelState()
        val evaluation = evaluateRules(networkObserver.current())
        val lastChange = journalRepository.observeRecent().first().firstOrNull()
        val override = detectManualOverride(evaluation, state, lastChange)

        return TunnelStatus(
            state = state,
            ruleId = evaluation.ruleId,
            isManuallyOverridden = override != null,
        )
    }

    private suspend fun tunnelState(): TunnelState =
        when {
            !controller.isAvailable() -> TunnelState.UNKNOWN
            controller.isRunning() -> TunnelState.ENABLED
            else -> TunnelState.DISABLED
        }
}
