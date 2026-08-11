package fr.vbrosseau.tailscaleautorules.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.tailscaleautorules.data.local.AppDatabase
import fr.vbrosseau.tailscaleautorules.data.local.NetworkPreferenceEntity
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Éprouve la persistance réelle des exceptions dynamiques.
 *
 * L'enjeu principal est « une mémoire par réseau » : le remplacement passe par
 * une transaction et un index unique de la base, ce que seul un vrai moteur
 * SQLite peut confirmer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomNetworkPreferenceRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: RoomNetworkPreferenceRepository
    private val clock = FakeClock(1_000)

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository =
            RoomNetworkPreferenceRepository(
                database.networkPreferenceDao(),
                clock,
                UnconfinedTestDispatcher(),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aMemorizedGestureIsPersistedAndVisible() = runTest {
        repository.upsert(NetworkPreferenceKey("wifi:maison"), "Maison", TunnelState.ENABLED)

        val exception = repository.observeAll().first().single()
        assertEquals("Maison", exception.ssid)
        assertEquals(1_000, exception.epochMillis)
        assertEquals(
            mapOf(NetworkPreferenceKey("wifi:maison") to TunnelState.ENABLED),
            repository.current(),
        )
    }

    @Test
    fun aNewGestureReplacesTheExceptionOfTheSameNetworkKeepingItsIdentity() = runTest {
        repository.upsert(NetworkPreferenceKey.Cellular, null, TunnelState.DISABLED)
        val id = repository.observeAll().first().single().id

        clock.advanceBy(5_000)
        repository.upsert(NetworkPreferenceKey.Cellular, null, TunnelState.ENABLED)

        val exception = repository.observeAll().first().single()
        assertEquals(id, exception.id)
        assertEquals(TunnelState.ENABLED, exception.desiredState)
        assertEquals(6_000, exception.epochMillis)
    }

    @Test
    fun theMostRecentGestureComesFirst() = runTest {
        repository.upsert(NetworkPreferenceKey("wifi:maison"), "Maison", TunnelState.ENABLED)
        clock.advanceBy(5_000)
        repository.upsert(NetworkPreferenceKey.Cellular, null, TunnelState.DISABLED)

        val keys = repository.observeAll().first().map { it.key }
        assertEquals(listOf(NetworkPreferenceKey.Cellular, NetworkPreferenceKey("wifi:maison")), keys)
    }

    @Test
    fun removingAnExceptionLeavesTheOthersUntouched() = runTest {
        repository.upsert(NetworkPreferenceKey("wifi:maison"), "Maison", TunnelState.ENABLED)
        repository.upsert(NetworkPreferenceKey.Cellular, null, TunnelState.DISABLED)
        val id = repository.observeAll().first().first { it.ssid == "Maison" }.id

        repository.remove(id)

        assertEquals(listOf(NetworkPreferenceKey.Cellular), repository.observeAll().first().map { it.key })
    }

    @Test
    fun anUnreadableRowIsIgnoredRatherThanFatal() = runTest {
        // Un état inconnu ou une ligne incohérente ne peuvent venir que d'une
        // base écrite par une version ultérieure : elle ne doit ni planter
        // l'affichage, ni surtout piloter le tunnel.
        database.networkPreferenceDao().insert(
            NetworkPreferenceEntity(
                networkKey = "wifi:bureau",
                ssid = "Bureau",
                desiredState = "BROKEN",
                epochMillis = 1,
            ),
        )
        database.networkPreferenceDao().insert(
            NetworkPreferenceEntity(
                networkKey = "cellular",
                ssid = "Incohérent",
                desiredState = "ENABLED",
                epochMillis = 2,
            ),
        )
        repository.upsert(NetworkPreferenceKey("wifi:maison"), "Maison", TunnelState.ENABLED)

        assertEquals(listOf("Maison"), repository.observeAll().first().map { it.ssid })
        assertEquals(mapOf(NetworkPreferenceKey("wifi:maison") to TunnelState.ENABLED), repository.current())
        assertTrue(
            database.networkPreferenceDao().getAll().size == 3,
            "Les lignes restent en base, seules leurs lectures sont écartées.",
        )
    }
}
