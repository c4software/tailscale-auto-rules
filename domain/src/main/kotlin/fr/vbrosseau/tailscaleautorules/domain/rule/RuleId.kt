package fr.vbrosseau.tailscaleautorules.domain.rule

/**
 * Identifiant stable d'une règle.
 *
 * Il sert de clé de persistance (configuration, journal) et de critère de
 * départage à priorité égale. **Il ne change jamais** : le renommer romprait la
 * configuration enregistrée par l'utilisateur et l'historique du journal.
 */
@JvmInline
value class RuleId(val value: String)
