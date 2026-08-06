package fr.vbrosseau.tailscaleautorules.domain.repository

import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleSettings
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Préférences de l'utilisateur et réglages par règle.
 *
 * Deux flux séparés plutôt qu'un seul objet : les préférences générales et les
 * réglages de règles n'ont ni le même rythme de changement ni les mêmes
 * lecteurs, et les fusionner ferait recalculer l'un à chaque modification de
 * l'autre.
 *
 * Une règle absente de la carte utilise ses réglages par défaut. Ne stocker que
 * les écarts évite d'avoir à migrer la persistance à chaque nouvelle règle.
 */
interface SettingsRepository {
    fun observeAppSettings(): Flow<AppSettings>

    /** Instantané des préférences, pour un cycle ponctuel de synchronisation. */
    suspend fun currentAppSettings(): AppSettings

    suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings)

    /** Réglages surchargés par l'utilisateur, par règle. */
    fun observeRuleSettings(): Flow<Map<RuleId, RuleSettings>>

    suspend fun currentRuleSettings(): Map<RuleId, RuleSettings>

    suspend fun setRuleSettings(
        ruleId: RuleId,
        settings: RuleSettings,
    )

    /** Rend à une règle ses réglages par défaut. */
    suspend fun resetRuleSettings(ruleId: RuleId)
}
