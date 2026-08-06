package fr.vbrosseau.tailscaleautorules.automation

/**
 * Met en place — ou retire — l'observation du réseau qui pilote
 * l'automatisation.
 *
 * Abstrait derrière une interface pour que le coordinateur, qui porte la
 * logique, soit testable sans mécanique Android.
 */
interface AutomationTrigger {

    /**
     * Observe le réseau selon le mode demandé.
     *
     * L'appel est idempotent : réarmer dans le mode déjà actif ne doit rien
     * perturber, réarmer dans l'autre mode doit basculer.
     *
     * @param immediate observation continue, qui réagit sans délai mais exige
     *   un processus vivant — donc un service de premier plan et sa
     *   notification permanente. Sinon, vérification périodique espacée.
     */
    fun arm(immediate: Boolean)

    /** Cesse toute observation, dans les deux modes. */
    fun disarm()
}
