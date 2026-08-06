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
     * Observe le réseau en continu.
     *
     * L'appel est idempotent : réarmer une observation déjà en place ne doit
     * rien perturber.
     */
    fun arm()

    /** Cesse d'observer. */
    fun disarm()
}
