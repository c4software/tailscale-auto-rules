package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEngine
import fr.vbrosseau.tailscaleautorules.domain.engine.RuleEvaluation
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.repository.BlacklistRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.NetworkPreferenceRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleContext

/**
 * Évalue les règles sur un contexte réseau.
 *
 * Rassembler ici la construction du [RuleContext] n'est pas qu'une économie de
 * lignes : c'est la garantie que le cycle, le constat d'intervention manuelle
 * et la notification décident tous les trois **sur les mêmes entrées**. Une
 * divergence entre ces trois lectures produirait une notification qui contredit
 * ce que l'automatisation vient de faire.
 *
 * La liste noire, les exceptions dynamiques et les réglages sont relus à
 * chaque appel : une modification produit son effet dès l'évaluation suivante,
 * sans invalidation de cache ni redémarrage.
 */
class EvaluateRulesUseCase(
    private val blacklistRepository: BlacklistRepository,
    private val networkPreferenceRepository: NetworkPreferenceRepository,
    private val settingsRepository: SettingsRepository,
    private val engine: RuleEngine,
) {
    suspend operator fun invoke(networkContext: NetworkContext): RuleEvaluation =
        engine.evaluate(
            RuleContext(
                network = networkContext,
                blacklistedSsids = blacklistRepository.currentSsids(),
                networkPreferences = networkPreferenceRepository.current(),
                settings = settingsRepository.currentRuleSettings(),
            ),
        )
}
