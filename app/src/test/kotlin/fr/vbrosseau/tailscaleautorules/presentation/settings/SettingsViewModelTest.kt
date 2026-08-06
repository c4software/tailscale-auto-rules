package fr.vbrosseau.tailscaleautorules.presentation.settings

import fr.vbrosseau.tailscaleautorules.domain.repository.FakeSettingsRepository
import fr.vbrosseau.tailscaleautorules.presentation.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()
    private fun viewModel() = SettingsViewModel(repository)

    @Test
    fun theInitialStateCarriesTheStoredPreferences() = runTest {
        val settings = viewModel().uiState.value.settings

        assertTrue(settings.isServiceEnabled)
        assertTrue(settings.startOnBoot)
        assertTrue(!settings.showPersistentNotification)
        assertTrue(!settings.verboseLogging)
    }

    @Test
    fun eachToggleAffectsOnlyItsOwnPreference() = runTest {
        val model = viewModel()

        model.setPersistentNotification(true)

        val settings = model.uiState.value.settings
        assertTrue(settings.showPersistentNotification)
        assertTrue(settings.isServiceEnabled)
        assertTrue(settings.startOnBoot)
        assertTrue(!settings.verboseLogging)
    }

    @Test
    fun everyToggleIsWiredToItsPreference() = runTest {
        val model = viewModel()

        model.setServiceEnabled(false)
        model.setStartOnBoot(false)
        model.setPersistentNotification(true)
        model.setVerboseLogging(true)

        val settings = model.uiState.value.settings
        assertTrue(!settings.isServiceEnabled)
        assertTrue(!settings.startOnBoot)
        assertTrue(settings.showPersistentNotification)
        assertTrue(settings.verboseLogging)
    }

    @Test
    fun aChangeMadeElsewhereIsReflected() = runTest {
        val model = viewModel()

        repository.updateAppSettings { it.copy(verboseLogging = true) }

        assertTrue(model.uiState.value.settings.verboseLogging)
    }
}
