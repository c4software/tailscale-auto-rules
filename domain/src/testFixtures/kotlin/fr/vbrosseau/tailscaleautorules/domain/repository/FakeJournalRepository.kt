package fr.vbrosseau.tailscaleautorules.domain.repository

import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.time.Clock
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Journal en mémoire, purge comprise. */
class FakeJournalRepository(private val clock: Clock = FakeClock()) : JournalRepository {
    private var nextId = 1L
    private val entries = MutableStateFlow(emptyList<JournalEntry>())

    override fun observeRecent(): Flow<List<JournalEntry>> = entries.asStateFlow()

    override suspend fun record(
        previousState: TunnelState,
        newState: TunnelState,
        ruleId: RuleId,
    ) {
        val entry =
            JournalEntry(
                id = nextId++,
                epochMillis = clock.nowEpochMillis(),
                previousState = previousState,
                newState = newState,
                ruleId = ruleId,
            )
        entries.value = (listOf(entry) + entries.value).take(JournalRepository.MAX_ENTRIES)
    }

    override suspend fun clear() {
        entries.value = emptyList()
    }
}
