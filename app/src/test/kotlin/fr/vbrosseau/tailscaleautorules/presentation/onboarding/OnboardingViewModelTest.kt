package fr.vbrosseau.tailscaleautorules.presentation.onboarding

import fr.vbrosseau.tailscaleautorules.domain.repository.FakeSettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import fr.vbrosseau.tailscaleautorules.presentation.MainDispatcherRule
import fr.vbrosseau.tailscaleautorules.presentation.keepCollecting
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository()

    private fun TestScope.viewModel() = OnboardingViewModel(settings).also { keepCollecting(it.uiState) }

    @Test
    fun theJourneyShowsOnAFreshInstall() = runTest {
        val state = viewModel().uiState.value

        assertTrue(!state.isLoading)
        assertTrue(!state.isDone)
    }

    @Test
    fun aClosedJourneyNeverComesBack() = runTest {
        settings.updateAppSettings { it.copy(isOnboardingDone = true) }

        assertTrue(viewModel().uiState.value.isDone)
    }

    @Test
    fun finishingRecordsTheChoiceAndClosesTheJourney() = runTest {
        val model = viewModel()

        model.finish(learningEnabled = false)

        val stored = settings.currentAppSettings()
        assertTrue(stored.isOnboardingDone)
        assertTrue(!stored.isLearningEnabled)
        assertTrue(stored.isLearningPrompted, "La question de l'accueil ne doit jamais redoubler le parcours.")
        assertTrue(model.uiState.value.isDone)
    }

    @Test
    fun theJourneyIsClosedWhateverTheAnswer() = runTest {
        val model = viewModel()

        model.finish(learningEnabled = true)

        val stored = settings.currentAppSettings()
        assertTrue(stored.isOnboardingDone)
        assertTrue(stored.isLearningEnabled)
    }

    @Test
    fun onboardingStartsUndoneByDefault() {
        assertTrue(!AppSettings.Defaults.isOnboardingDone)
    }
}
