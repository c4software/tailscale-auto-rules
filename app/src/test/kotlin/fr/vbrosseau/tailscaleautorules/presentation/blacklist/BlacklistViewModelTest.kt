package fr.vbrosseau.tailscaleautorules.presentation.blacklist

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.network.FakeNetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeBlacklistRepository
import fr.vbrosseau.tailscaleautorules.presentation.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlacklistViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeBlacklistRepository()
    private val observer = FakeNetworkObserver()

    private fun viewModel() = BlacklistViewModel(repository, observer)

    private fun onWifi(ssid: String?) = observer.emit(
        NetworkContext(NetworkTransport.WIFI, isInternetValidated = true, ssid = ssid),
    )

    @Test
    fun theListFollowsTheRepository() = runTest {
        val model = viewModel()

        model.add("Maison")

        assertEquals(listOf("Maison"), model.uiState.value.entries.map { it.value })
    }

    @Test
    fun aDuplicateIsTranslatedIntoADisplayableError() = runTest {
        val model = viewModel()
        model.add("Maison")

        model.add("  maison ")

        assertEquals(BlacklistError.DUPLICATE, model.uiState.value.error)
        assertEquals(1, model.uiState.value.entries.size)
    }

    @Test
    fun anErrorCanBeDismissed() = runTest {
        val model = viewModel()
        model.add("Maison")
        model.add("Maison")

        model.dismissError()

        assertNull(model.uiState.value.error)
    }

    @Test
    fun aSuccessfulActionClearsThePreviousError() = runTest {
        val model = viewModel()
        model.add("Maison")
        model.add("Maison")

        model.add("Bureau")

        assertNull(model.uiState.value.error)
    }

    @Test
    fun theCurrentSsidCanBeAddedInOneGesture() = runTest {
        onWifi("Aéroport")
        val model = viewModel()

        assertTrue(model.uiState.value.canAddCurrentSsid)
        model.addCurrentSsid()

        assertEquals(listOf("Aéroport"), model.uiState.value.entries.map { it.value })
    }

    @Test
    fun anUnavailableSsidDisablesTheQuickAdd() = runTest {
        onWifi(null)

        assertTrue(!viewModel().uiState.value.canAddCurrentSsid)
    }

    @Test
    fun anAlreadyListedSsidDisablesTheQuickAdd() = runTest {
        // Proposer un ajout voué à échouer serait une invitation à l'erreur.
        onWifi("Maison")
        val model = viewModel()
        model.add("maison")

        assertTrue(model.uiState.value.isCurrentSsidAlreadyListed)
        assertTrue(!model.uiState.value.canAddCurrentSsid)
    }

    @Test
    fun theQuickAddIsANoOpWhenNoSsidIsAvailable() = runTest {
        val model = viewModel()

        model.addCurrentSsid()

        assertTrue(model.uiState.value.entries.isEmpty())
        assertNull(model.uiState.value.error)
    }

    @Test
    fun renamingUpdatesTheEntry() = runTest {
        val model = viewModel()
        model.add("Maison")
        val id = model.uiState.value.entries.single().id

        model.rename(id, "Maison Fibre")

        assertEquals("Maison Fibre", model.uiState.value.entries.single().value)
    }

    @Test
    fun renamingOntoAnExistingEntryReportsADuplicate() = runTest {
        val model = viewModel()
        model.add("Maison")
        model.add("Bureau")
        val bureauId = model.uiState.value.entries.first { it.value == "Bureau" }.id

        model.rename(bureauId, "maison")

        assertEquals(BlacklistError.DUPLICATE, model.uiState.value.error)
    }

    @Test
    fun removingAnEntryUpdatesTheList() = runTest {
        val model = viewModel()
        model.add("Maison")
        val id = model.uiState.value.entries.single().id

        model.remove(id)

        assertTrue(model.uiState.value.entries.isEmpty())
    }
}
