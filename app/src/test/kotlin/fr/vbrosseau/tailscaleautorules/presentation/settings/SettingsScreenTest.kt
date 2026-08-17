package fr.vbrosseau.tailscaleautorules.presentation.settings

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import fr.vbrosseau.tailscaleautorules.presentation.LoadingTestTags
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
    private var backgroundLocationRequests = 0
    private var batteryClicks = 0
    private var replayClicks = 0

    private fun show(uiState: SettingsUiState) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                SettingsScreen(
                    uiState = uiState,
                    onServiceEnabledChange = { toggles += "service" to it },
                    onLearningEnabledChange = { toggles += "learning" to it },
                    onStartOnBootChange = { toggles += "boot" to it },
                    onVerboseLoggingChange = { toggles += "logging" to it },
                    onRequestNotificationPermission = { permissionRequests++ },
                    onRequestBackgroundLocation = { backgroundLocationRequests++ },
                    onOpenBatterySettings = { batteryClicks++ },
                    onReplayOnboarding = { replayClicks++ },
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
                    verboseLogging = false,
                ),
            ),
        )

        composeRule.onNodeWithTag(SettingsTestTags.SERVICE).assertIsOn()
        composeRule.onNodeWithTag(SettingsTestTags.START_ON_BOOT).assertIsOff()
        composeRule.onNodeWithTag(SettingsTestTags.LOGGING).performScrollTo().assertIsOff()
    }

    @Test
    fun theNotificationIsExplainedRatherThanOffered() {
        // Elle est imposée par Android au service qui observe le réseau. Un
        // interrupteur — même grisé — laisserait croire à un choix inexistant,
        // et coché-désactivé il se lit presque comme éteint.
        show(SettingsUiState(settings = AppSettings(isServiceEnabled = true)))

        composeRule.onNodeWithTag(SettingsTestTags.NOTIFICATION)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.NOTIFICATION).assertHasNoClickAction()
    }

    @Test
    fun nothingIsExplainedWhenTheAutomationIsOff() {
        show(SettingsUiState(settings = AppSettings(isServiceEnabled = false)))

        composeRule.onNodeWithTag(SettingsTestTags.NOTIFICATION).assertDoesNotExist()
    }

    @Test
    fun togglingASwitchReportsTheNewValue() {
        show(SettingsUiState(settings = AppSettings(verboseLogging = false)))

        composeRule.onNodeWithTag(SettingsTestTags.LOGGING).performScrollTo().performClick()

        assertEquals(listOf("logging" to true), toggles)
    }

    @Test
    fun togglingTheLearningSwitchReportsTheNewValue() {
        show(SettingsUiState(settings = AppSettings(isLearningEnabled = true)))

        composeRule.onNodeWithTag(SettingsTestTags.LEARNING).performScrollTo().assertIsOn().performClick()

        assertEquals(listOf("learning" to false), toggles)
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
    fun thePermissionIsOfferedWhileTheAutomationIsActive() {
        show(
            SettingsUiState(
                settings = AppSettings(isServiceEnabled = true),
                canNotify = false,
            ),
        )

        composeRule.onNodeWithTag(SettingsTestTags.NOTIFICATION_PERMISSION)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("${SettingsTestTags.NOTIFICATION_PERMISSION}:action")
            .performClick()

        assertEquals(1, permissionRequests)
    }

    @Test
    fun nothingIsAskedWhenTheAutomationIsOff() {
        show(
            SettingsUiState(
                // Sans automatisation, aucune notification n'est affichée : en
                // réclamer la permission serait demander à l'utilisateur un
                // droit dont l'application ne ferait rien.
                settings = AppSettings(isServiceEnabled = false),
                canNotify = false,
            ),
        )

        composeRule.onNodeWithTag(SettingsTestTags.NOTIFICATION_PERMISSION).assertDoesNotExist()
    }

    @Test
    fun theBackgroundLocationIsOfferedWhenTheBootStartWouldBeBlocked() {
        // Localisation accordée « pendant l'utilisation » seulement et
        // démarrage au boot demandé : Android refusera le service au boot,
        // la carte propose de passer en « Toujours autoriser ».
        show(
            SettingsUiState(
                settings = AppSettings(isServiceEnabled = true, startOnBoot = true),
                canReadSsid = true,
                canReadSsidInBackground = false,
            ),
        )

        composeRule.onNodeWithTag(SettingsTestTags.BACKGROUND_LOCATION)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("${SettingsTestTags.BACKGROUND_LOCATION}:action")
            .performClick()

        assertEquals(1, backgroundLocationRequests)
    }

    @Test
    fun nothingIsOfferedOnceTheBackgroundLocationIsGranted() {
        show(
            SettingsUiState(
                settings = AppSettings(isServiceEnabled = true, startOnBoot = true),
                canReadSsid = true,
                canReadSsidInBackground = true,
            ),
        )

        composeRule.onNodeWithTag(SettingsTestTags.BACKGROUND_LOCATION).assertDoesNotExist()
    }

    @Test
    fun nothingIsOfferedWithoutTheForegroundLocation() {
        // Sans localisation de premier plan, le service part sans le type en
        // cause : rien ne bloque le boot, et la permission d'arrière-plan ne
        // s'accorde de toute façon qu'au-dessus de celle de premier plan.
        show(
            SettingsUiState(
                settings = AppSettings(isServiceEnabled = true, startOnBoot = true),
                canReadSsid = false,
                canReadSsidInBackground = false,
            ),
        )

        composeRule.onNodeWithTag(SettingsTestTags.BACKGROUND_LOCATION).assertDoesNotExist()
    }

    @Test
    fun nothingIsOfferedWhenTheBootStartIsOff() {
        // La permission ne sert qu'au redémarrage du terminal : sans
        // démarrage automatique, la demander serait un droit sans usage.
        show(
            SettingsUiState(
                settings = AppSettings(isServiceEnabled = true, startOnBoot = false),
                canReadSsid = true,
                canReadSsidInBackground = false,
            ),
        )

        composeRule.onNodeWithTag(SettingsTestTags.BACKGROUND_LOCATION).assertDoesNotExist()
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
    fun replayingTheOnboardingGoesThroughItsButton() {
        show(SettingsUiState())

        composeRule.onNodeWithTag(SettingsTestTags.REPLAY_ONBOARDING)
            .performScrollTo()
            .performClick()

        assertEquals(1, replayClicks)
    }

    @Test
    fun theVersionIsDisplayed() {
        show(SettingsUiState(versionName = "1.2.3"))

        composeRule.onNodeWithTag(SettingsTestTags.VERSION)
            .performScrollTo()
            .assertTextEquals("Version 1.2.3")
    }

    @Test
    fun whileLoadingOnlyTheIndicatorIsShown() {
        // Les valeurs d'usine d'`AppSettings` sont indiscernables de réglages
        // réels : afficher les interrupteurs avant la première lecture de
        // DataStore les ferait sauter sous les yeux de l'utilisateur.
        show(SettingsUiState(isLoading = true))

        composeRule.onNodeWithTag(LoadingTestTags.INDICATOR).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SERVICE).assertDoesNotExist()
    }
}
