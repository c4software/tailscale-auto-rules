package fr.vbrosseau.tailscaleautorules.presentation.settings

import fr.vbrosseau.tailscaleautorules.domain.repository.FakeSettingsRepository
import fr.vbrosseau.tailscaleautorules.presentation.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()
    private val systemStatus = FakeSystemStatus()

    private fun viewModel() = SettingsViewModel(repository, systemStatus)

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

    @Test
    fun theVersionComesFromThePlatform() = runTest {
        systemStatus.versionName = "1.2.3"

        assertEquals("1.2.3", viewModel().uiState.value.versionName)
    }

    @Test
    fun thePermissionIsOnlyMissingOnceTheOptionIsEnabled() = runTest {
        // Sans l'option, l'absence de permission n'est pas un manque : rien ne
        // doit être demandé à l'utilisateur.
        systemStatus.notificationsAllowed = false
        val model = viewModel()

        assertTrue(!model.uiState.value.needsNotificationPermission)

        model.setPersistentNotification(true)

        assertTrue(model.uiState.value.needsNotificationPermission)
    }

    @Test
    fun anAllowedNotificationNeedsNothing() = runTest {
        val model = viewModel()
        model.setPersistentNotification(true)

        assertTrue(!model.uiState.value.needsNotificationPermission)
    }

    @Test
    fun aPermissionGrantedOutsideTheApplicationIsPickedUpOnRefresh() = runTest {
        // La permission se donne dans les réglages système : sans reconstat au
        // retour, l'écran resterait indéfiniment sur un état périmé.
        systemStatus.notificationsAllowed = false
        val model = viewModel()
        model.setPersistentNotification(true)
        assertTrue(model.uiState.value.needsNotificationPermission)

        systemStatus.notificationsAllowed = true
        model.refreshSystemStatus()

        assertTrue(!model.uiState.value.needsNotificationPermission)
    }

    @Test
    fun theBatteryExemptionIsRereadOnRefresh() = runTest {
        systemStatus.batteryExempted = false
        val model = viewModel()
        assertTrue(!model.uiState.value.isIgnoringBatteryOptimizations)

        systemStatus.batteryExempted = true
        model.refreshSystemStatus()

        assertTrue(model.uiState.value.isIgnoringBatteryOptimizations)
    }
}
