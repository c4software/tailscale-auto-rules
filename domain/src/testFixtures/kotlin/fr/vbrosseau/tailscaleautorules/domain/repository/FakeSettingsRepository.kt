package fr.vbrosseau.tailscaleautorules.domain.repository

import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleSettings
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Préférences en mémoire. */
class FakeSettingsRepository(
    initialSettings: AppSettings = AppSettings.Defaults,
    initialRuleSettings: Map<RuleId, RuleSettings> = emptyMap(),
) : SettingsRepository {
    private val appSettings = MutableStateFlow(initialSettings)
    private val ruleSettings = MutableStateFlow(initialRuleSettings)

    override fun observeAppSettings(): Flow<AppSettings> = appSettings.asStateFlow()

    override suspend fun currentAppSettings(): AppSettings = appSettings.value

    override suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        appSettings.value = transform(appSettings.value)
    }

    override fun observeRuleSettings(): Flow<Map<RuleId, RuleSettings>> = ruleSettings.asStateFlow()

    override suspend fun currentRuleSettings(): Map<RuleId, RuleSettings> = ruleSettings.value

    override suspend fun setRuleSettings(
        ruleId: RuleId,
        settings: RuleSettings,
    ) {
        ruleSettings.value += (ruleId to settings)
    }

    override suspend fun resetRuleSettings(ruleId: RuleId) {
        ruleSettings.value -= ruleId
    }
}
