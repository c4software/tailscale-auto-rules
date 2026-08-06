package fr.vbrosseau.tailscaleautorules.domain.rule

/**
 * Réglages d'une règle, tels que l'utilisateur peut les modifier.
 *
 * Ils vivent hors de la règle plutôt qu'en son sein : une règle reste ainsi un
 * objet sans état, partageable et évaluable en parallèle, et l'ajout d'un
 * réglage ne touche aucune règle existante.
 */
data class RuleSettings(
    val isEnabled: Boolean,
    val priority: Int,
)
