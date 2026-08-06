package fr.vbrosseau.tailscaleautorules.automation

/**
 * Arme ou désarme le réveil automatique de l'application.
 *
 * Abstrait derrière une interface pour que le coordinateur — qui porte la
 * logique — soit testable sans mécanique Android.
 */
interface AutomationTrigger {

    /** Demande au système de réveiller l'application à chaque changement réseau. */
    fun arm()

    /** Cesse d'être réveillé. */
    fun disarm()
}
