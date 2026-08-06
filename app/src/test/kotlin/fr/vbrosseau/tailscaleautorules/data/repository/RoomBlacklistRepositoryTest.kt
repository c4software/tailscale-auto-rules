package fr.vbrosseau.tailscaleautorules.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.tailscaleautorules.data.local.AppDatabase
import fr.vbrosseau.tailscaleautorules.domain.repository.DuplicateSsidException
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Éprouve la persistance réelle, base SQLite en mémoire comprise.
 *
 * L'enjeu principal est l'unicité : elle est portée par un index de la base, et
 * non par une vérification applicative. Seul un test contre un vrai moteur
 * SQLite peut le confirmer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomBlacklistRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: RoomBlacklistRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomBlacklistRepository(database.blacklistDao(), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun anAddedSsidIsPersistedAndVisible() = runTest {
        assertTrue(repository.add("Maison").isSuccess)

        assertEquals(listOf("Maison"), repository.observeAll().first().map { it.value })
        assertEquals(setOf("Maison"), repository.currentSsids())
    }

    @Test
    fun surroundingSpacesAreStrippedBeforeStoring() = runTest {
        repository.add("  Maison  ")

        assertEquals("Maison", repository.observeAll().first().single().value)
    }

    @Test
    fun theDatabaseItselfRejectsACanonicalDuplicate() = runTest {
        repository.add("Maison")

        val result = repository.add("  MAISON ")

        assertIs<DuplicateSsidException>(result.exceptionOrNull())
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun theOriginalCasingIsPreservedForDisplay() = runTest {
        // La normalisation sert à comparer, pas à réécrire ce que l'utilisateur
        // a saisi.
        repository.add("MaIsOn Fibre")

        assertEquals("MaIsOn Fibre", repository.observeAll().first().single().value)
    }

    @Test
    fun entriesAreOrderedRegardlessOfTypedCase() = runTest {
        repository.add("zebra")
        repository.add("Alpha")
        repository.add("Milieu")

        assertEquals(
            listOf("Alpha", "Milieu", "zebra"),
            repository.observeAll().first().map { it.value },
        )
    }

    @Test
    fun renamingKeepsTheIdentityAndUpdatesTheCanonicalForm() = runTest {
        repository.add("Maison")
        val id = repository.observeAll().first().single().id

        assertTrue(repository.update(id, "Bureau").isSuccess)

        val entry = repository.observeAll().first().single()
        assertEquals(id, entry.id)
        assertEquals("Bureau", entry.value)
        // La forme canonique a suivi : « maison » redevient disponible.
        assertTrue(repository.add("Maison").isSuccess)
    }

    @Test
    fun correctingTheCaseOfAnEntryIsNotADuplicate() = runTest {
        repository.add("maison")
        val id = repository.observeAll().first().single().id

        assertTrue(repository.update(id, "Maison").isSuccess)
        assertEquals("Maison", repository.observeAll().first().single().value)
    }

    @Test
    fun renamingOntoAnExistingEntryIsRejected() = runTest {
        repository.add("Maison")
        repository.add("Bureau")
        val bureauId = repository.observeAll().first().first { it.value == "Bureau" }.id

        val result = repository.update(bureauId, "maison")

        assertIs<DuplicateSsidException>(result.exceptionOrNull())
        assertEquals(2, repository.observeAll().first().size)
    }

    @Test
    fun renamingAnUnknownEntryFailsWithoutCreatingAnything() = runTest {
        val result = repository.update(id = 404, ssid = "Fantôme")

        assertIs<NoSuchElementException>(result.exceptionOrNull())
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun removingAnEntryLeavesTheOthers() = runTest {
        repository.add("Maison")
        repository.add("Bureau")
        val id = repository.observeAll().first().first().id

        repository.remove(id)

        assertEquals(listOf("Maison"), repository.observeAll().first().map { it.value })
    }

    @Test
    fun removingAnUnknownEntryIsHarmless() = runTest {
        repository.add("Maison")

        repository.remove(404)

        assertEquals(1, repository.observeAll().first().size)
    }
}
