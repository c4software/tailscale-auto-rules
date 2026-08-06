package fr.vbrosseau.tailscaleautorules.presentation.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val toggles = mutableListOf<Pair<String, Boolean>>()
    private var permissionRequests = 0
    private var batteryClicks = 0

    private fun show(uiState: SettingsUiState) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                SettingsScreen(
                    uiState = uiState,
                    onServiceEnabledChange = { toggles += "service" to it },
                    onStartOnBootChange = { toggles += "boot" to it },
                    onPersistentNotificationChange = { toggles += "notification" to it },
                    onVerboseLoggingChange = { toggles += "logging" to it },
                    onRequestNotificationPermission = { permissionRequests++ },
                    onOpenBatterySettings = { batteryClicks++ },
                )
            }
        }
    }

    @Test
    fun everyPreferenceIsReflectedByItsSwitch() {
        show(
            SettingsUiState(
                settings = AppSettings(
                    isServiceEnabled = true,
                    startOnBoot = false,
                    showPersistentNotification = true,
                    verboseLogging = false,
                ),
            ),
        )

        composeRule.onNodeWithTag(SettingsTestTags.SERVICE).assertIsOn()
        composeRule.onNodeWithTag(SettingsTestTags.START_ON_BOOT).assertIsOff()
        composeRule.onNodeWithTag(SettingsTestTags.NOTIFICATION).assertIsOn()
        composeRule.onNodeWithTag(SettingsTestTags.LOGGING).assertIsOff()
    }

    @Test
    fun togglingASwitchReportsTheNewValue() {
        show(SettingsUiState(settings = AppSettings(verboseLogging = false)))

        composeRule.onNodeWithTag(SettingsTestTags.LOGGING).performClick()

        assertEquals(listOf("logging" to true), toggles)
    }

    @Test
    fun aDisabledAutomationIsAnnounced() {
        // Les autres réglages deviennent sans effet : le dire vaut mieux que de
        // laisser l'utilisateur chercher.
        show(SettingsUiState(settings = AppSettings(isServiceEnabled = false)))

        composeRule.onNodeWithTag(SettingsTestTags.DISABLED_NOTICE).assertIsDisplayed()
    }

    @Test
    fun noNoticeWhenAutomationIsActive() {
        show(SettingsUiState(settings = AppSettings(isServiceEnabled = true)))

        composeRule.onNodeWithTag(SettingsTestTags.DISABLED_NOTICE).assertDoesNotExist()
    }

    @Test
    fun thePermissionIsOnlyOfferedOnceTheOptionIsEnabled() {
        show(
            SettingsUiState(
                settings = AppSettings(showPersistentNotification = true),
                canNotify = false,
            ),
        )

        composeRule.onNodeWithTag(SettingsTestTags.NOTIFICATION_PERMISSION).assertIsDisplayed()
        composeRule.onNodeWithTag("${SettingsTestTags.NOTIFICATION_PERMISSION}:action")
            .performClick()

        assertEquals(1, permissionRequests)
    }

    @Test
    fun nothingIsAskedWhenTheOptionIsOff() {
        show(
            SettingsUiState(
                settings = AppSettings(showPersistentNotification = false),
                canNotify = false,
            ),
        )

        composeRule.onNodeWithTag(SettingsTestTags.NOTIFICATION_PERMISSION).assertDoesNotExist()
    }

    @Test
    fun theBatteryHintAppearsOnlyWhenNotExempted() {
        show(SettingsUiState(isIgnoringBatteryOptimizations = false))

        // L'écran défile : la carte est en bas de liste, hors du viewport de
        // test tant qu'on n'y a pas amené le regard.
        composeRule.onNodeWithTag(SettingsTestTags.BATTERY)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("${SettingsTestTags.BATTERY}:action").performClick()

        assertEquals(1, batteryClicks)
    }

    @Test
    fun noBatteryHintWhenAlreadyExempted() {
        show(SettingsUiState(isIgnoringBatteryOptimizations = true))

        composeRule.onNodeWithTag(SettingsTestTags.BATTERY).assertDoesNotExist()
    }

    @Test
    fun theVersionIsDisplayed() {
        show(SettingsUiState(versionName = "1.2.3"))

        composeRule.onNodeWithTag(SettingsTestTags.VERSION)
            .performScrollTo()
            .assertTextEquals("Version 1.2.3")
    }
}
