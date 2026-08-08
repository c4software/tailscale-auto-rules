package fr.vbrosseau.tailscaleautorules.presentation.blacklist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import fr.vbrosseau.tailscaleautorules.domain.model.BlacklistedSsid
import fr.vbrosseau.tailscaleautorules.presentation.LoadingTestTags
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class BlacklistScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val recordedAdds = mutableListOf<String>()
    private val recordedRenames = mutableListOf<Pair<Long, String>>()
    private val recordedRemovals = mutableListOf<Long>()
    private var quickAddCount = 0
    private var locationRequests = 0
    private val recordedMobileRuleChanges = mutableListOf<Boolean>()

    private fun show(uiState: BlacklistUiState) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                BlacklistScreen(
                    uiState = uiState,
                    onAdd = { recordedAdds += it },
                    onRename = { id, value -> recordedRenames += id to value },
                    onRemove = { recordedRemovals += it },
                    onAddCurrentSsid = { quickAddCount++ },
                    onDismissError = {},
                    onMobileRuleChange = { recordedMobileRuleChanges += it },
                    onRequestLocationPermission = { locationRequests++ },
                )
            }
        }
    }

    private val twoEntries = listOf(
        BlacklistedSsid(id = 1, value = "Maison"),
        BlacklistedSsid(id = 2, value = "Bureau"),
    )

    /**
     * Amène l'élément dans la fenêtre avant d'interagir : l'écran est une
     * liste défilante, et un clic hors de la fenêtre se perd en silence.
     */
    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag(BlacklistTestTags.LIST).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun anEmptyListIsExplainedRatherThanLeftBlank() {
        show(BlacklistUiState())

        scrollTo(BlacklistTestTags.EMPTY)
        composeRule.onNodeWithTag(BlacklistTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun eachEntryIsDisplayed() {
        show(BlacklistUiState(entries = twoEntries))

        scrollTo(BlacklistTestTags.entry(1))
        composeRule.onNodeWithTag(BlacklistTestTags.entry(1)).assertTextEquals("Maison")
        scrollTo(BlacklistTestTags.entry(2))
        composeRule.onNodeWithTag(BlacklistTestTags.entry(2)).assertTextEquals("Bureau")
        composeRule.onNodeWithTag(BlacklistTestTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun addingASsidGoesThroughTheDialog() {
        show(BlacklistUiState())

        composeRule.onNodeWithTag(BlacklistTestTags.ADD).performClick()
        composeRule.onNodeWithTag(BlacklistTestTags.DIALOG_FIELD).performTextInput("Aéroport")
        composeRule.onNodeWithTag(BlacklistTestTags.DIALOG_CONFIRM).performClick()

        assertEquals(listOf("Aéroport"), recordedAdds)
    }

    @Test
    fun theDialogClosesAfterConfirmation() {
        show(BlacklistUiState())

        composeRule.onNodeWithTag(BlacklistTestTags.ADD).performClick()
        composeRule.onNodeWithTag(BlacklistTestTags.DIALOG_CONFIRM).performClick()

        composeRule.onNodeWithTag(BlacklistTestTags.DIALOG_FIELD).assertDoesNotExist()
    }

    @Test
    fun renamingPrefillsTheCurrentValue() {
        show(BlacklistUiState(entries = twoEntries))

        scrollTo(BlacklistTestTags.rename(1))
        composeRule.onNodeWithTag(BlacklistTestTags.rename(1)).performClick()

        composeRule.onNodeWithTag(BlacklistTestTags.DIALOG_FIELD).assertTextEquals("Maison")
    }

    @Test
    fun renamingReportsTheEntryIdentity() {
        show(BlacklistUiState(entries = twoEntries))

        scrollTo(BlacklistTestTags.rename(2))
        composeRule.onNodeWithTag(BlacklistTestTags.rename(2)).performClick()
        composeRule.onNodeWithTag(BlacklistTestTags.DIALOG_FIELD).performTextClearance()
        composeRule.onNodeWithTag(BlacklistTestTags.DIALOG_FIELD).performTextInput("Bureau Fibre")
        composeRule.onNodeWithTag(BlacklistTestTags.DIALOG_CONFIRM).performClick()

        assertEquals(listOf(2L to "Bureau Fibre"), recordedRenames)
    }

    @Test
    fun removingReportsTheEntryIdentity() {
        show(BlacklistUiState(entries = twoEntries))

        scrollTo(BlacklistTestTags.remove(2))
        composeRule.onNodeWithTag(BlacklistTestTags.remove(2)).performClick()

        assertEquals(listOf(2L), recordedRemovals)
    }

    @Test
    fun theQuickAddAppearsOnlyWhenItCanSucceed() {
        show(BlacklistUiState(currentSsid = "Aéroport"))

        composeRule.onNodeWithTag(BlacklistTestTags.ADD_CURRENT).performClick()

        assertEquals(1, quickAddCount)
    }

    @Test
    fun theQuickAddIsAbsentWithoutASsid() {
        show(BlacklistUiState(currentSsid = null))

        composeRule.onNodeWithTag(BlacklistTestTags.ADD_CURRENT).assertDoesNotExist()
    }

    @Test
    fun theQuickAddIsAbsentWhenTheNetworkIsAlreadyListed() {
        show(
            BlacklistUiState(
                entries = twoEntries,
                currentSsid = "Maison",
                isCurrentSsidAlreadyListed = true,
            ),
        )

        composeRule.onNodeWithTag(BlacklistTestTags.ADD_CURRENT).assertDoesNotExist()
    }

    @Test
    fun theMobileRuleSwitchReflectsTheState() {
        show(BlacklistUiState(isMobileRuleEnabled = false))

        composeRule.onNodeWithTag(BlacklistTestTags.MOBILE_RULE)
            .assertIsDisplayed()
            .assertIsOff()
    }

    @Test
    fun togglingTheMobileRuleReportsTheNewValue() {
        show(BlacklistUiState(isMobileRuleEnabled = true))

        composeRule.onNodeWithTag(BlacklistTestTags.MOBILE_RULE).assertIsOn().performClick()

        assertEquals(listOf(false), recordedMobileRuleChanges)
    }

    @Test
    fun anErrorIsShownAndNamed() {
        show(BlacklistUiState(error = BlacklistError.DUPLICATE))

        composeRule.onNodeWithTag(BlacklistTestTags.ERROR).assertIsDisplayed()
    }

    @Test
    fun noErrorCardWithoutError() {
        show(BlacklistUiState())

        composeRule.onNodeWithTag(BlacklistTestTags.ERROR).assertDoesNotExist()
    }

    @Test
    fun thePermissionIsExplainedBeforeBeingRequested() {
        // Android impose la localisation pour lire un SSID sans le dire :
        // une demande non expliquée serait à juste titre refusée.
        show(BlacklistUiState(canReadSsid = false))

        scrollTo(BlacklistTestTags.LOCATION_RATIONALE)
        composeRule.onNodeWithTag(BlacklistTestTags.LOCATION_RATIONALE).assertIsDisplayed()
        scrollTo(BlacklistTestTags.LOCATION_GRANT)
        composeRule.onNodeWithTag(BlacklistTestTags.LOCATION_GRANT).performClick()

        assertEquals(1, locationRequests)
    }

    @Test
    fun nothingIsExplainedOnceTheSsidCanBeRead() {
        show(BlacklistUiState(canReadSsid = true))

        composeRule.onNodeWithTag(BlacklistTestTags.LOCATION_RATIONALE).assertDoesNotExist()
    }

    @Test
    fun whileLoadingOnlyTheIndicatorIsShown() {
        // Sans ce garde-fou, la liste encore vide s'afficherait comme « aucun
        // réseau de confiance » le temps de la première lecture de Room.
        show(BlacklistUiState(isLoading = true))

        composeRule.onNodeWithTag(LoadingTestTags.INDICATOR).assertIsDisplayed()
        composeRule.onNodeWithTag(BlacklistTestTags.EMPTY).assertDoesNotExist()
        composeRule.onNodeWithTag(BlacklistTestTags.ADD).assertDoesNotExist()
    }
}
