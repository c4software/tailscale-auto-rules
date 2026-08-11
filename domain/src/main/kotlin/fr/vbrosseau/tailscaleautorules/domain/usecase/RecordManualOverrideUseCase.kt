package fr.vbrosseau.tailscaleautorules.domain.usecase

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.NetworkPreferenceRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.NetworkPreferenceRule

/**
 * Mémorise un geste manuel comme exception dynamique (SPECS.md §3.3).
 *
 * L'état observé est **toujours** celui mémorisé — que le geste contredise une
 * règle de base ou une exception déjà apprise : la mémoire d'un réseau est son
 * dernier geste, sans autre cas particulier.
 *
 * La mémorisation écrit aussi une entrée de journal sous [NetworkPreferenceRule.Id].
 * Ce n'est pas qu'une trace : la détection d'un geste exige que le journal
 * atteste de la décision courante — sans cette entrée, le prochain geste sur le
 * même réseau serait invisible, le journal portant encore une cible périmée.
 *
 * Le geste n'est pas mémorisé lorsque l'apprentissage ou le service sont
 * coupés, en mode avion, ou quand le réseau n'a pas de clé (§4.5) : une
 * exception sans identité stable rejouerait sur un réseau qui n'est pas celui
 * du geste.
 */
class RecordManualOverrideUseCase(
    private val settingsRepository: SettingsRepository,
    private val exceptionRepository: NetworkPreferenceRepository,
    private val journalRepository: JournalRepository,
) {
    /** @return `true` lorsque le geste a été mémorisé. */
    suspend operator fun invoke(
        networkContext: NetworkContext,
        manualOverride: ManualOverride,
    ): Boolean {
        val key = if (isLearningAllowed(networkContext)) NetworkPreferenceKey.from(networkContext) else null
        if (key == null) return false

        val observedState = manualOverride.observedState
        val previousState = observedState.opposite()
        exceptionRepository.upsert(key, networkContext.ssid, observedState)
        journalRepository.record(
            previousState = previousState,
            newState = observedState,
            ruleId = NetworkPreferenceRule.Id,
        )
        return true
    }

    private suspend fun isLearningAllowed(networkContext: NetworkContext): Boolean {
        val settings = settingsRepository.currentAppSettings()
        return settings.isServiceEnabled && settings.isLearningEnabled && !networkContext.isAirplaneModeOn
    }

    /**
     * L'état que les règles avaient appliqué, et que le geste vient d'inverser.
     * La détection garantit un état constaté ferme : UNKNOWN n'arrive pas ici.
     */
    private fun TunnelState.opposite(): TunnelState =
        when (this) {
            TunnelState.ENABLED -> TunnelState.DISABLED
            TunnelState.DISABLED -> TunnelState.ENABLED
            TunnelState.UNKNOWN -> error("Un geste manuel constate un état ferme, jamais UNKNOWN.")
        }
}
