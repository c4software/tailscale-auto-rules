package fr.vbrosseau.tailscaleautorules.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.tailscaleautorules.data.local.AppDatabase
import fr.vbrosseau.tailscaleautorules.data.local.JournalEntryEntity
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomJournalRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var clock: FakeClock
    private lateinit var repository: RoomJournalRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = FakeClock(1_000)
        repository = RoomJournalRepository(database.journalDao(), clock, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun recordEnable(ruleId: String) =
        repository.record(TunnelState.DISABLED, TunnelState.ENABLED, RuleId(ruleId))

    private suspend fun recordDisable(ruleId: String) =
        repository.record(TunnelState.ENABLED, TunnelState.DISABLED, RuleId(ruleId))

    @Test
    fun aRecordedChangeCarriesItsTimestampAndRule() = runTest {
        recordEnable("mobile-network")

        val entry = repository.observeRecent().first().single()
        assertEquals(1_000, entry.epochMillis)
        assertEquals(TunnelState.DISABLED, entry.previousState)
        assertEquals(TunnelState.ENABLED, entry.newState)
        assertEquals(RuleId("mobile-network"), entry.ruleId)
    }

    @Test
    fun theMostRecentEntryComesFirst() = runTest {
        recordEnable("mobile-network")
        clock.advanceBy(5_000)
        recordDisable("airplane-mode")

        val entries = repository.observeRecent().first()
        assertEquals(RuleId("airplane-mode"), entries.first().ruleId)
        assertEquals(RuleId("mobile-network"), entries.last().ruleId)
    }

    @Test
    fun entriesSharingATimestampKeepAStableOrder() = runTest {
        // Deux synchronisations dans la même milliseconde restent possibles :
        // sans départage par identifiant, leur ordre serait indéterminé.
        recordEnable("first")
        recordDisable("second")

        val entries = repository.observeRecent().first()
        assertEquals(RuleId("second"), entries.first().ruleId)
        assertEquals(RuleId("first"), entries.last().ruleId)
    }

    @Test
    fun theJournalIsCappedAtItsDocumentedCapacity() = runTest {
        repeat(JournalRepository.MAX_ENTRIES + 25) { index ->
            clock.advanceBy(1_000)
            if (index % 2 == 0) recordEnable("rule-$index") else recordDisable("rule-$index")
        }

        assertEquals(JournalRepository.MAX_ENTRIES, database.journalDao().count())
    }

    @Test
    fun thePurgeRemovesTheOldestEntriesFirst() = runTest {
        repeat(JournalRepository.MAX_ENTRIES + 10) { index ->
            clock.advanceBy(1_000)
            if (index % 2 == 0) recordEnable("rule-$index") else recordDisable("rule-$index")
        }

        val entries = repository.observeRecent().first()
        assertEquals(RuleId("rule-509"), entries.first().ruleId)
        assertEquals(RuleId("rule-10"), entries.last().ruleId)
    }

    @Test
    fun clearingEmptiesTheJournal() = runTest {
        recordEnable("a")

        repository.clear()

        assertTrue(repository.observeRecent().first().isEmpty())
        assertEquals(0, database.journalDao().count())
    }

    @Test
    fun anUnreadableRowIsSkippedInsteadOfBreakingTheJournal() = runTest {
        // Une base écrite par une version ultérieure, ou restaurée depuis une
        // sauvegarde, peut contenir un état inconnu. L'affichage ne doit pas
        // s'effondrer pour autant.
        recordEnable("valide")
        database.journalDao().insert(
            JournalEntryEntity(
                epochMillis = 2_000,
                previousState = "TELEPORTED",
                newState = "ENABLED",
                ruleId = "venu-du-futur",
            ),
        )

        val entries = repository.observeRecent().first()
        assertEquals(listOf(RuleId("valide")), entries.map { it.ruleId })
    }

    @Test
    fun aRowWithoutActualChangeIsSkipped() = runTest {
        // L'invariant de JournalEntry interdit d'en construire une : la ligne
        // est ignorée plutôt que de faire lever une exception à la lecture.
        database.journalDao().insert(
            JournalEntryEntity(
                epochMillis = 2_000,
                previousState = "ENABLED",
                newState = "ENABLED",
                ruleId = "sans-effet",
            ),
        )

        assertTrue(repository.observeRecent().first().isEmpty())
    }
}
