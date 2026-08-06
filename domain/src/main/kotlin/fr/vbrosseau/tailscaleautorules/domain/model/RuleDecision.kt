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
}
