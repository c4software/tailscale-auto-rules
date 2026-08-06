package fr.vbrosseau.tailscaleautorules.data.repository

import fr.vbrosseau.tailscaleautorules.data.local.JournalDao
import fr.vbrosseau.tailscaleautorules.data.local.JournalEntryEntity
import fr.vbrosseau.tailscaleautorules.di.IoDispatcher
import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomJournalRepository @Inject constructor(
    private val dao: JournalDao,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : JournalRepository {

    override fun observeRecent(): Flow<List<JournalEntry>> = dao.observeRecent()
        .map { entities -> entities.mapNotNull { it.toDomainOrNull() } }
        .flowOn(ioDispatcher)

    override suspend fun record(
        previousState: TunnelState,
        newState: TunnelState,
        ruleId: RuleId,
    ) = withContext(ioDispatcher) {
        dao.insertAndTrim(
            JournalEntryEntity(
                epochMillis = clock.nowEpochMillis(),
                previousState = previousState.name,
                newState = newState.name,
                ruleId = ruleId.value,
            ),
            limit = JournalRepository.MAX_ENTRIES,
        )
    }

    override suspend fun clear() = withContext(ioDispatcher) { dao.clear() }

    /**
     * Une entrée illisible est ignorée plutôt que fatale.
     *
     * Un état inconnu ne peut venir que d'une base écrite par une version
     * ultérieure — restauration de sauvegarde, retour arrière de version. Faire
     * planter l'affichage du journal pour une ligne aberrante serait
     * disproportionné.
     */
    private fun JournalEntryEntity.toDomainOrNull(): JournalEntry? {
        val previous = tunnelStateOrNull(previousState)
        val next = tunnelStateOrNull(newState)

        // `previous == next` violerait l'invariant de JournalEntry : la ligne
        // est écartée plutôt que de faire lever une exception à la lecture.
        return if (previous == null || next == null || previous == next) {
            null
        } else {
            JournalEntry(
                id = id,
                epochMillis = epochMillis,
                previousState = previous,
                newState = next,
                ruleId = RuleId(ruleId),
            )
        }
    }

    private fun tunnelStateOrNull(name: String): TunnelState? =
        TunnelState.entries.firstOrNull { it.name == name }
}
