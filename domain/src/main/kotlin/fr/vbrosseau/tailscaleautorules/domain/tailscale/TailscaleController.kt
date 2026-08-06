package fr.vbrosseau.tailscaleautorules.domain.tailscale

/**
 * Pilotage du tunnel Tailscale, vu du domaine.
 *
 * Le moteur et les cas d'usage ne connaissent que ce contrat : c'est lui qui
 * isole le seul risque fonctionnel majeur du projet (voir SPECS.md §10.1).
 *
 * **Les commandes ne sont pas synchrones.** Le client officiel accepte une
 * demande et la traite en tâche de fond, sans accusé de réception. Un
 * [Result] réussi signifie donc « la demande a été transmise », jamais « le
 * tunnel est actif ». Seul [isRunning] fait foi sur l'état réel, et il peut
 * mettre un instant à refléter la demande.
 */
interface TailscaleController {
    /** Vrai lorsqu'un client Tailscale pilotable est présent sur le terminal. */
    suspend fun isAvailable(): Boolean

    /** Demande l'activation du tunnel. */
    suspend fun enable(): Result<Unit>

    /** Demande la désactivation du tunnel. */
    suspend fun disable(): Result<Unit>

    /** État réel du tunnel au moment de l'appel. */
    suspend fun isRunning(): Boolean
}

/**
 * Aucun client Tailscale pilotable n'est installé.
 *
 * C'est une issue nominale, pas un bug : l'application doit continuer de
 * fonctionner et le signaler à l'utilisateur.
 */
class TailscaleUnavailableException : Exception(
    "Aucun client Tailscale pilotable n'est installé sur ce terminal.",
)
