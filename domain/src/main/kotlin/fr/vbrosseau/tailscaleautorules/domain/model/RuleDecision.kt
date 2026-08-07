package fr.vbrosseau.tailscaleautorules.domain.model

/**
 * Verdict rendu par une règle pour un contexte réseau donné.
 *
 * [NO_DECISION] signifie « cette règle ne se prononce pas », et non « ne rien
 * faire » : le moteur poursuit alors l'évaluation des règles suivantes.
 */
enum class RuleDecision {
    ENABLE,
    DISABLE,
    NO_DECISION,
    ;

    /**
     * Vrai lorsque la décision est ferme, c'est-à-dire qu'elle arrête
     * l'évaluation. C'est le seul critère d'arrêt du moteur.
     */
    val isFirm: Boolean get() = this != NO_DECISION

    /**
     * État du tunnel que la décision vise, ou `null` pour une abstention.
     *
     * La correspondance vit ici pour que « ce que la règle veut » et « ce que
     * le tunnel est » se comparent au même endroit chez tous les lecteurs —
     * synchronisation comme détection d'une intervention manuelle.
     */
    fun asTunnelState(): TunnelState? =
        when (this) {
            ENABLE -> TunnelState.ENABLED
            DISABLE -> TunnelState.DISABLED
            NO_DECISION -> null
        }
}
