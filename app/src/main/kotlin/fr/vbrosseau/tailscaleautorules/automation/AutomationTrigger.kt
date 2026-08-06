package fr.vbrosseau.tailscaleautorules.automation

/**
 * Arme ou désarme l'observation du réseau qui pilote l'automatisation.
 *
 * Abstrait derrière une interface pour que le coordinateur — qui porte la
 * logique — soit testable sans mécanique Android.
 */
interface AutomationTrigger {

    /** Met en place l'observation du réseau. */
    fun arm()

    /** Cesse d'observer. */
    fun disarm()
}
