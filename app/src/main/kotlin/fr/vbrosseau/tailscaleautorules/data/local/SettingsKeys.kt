package fr.vbrosseau.tailscaleautorules.data.local

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey

/**
 * Clés du DataStore de préférences.
 *
 * Les réglages par règle sont dérivés de l'identifiant de la règle plutôt que
 * déclarés un par un : ajouter une règle ne demande alors aucune modification
 * ici. En contrepartie, un identifiant de règle **ne doit jamais changer** —
 * c'est déjà la contrainte portée par `RuleId`.
 */
internal object SettingsKeys {
    val ServiceEnabled = booleanPreferencesKey("service_enabled")
    val LearningEnabled = booleanPreferencesKey("learning_enabled")
    val LearningPrompted = booleanPreferencesKey("learning_prompted")
    val OnboardingDone = booleanPreferencesKey("onboarding_done")
    val StartOnBoot = booleanPreferencesKey("start_on_boot")
    val VerboseLogging = booleanPreferencesKey("verbose_logging")

    fun ruleEnabled(ruleId: String): Preferences.Key<Boolean> =
        booleanPreferencesKey("rule.$ruleId.enabled")

    fun rulePriority(ruleId: String): Preferences.Key<Int> =
        intPreferencesKey("rule.$ruleId.priority")

    /** Recense les règles ayant un réglage enregistré, sans liste codée en dur. */
    fun overriddenRuleIds(preferences: Preferences): Set<String> = preferences.asMap().keys
        .mapNotNull { key -> key.name.removeSurroundingRulePrefix() }
        .toSet()

    private fun String.removeSurroundingRulePrefix(): String? =
        takeIf { it.startsWith("rule.") }
            ?.removePrefix("rule.")
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotEmpty() }
}
