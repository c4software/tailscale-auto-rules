package fr.vbrosseau.tailscaleautorules.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.vbrosseau.tailscaleautorules.data.local.AppDatabase
import fr.vbrosseau.tailscaleautorules.data.local.NetworkExceptionEntity
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkExceptionKey
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
class RoomNetworkExceptionRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: RoomNetworkExceptionRepository
    private val clock = FakeClock(1_000)

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository =
            RoomNetworkExceptionRepository(
                database.networkExceptionDao(),
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
        repository.upsert(NetworkExceptionKey("wifi:maison"), "Maison", TunnelState.ENABLED)

        val exception = repository.observeAll().first().single()
        assertEquals("Maison", exception.ssid)
        assertEquals(1_000, exception.epochMillis)
        assertEquals(
            mapOf(NetworkExceptionKey("wifi:maison") to TunnelState.ENABLED),
            repository.current(),
        )
    }

    @Test
    fun aNewGestureReplacesTheExceptionOfTheSameNetworkKeepingItsIdentity() = runTest {
        repository.upsert(NetworkExceptionKey.Cellular, null, TunnelState.DISABLED)
        val id = repository.observeAll().first().single().id

        clock.advanceBy(5_000)
        repository.upsert(NetworkExceptionKey.Cellular, null, TunnelState.ENABLED)

        val exception = repository.observeAll().first().single()
        assertEquals(id, exception.id)
        assertEquals(TunnelState.ENABLED, exception.desiredState)
        assertEquals(6_000, exception.epochMillis)
    }

    @Test
    fun theMostRecentGestureComesFirst() = runTest {
        repository.upsert(NetworkExceptionKey("wifi:maison"), "Maison", TunnelState.ENABLED)
        clock.advanceBy(5_000)
        repository.upsert(NetworkExceptionKey.Cellular, null, TunnelState.DISABLED)

        val keys = repository.observeAll().first().map { it.key }
        assertEquals(listOf(NetworkExceptionKey.Cellular, NetworkExceptionKey("wifi:maison")), keys)
    }

    @Test
    fun removingAnExceptionLeavesTheOthersUntouched() = runTest {
        repository.upsert(NetworkExceptionKey("wifi:maison"), "Maison", TunnelState.ENABLED)
        repository.upsert(NetworkExceptionKey.Cellular, null, TunnelState.DISABLED)
        val id = repository.observeAll().first().first { it.ssid == "Maison" }.id

        repository.remove(id)

        assertEquals(listOf(NetworkExceptionKey.Cellular), repository.observeAll().first().map { it.key })
    }

    @Test
    fun anUnreadableRowIsIgnoredRatherThanFatal() = runTest {
        // Un état inconnu ou une ligne incohérente ne peuvent venir que d'une
        // base écrite par une version ultérieure : elle ne doit ni planter
        // l'affichage, ni surtout piloter le tunnel.
        database.networkExceptionDao().insert(
            NetworkExceptionEntity(
                networkKey = "wifi:bureau",
                ssid = "Bureau",
                desiredState = "BROKEN",
                epochMillis = 1,
            ),
        )
        database.networkExceptionDao().insert(
            NetworkExceptionEntity(
                networkKey = "cellular",
                ssid = "Incohérent",
                desiredState = "ENABLED",
                epochMillis = 2,
            ),
        )
        repository.upsert(NetworkExceptionKey("wifi:maison"), "Maison", TunnelState.ENABLED)

        assertEquals(listOf("Maison"), repository.observeAll().first().map { it.ssid })
        assertEquals(mapOf(NetworkExceptionKey("wifi:maison") to TunnelState.ENABLED), repository.current())
        assertTrue(
            database.networkExceptionDao().getAll().size == 3,
            "Les lignes restent en base, seules leurs lectures sont écartées.",
        )
    }
}
