package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.time.Clock

/**
 * Preuve, portée par la session, qu'un cycle a constaté le tunnel dans l'état
 * que les règles visaient.
 *
 * Le journal n'atteste que des changements effectifs. Quand le premier cycle
 * qui suit le boot trouve le tunnel déjà dans l'état visé, rien n'y est écrit :
 * la dernière entrée restait antérieure au boot, n'attestait donc de rien, et
 * la détection du geste manuel demeurait morte pour toute la session. Ce
 * constat sans changement est pourtant une attestation à part entière ; il est
 * retenu ici, en mémoire, avec l'état constaté. Un état seul suffit : la
 * détection compare de toute façon la décision courante à ce qui a été
 * confirmé, et une décision différente ne trouve ici rien qui la soutienne.
 *
 * La perte au décès du processus est bornée par construction : chaque session
 * reconstate au premier cycle, battement de secours compris. La grâce est la
 * même que pour une entrée de journal : un constat trop frais pourrait
 * précéder de peu un client encore en train de restaurer son propre état.
 */
class SessionAttestation(private val clock: Clock) {
    private data class Confirmation(
        val state: TunnelState,
        val epochMillis: Long,
    )

    // Volatile : confirmé sous le verrou du cycle, mais lu aussi par le chemin
    // de la notification, qui ne le prend pas.
    @Volatile
    private var confirmation: Confirmation? = null

    /** Retient qu'un cycle vient de constater le tunnel dans l'état visé. */
    fun confirm(state: TunnelState) {
        confirmation = Confirmation(state, clock.nowEpochMillis())
    }

    /**
     * Vrai lorsqu'un cycle de la session a constaté [targetState], depuis assez
     * longtemps pour qu'une restauration tardive du client se soit manifestée.
     */
    fun attests(targetState: TunnelState): Boolean {
        val confirmed = confirmation ?: return false

        val isSettled =
            clock.nowEpochMillis() - confirmed.epochMillis >=
                DetectManualOverrideUseCase.CommandSettleGrace.inWholeMilliseconds

        return confirmed.state == targetState && isSettled
    }
}
