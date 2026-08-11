package fr.vbrosseau.tailscaleautorules.presentation.networks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreference
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.presentation.LoadingTestTags
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class NetworksScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val recordedAdds = mutableListOf<Pair<String, Boolean>>()
    private val recordedRenames = mutableListOf<Pair<Long, String>>()
    private val recordedRemovals = mutableListOf<Long>()
    private val recordedToggles = mutableListOf<Pair<Long, Boolean>>()
    private var quickAddCount = 0
    private var locationRequests = 0
    private val recordedMobileRuleChanges = mutableListOf<Boolean>()

    private fun show(uiState: NetworksUiState) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                NetworksScreen(
                    uiState = uiState,
                    onAdd = { ssid, enabled -> recordedAdds += ssid to enabled },
                    onRename = { id, value -> recordedRenames += id to value },
                    onRemove = { recordedRemovals += it },
                    onSetPreferenceEnabled = { preference, enabled ->
                        recordedToggles += preference.id to enabled
                    },
                    onAddCurrentSsid = { quickAddCount++ },
                    onDismissError = {},
                    onMobileRuleChange = { recordedMobileRuleChanges += it },
                    onRequestLocationPermission = { locationRequests++ },
                )
            }
        }
    }

    private val twoPreferences = listOf(
        NetworkPreference(
            id = 1,
            key = NetworkPreferenceKey("wifi:maison"),
            ssid = "Maison",
            desiredState = TunnelState.DISABLED,
            epochMillis = 0,
        ),
        NetworkPreference(
            id = 2,
            key = NetworkPreferenceKey.Cellular,
            ssid = null,
            desiredState = TunnelState.ENABLED,
            epochMillis = 0,
        ),
    )

    /**
     * Amène l'élément dans la fenêtre avant d'interagir : l'écran est une
     * liste défilante, et un clic hors de la fenêtre se perd en silence.
     */
    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag(NetworksTestTags.LIST).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun anEmptyListIsExplainedRatherThanLeftBlank() {
        show(NetworksUiState())

        scrollTo(NetworksTestTags.EMPTY)
        composeRule.onNodeWithTag(NetworksTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun eachPreferenceShowsItsWillOnItsSwitch() {
        show(NetworksUiState(preferences = twoPreferences))

        scrollTo(NetworksTestTags.preference(1))
        composeRule.onNodeWithTag(NetworksTestTags.preferenceSwitch(1)).assertIsOff()
        scrollTo(NetworksTestTags.preference(2))
        composeRule.onNodeWithTag(NetworksTestTags.preferenceSwitch(2)).assertIsOn()
        composeRule.onNodeWithTag(NetworksTestTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun togglingAPreferenceReportsItsIdentityAndTheNewWill() {
        show(NetworksUiState(preferences = twoPreferences))

        scrollTo(NetworksTestTags.preferenceSwitch(1))
        composeRule.onNodeWithTag(NetworksTestTags.preferenceSwitch(1)).performClick()

        assertEquals(listOf(1L to true), recordedToggles)
    }

    @Test
    fun swipingAPreferenceAsksForItsRemoval() {
        show(NetworksUiState(preferences = twoPreferences))

        scrollTo(NetworksTestTags.preference(1))
        composeRule.onNodeWithTag(NetworksTestTags.preference(1))
            .performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(listOf(1L), recordedRemovals)
    }

    @Test
    fun addingANetworkGoesThroughTheDialogWithItsWill() {
        show(NetworksUiState())

        composeRule.onNodeWithTag(NetworksTestTags.ADD).performClick()
        composeRule.onNodeWithTag(NetworksTestTags.DIALOG_FIELD).performTextInput("Aéroport")
        composeRule.onNodeWithTag(NetworksTestTags.DIALOG_SWITCH).performClick()
        composeRule.onNodeWithTag(NetworksTestTags.DIALOG_CONFIRM).performClick()

        assertEquals(listOf("Aéroport" to true), recordedAdds)
    }

    @Test
    fun theDialogDefaultsToATrustedNetwork() {
        // Déclarer un réseau, c'est d'abord le geste de confiance d'hier.
        show(NetworksUiState())

        composeRule.onNodeWithTag(NetworksTestTags.ADD).performClick()
        composeRule.onNodeWithTag(NetworksTestTags.DIALOG_SWITCH).assertIsOff()
        composeRule.onNodeWithTag(NetworksTestTags.DIALOG_FIELD).performTextInput("Aéroport")
        composeRule.onNodeWithTag(NetworksTestTags.DIALOG_CONFIRM).performClick()

        assertEquals(listOf("Aéroport" to false), recordedAdds)
    }

    @Test
    fun theDialogClosesAfterConfirmation() {
        show(NetworksUiState())

        composeRule.onNodeWithTag(NetworksTestTags.ADD).performClick()
        composeRule.onNodeWithTag(NetworksTestTags.DIALOG_CONFIRM).performClick()

        composeRule.onNodeWithTag(NetworksTestTags.DIALOG_FIELD).assertDoesNotExist()
    }

    @Test
    fun tappingAWifiNameOpensTheRenameDialogPrefilled() {
        show(NetworksUiState(preferences = twoPreferences))

        scrollTo(NetworksTestTags.preference(1))
        composeRule.onNodeWithTag(NetworksTestTags.preferenceName(1)).performClick()
        composeRule.onNodeWithTag(NetworksTestTags.DIALOG_FIELD).performTextClearance()
        composeRule.onNodeWithTag(NetworksTestTags.DIALOG_FIELD).performTextInput("Maison Fibre")
        composeRule.onNodeWithTag(NetworksTestTags.DIALOG_CONFIRM).performClick()

        assertEquals(listOf(1L to "Maison Fibre"), recordedRenames)
    }

    @Test
    fun renamingHidesTheWillChoice() {
        // Au renommage, la volonté a déjà son interrupteur sur la carte.
        show(NetworksUiState(preferences = twoPreferences))

        scrollTo(NetworksTestTags.preference(1))
        composeRule.onNodeWithTag(NetworksTestTags.preferenceName(1)).performClick()

        composeRule.onNodeWithTag(NetworksTestTags.DIALOG_SWITCH).assertDoesNotExist()
    }

    @Test
    fun theQuickAddAppearsOnlyWhenItCanSucceed() {
        show(NetworksUiState(currentSsid = "Aéroport"))

        composeRule.onNodeWithTag(NetworksTestTags.ADD_CURRENT).performClick()

        assertEquals(1, quickAddCount)
    }

    @Test
    fun theQuickAddIsAbsentWithoutASsid() {
        show(NetworksUiState(currentSsid = null))

        composeRule.onNodeWithTag(NetworksTestTags.ADD_CURRENT).assertDoesNotExist()
    }

    @Test
    fun theQuickAddIsAbsentWhenTheNetworkIsAlreadyListed() {
        show(
            NetworksUiState(
                preferences = twoPreferences,
                currentSsid = "Maison",
                isCurrentSsidAlreadyListed = true,
            ),
        )

        composeRule.onNodeWithTag(NetworksTestTags.ADD_CURRENT).assertDoesNotExist()
    }

    @Test
    fun theMobileRuleSwitchReflectsTheState() {
        show(NetworksUiState(isMobileRuleEnabled = false))

        composeRule.onNodeWithTag(NetworksTestTags.MOBILE_RULE)
            .assertIsDisplayed()
            .assertIsOff()
    }

    @Test
    fun togglingTheMobileRuleReportsTheNewValue() {
        show(NetworksUiState(isMobileRuleEnabled = true))

        composeRule.onNodeWithTag(NetworksTestTags.MOBILE_RULE).assertIsOn().performClick()

        assertEquals(listOf(false), recordedMobileRuleChanges)
    }

    @Test
    fun anErrorIsDisplayedInItsCard() {
        show(NetworksUiState(error = NetworksError.DUPLICATE))

        scrollTo(NetworksTestTags.ERROR)
        composeRule.onNodeWithTag(NetworksTestTags.ERROR).assertIsDisplayed()
    }

    @Test
    fun noErrorCardWithoutError() {
        show(NetworksUiState())

        composeRule.onNodeWithTag(NetworksTestTags.ERROR).assertDoesNotExist()
    }

    @Test
    fun thePermissionIsExplainedBeforeBeingRequested() {
        // Android impose la localisation pour lire un SSID sans le dire :
        // une demande non expliquée serait à juste titre refusée.
        show(NetworksUiState(canReadSsid = false))

        scrollTo(NetworksTestTags.LOCATION_RATIONALE)
        composeRule.onNodeWithTag(NetworksTestTags.LOCATION_RATIONALE).assertIsDisplayed()
        scrollTo(NetworksTestTags.LOCATION_GRANT)
        composeRule.onNodeWithTag(NetworksTestTags.LOCATION_GRANT).performClick()

        assertEquals(1, locationRequests)
    }

    @Test
    fun nothingIsExplainedOnceTheSsidCanBeRead() {
        show(NetworksUiState(canReadSsid = true))

        composeRule.onNodeWithTag(NetworksTestTags.LOCATION_RATIONALE).assertDoesNotExist()
    }

    @Test
    fun whileLoadingOnlyTheIndicatorIsShown() {
        // Sans ce garde-fou, la liste encore vide s'afficherait comme « aucune
        // préférence » le temps de la première lecture de Room.
        show(NetworksUiState(isLoading = true))

        composeRule.onNodeWithTag(LoadingTestTags.INDICATOR).assertIsDisplayed()
        composeRule.onNodeWithTag(NetworksTestTags.EMPTY).assertDoesNotExist()
        composeRule.onNodeWithTag(NetworksTestTags.ADD).assertDoesNotExist()
    }
}
