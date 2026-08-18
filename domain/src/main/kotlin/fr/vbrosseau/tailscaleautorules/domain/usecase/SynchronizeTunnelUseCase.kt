package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleController

/**
 * Exécute un cycle complet : capture du contexte, évaluation, application,
 * journalisation.
 *
 * C'est le seul composant qui connaît l'enchaînement. Le moteur ignore le
 * tunnel, les règles ignorent le journal, et le contrôleur ignore les règles :
 * l'orchestration est rassemblée ici plutôt que dispersée.
 */
class SynchronizeTunnelUseCase(
    private val networkObserver: NetworkObserver,
    private val settingsRepository: SettingsRepository,
    private val evaluateRules: EvaluateRulesUseCase,
    private val controller: TailscaleController,
    private val journalRepository: JournalRepository,
    private val sessionAttestation: SessionAttestation,
) {
    /** Cycle sur le contexte réseau courant. */
    suspend operator fun invoke(): SynchronizationOutcome = invoke(networkObserver.current())

    /**
     * Cycle sur un contexte déjà capturé.
     *
     * Cette forme sert l'observation continue, qui fournit un contexte déjà
     * stabilisé : le relire ici perdrait le bénéfice du debounce et pourrait
     * même livrer un contexte différent de celui qui a déclenché le cycle.
     */
    suspend operator fun invoke(networkContext: NetworkContext): SynchronizationOutcome =
        if (settingsRepository.currentAppSettings().isServiceEnabled) {
            decideAndApply(networkContext)
        } else {
            SynchronizationOutcome.ServiceDisabled
        }

    private suspend fun decideAndApply(networkContext: NetworkContext): SynchronizationOutcome {
        val evaluation = evaluateRules(networkContext)

        val ruleId = evaluation.ruleId
        val targetState = evaluation.decision.asTunnelState()

        return if (ruleId == null || targetState == null) {
            SynchronizationOutcome.NoDecision
        } else {
            apply(targetState, ruleId)
        }
    }

    private suspend fun apply(
        targetState: TunnelState,
        ruleId: RuleId,
    ): SynchronizationOutcome =
        when (val currentState = readTunnelState()) {
            null -> SynchronizationOutcome.TailscaleUnavailable

            // Ne rien faire quand l'état visé est déjà le sien : c'est ce qui évite
            // de saturer le journal et de solliciter le client sans raison. Le
            // constat vaut pourtant attestation, retenue en mémoire : après un
            // boot, le journal ne recevrait sinon jamais d'entrée de la session
            // et la détection du geste manuel resterait morte (SPECS.md §3.3).
            targetState -> {
                sessionAttestation.confirm(currentState)
                SynchronizationOutcome.AlreadyInTargetState(ruleId, currentState)
            }

            else ->
                command(targetState).fold(
                    onSuccess = {
                        journalRepository.record(currentState, targetState, ruleId)
                        SynchronizationOutcome.Applied(ruleId, currentState, targetState)
                    },
                    // Une commande refusée n'a rien changé : elle n'a rien à faire au
                    // journal, qui atteste de ce qui a eu lieu.
                    onFailure = { cause -> SynchronizationOutcome.Failed(ruleId, cause) },
                )
        }

    private suspend fun command(targetState: TunnelState): Result<Unit> =
        if (targetState == TunnelState.ENABLED) controller.enable() else controller.disable()

    /** `null` lorsqu'aucun client pilotable n'est installé. */
    private suspend fun readTunnelState(): TunnelState? =
        when {
            !controller.isAvailable() -> null
            controller.isRunning() -> TunnelState.ENABLED
            else -> TunnelState.DISABLED
        }
}
