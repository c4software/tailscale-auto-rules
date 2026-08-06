package fr.vbrosseau.tailscaleautorules.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import fr.vbrosseau.tailscaleautorules.data.local.SettingsKeys
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleSettings
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Préférences scalaires, dans DataStore.
 *
 * Aucune collection ici : la blacklist et le journal vivent dans Room. Les deux
 * supports ne se recouvrent jamais (SPECS.md §9).
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeAppSettings(): Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

    override suspend fun currentAppSettings(): AppSettings = dataStore.data.first().toAppSettings()

    override suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toAppSettings())
            preferences[SettingsKeys.ServiceEnabled] = updated.isServiceEnabled
            preferences[SettingsKeys.StartOnBoot] = updated.startOnBoot
            preferences[SettingsKeys.VerboseLogging] = updated.verboseLogging
        }
    }

    override fun observeRuleSettings(): Flow<Map<RuleId, RuleSettings>> =
        dataStore.data.map { it.toRuleSettings() }

    override suspend fun currentRuleSettings(): Map<RuleId, RuleSettings> =
        dataStore.data.first().toRuleSettings()

    override suspend fun setRuleSettings(ruleId: RuleId, settings: RuleSettings) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.ruleEnabled(ruleId.value)] = settings.isEnabled
            preferences[SettingsKeys.rulePriority(ruleId.value)] = settings.priority
        }
    }

    override suspend fun resetRuleSettings(ruleId: RuleId) {
        dataStore.edit { preferences ->
            preferences.remove(SettingsKeys.ruleEnabled(ruleId.value))
            preferences.remove(SettingsKeys.rulePriority(ruleId.value))
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val defaults = AppSettings.Defaults
        return AppSettings(
            isServiceEnabled = this[SettingsKeys.ServiceEnabled] ?: defaults.isServiceEnabled,
            startOnBoot = this[SettingsKeys.StartOnBoot] ?: defaults.startOnBoot,
            verboseLogging = this[SettingsKeys.VerboseLogging] ?: defaults.verboseLogging,
        )
    }

    /**
     * Une règle n'apparaît que si **ses deux** réglages sont présents.
     *
     * Un enregistrement partiel — écriture interrompue, migration — ne doit pas
     * produire une surcharge à moitié définie : la règle reprend alors
     * simplement ses valeurs par défaut.
     */
    private fun Preferences.toRuleSettings(): Map<RuleId, RuleSettings> =
        SettingsKeys.overriddenRuleIds(this).mapNotNull { ruleId ->
            val isEnabled = this[SettingsKeys.ruleEnabled(ruleId)]
            val priority = this[SettingsKeys.rulePriority(ruleId)]
            if (isEnabled == null || priority == null) {
                null
            } else {
                RuleId(ruleId) to RuleSettings(isEnabled = isEnabled, priority = priority)
            }
        }.toMap()
}
