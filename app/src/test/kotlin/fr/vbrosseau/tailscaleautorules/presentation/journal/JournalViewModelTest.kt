package fr.vbrosseau.tailscaleautorules.presentation.journal

import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.repository.FakeJournalRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import fr.vbrosseau.tailscaleautorules.presentation.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JournalViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeClock(1_000)
    private val repository = FakeJournalRepository(clock)
    private fun viewModel() = JournalViewModel(repository)

    @Test
    fun anEmptyJournalIsReportedAsSuch() = runTest {
        val state = viewModel().uiState.value

        assertTrue(state.isEmpty)
        assertTrue(state.entries.isEmpty())
    }

    @Test
    fun theFirstEmissionEndsTheLoadingState() = runTest {
        // Le dispatcher unconfined a déjà livré la première liste : un état
        // encore « en chargement » signifierait que le drapeau ne retombe pas.
        assertTrue(!viewModel().uiState.value.isLoading)
    }

    @Test
    fun entriesArriveMostRecentFirst() = runTest {
        val model = viewModel()

        repository.record(TunnelState.DISABLED, TunnelState.ENABLED, RuleId("mobile-network"))
        clock.advanceBy(5_000)
        repository.record(TunnelState.ENABLED, TunnelState.DISABLED, RuleId("airplane-mode"))

        val entries = model.uiState.value.entries
        assertEquals(RuleId("airplane-mode"), entries.first().ruleId)
        assertEquals(RuleId("mobile-network"), entries.last().ruleId)
        assertTrue(!model.uiState.value.isEmpty)
    }

    @Test
    fun clearingEmptiesTheDisplayedList() = runTest {
        val model = viewModel()
        repository.record(TunnelState.DISABLED, TunnelState.ENABLED, RuleId("a"))

        model.clear()

        assertTrue(model.uiState.value.isEmpty)
    }
}
