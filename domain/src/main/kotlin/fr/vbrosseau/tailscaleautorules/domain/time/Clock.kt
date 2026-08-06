package fr.vbrosseau.tailscaleautorules.domain.time

/**
 * Source de temps du domaine.
 *
 * Seule son implémentation appelle `System.currentTimeMillis()`. Sans cette
 * abstraction, toute logique horodatée deviendrait intestable ou dépendante de
 * l'heure réelle de la machine.
 */
fun interface Clock {
    /** Millisecondes écoulées depuis l'époque Unix. */
    fun nowEpochMillis(): Long
}
