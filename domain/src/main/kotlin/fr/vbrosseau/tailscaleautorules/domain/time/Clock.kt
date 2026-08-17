package fr.vbrosseau.tailscaleautorules.domain.time

/**
 * Source de temps du domaine.
 *
 * Seule son implémentation appelle `System.currentTimeMillis()`. Sans cette
 * abstraction, toute logique horodatée deviendrait intestable ou dépendante de
 * l'heure réelle de la machine.
 */
interface Clock {
    /** Millisecondes écoulées depuis l'époque Unix. */
    fun nowEpochMillis(): Long

    /**
     * Instant du dernier démarrage du terminal, en millisecondes d'époque.
     *
     * Un redémarrage remet le tunnel dans son état par défaut sans qu'aucune
     * main n'y touche : ce repère permet d'écarter les attestations du journal
     * qui datent d'une session éteinte depuis.
     */
    fun bootEpochMillis(): Long
}
