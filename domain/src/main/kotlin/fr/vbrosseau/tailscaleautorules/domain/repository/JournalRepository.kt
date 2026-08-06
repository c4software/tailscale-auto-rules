package fr.vbrosseau.tailscaleautorules.domain.repository

import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import kotlinx.coroutines.flow.Flow

/**
 * Historique des changements d'état du tunnel.
 *
 * La capacité est bornée à [MAX_ENTRIES] : le journal sert au diagnostic
 * récent, pas à l'archivage. Sans purge, une application qui change souvent de
 * réseau ferait croître sa base indéfiniment.
 */
interface JournalRepository {
    /** Entrées de la plus récente à la plus ancienne. */
    fun observeRecent(): Flow<List<JournalEntry>>

    /**
     * Consigne un changement d'état et purge le surplus.
     *
     * L'horodatage est posé par l'implémentation, à partir de l'horloge
     * injectée : l'appelant n'a pas à connaître l'heure.
     */
    suspend fun record(
        previousState: TunnelState,
        newState: TunnelState,
        ruleId: RuleId,
    )

    suspend fun clear()

    companion object {
        const val MAX_ENTRIES = 500
    }
}
