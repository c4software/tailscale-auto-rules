package fr.vbrosseau.tailscaleautorules.presentation.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.usecase.ManualOverride
import fr.vbrosseau.tailscaleautorules.presentation.LoadingTestTags
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * L'écran est sans état : il se teste en lui donnant un [HomeUiState] et en
 * vérifiant ce qu'il affiche. Aucun ViewModel, aucune injection.
 */
@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        uiState: HomeUiState,
        onSynchronize: () -> Unit = {},
        onDisableAutomation: () -> Unit = {},
        onChooseLearning: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                HomeScreen(
                    uiState = uiState,
                    onSynchronize = onSynchronize,
                    onDisableAutomation = onDisableAutomation,
                    onChooseLearning = onChooseLearning,
                )
            }
        }
    }

    @Test
    fun theTunnelStateIsDisplayed() {
        show(HomeUiState(tunnelState = TunnelState.ENABLED))

        composeRule.onNodeWithTag(HomeTestTags.TUNNEL_STATE).assertTextEquals("Activé")
    }

    @Test
    fun anUnknownStateIsNamedRatherThanShownAsDisabled() {
        show(HomeUiState(tunnelState = TunnelState.UNKNOWN))

        composeRule.onNodeWithTag(HomeTestTags.TUNNEL_STATE).assertTextEquals("Indéterminé")
    }

    @Test
    fun aManualOverrideShowsItsCardAndNamesTheContradictedRule() {
        show(
            HomeUiState(
                tunnelState = TunnelState.ENABLED,
                transport = NetworkTransport.WIFI,
                ssid = "Maison",
                manualOverride = ManualOverride(
                    observedState = TunnelState.ENABLED,
                    ruleId = RuleId("blacklisted-wifi"),
                ),
            ),
        )

        composeRule.onNodeWithTag(HomeTestTags.MANUAL_OVERRIDE).assertIsDisplayed()
    }

    @Test
    fun withoutManualOverrideTheCardStaysAbsent() {
        show(HomeUiState(tunnelState = TunnelState.ENABLED))

        composeRule.onNodeWithTag(HomeTestTags.MANUAL_OVERRIDE).assertDoesNotExist()
    }

    @Test
    fun theNetworkTypeIsDisplayed() {
        show(HomeUiState(transport = NetworkTransport.CELLULAR))

        composeRule.onNodeWithTag(HomeTestTags.NETWORK).assertTextEquals("Réseau mobile")
    }

    @Test
    fun theSsidIsShownOnWifi() {
        show(HomeUiState(transport = NetworkTransport.WIFI, ssid = "Aéroport"))

        composeRule.onNodeWithTag(HomeTestTags.SSID).assertTextEquals("Aéroport")
    }

    @Test
    fun anUnavailableSsidIsStatedExplicitlyOnWifi() {
        show(HomeUiState(transport = NetworkTransport.WIFI, ssid = null))

        composeRule.onNodeWithTag(HomeTestTags.SSID).assertTextEquals("SSID indisponible")
    }

    @Test
    fun noSsidLineIsShownOutsideWifi() {
        // Afficher « indisponible » en 4G laisserait croire à un défaut de
        // permission là où il n'y a simplement pas de SSID.
        show(HomeUiState(transport = NetworkTransport.CELLULAR, ssid = null))

        composeRule.onNodeWithTag(HomeTestTags.SSID).assertDoesNotExist()
    }

    @Test
    fun anEmptyJournalIsStatedRatherThanLeftBlank() {
        show(HomeUiState(lastChange = null))

        composeRule.onNodeWithTag(HomeTestTags.LAST_CHANGE)
            .assertTextEquals("Aucun changement enregistré")
    }

    @Test
    fun theLastChangeReadsAsATransition() {
        show(
            HomeUiState(
                lastChange = JournalEntry(
                    id = 1,
                    epochMillis = 0,
                    previousState = TunnelState.DISABLED,
                    newState = TunnelState.ENABLED,
                    ruleId = RuleId("mobile-network"),
                ),
            ),
        )

        composeRule.onNodeWithTag(HomeTestTags.LAST_CHANGE)
            .assertTextEquals("Désactivé → Activé")
    }

    @Test
    fun theSynchronizeButtonReportsClicks() {
        var clicks = 0
        show(HomeUiState(), onSynchronize = { clicks++ })

        composeRule.onNodeWithTag(HomeTestTags.SYNCHRONIZE).assertIsEnabled().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun theButtonIsDisabledWhileSynchronizing() {
        show(HomeUiState(isSynchronizing = true))

        composeRule.onNodeWithTag(HomeTestTags.SYNCHRONIZE).assertIsNotEnabled()
    }

    @Test
    fun aMissingClientIsAnnouncedProminently() {
        show(HomeUiState(isTailscaleInstalled = false))

        composeRule.onNodeWithTag(HomeTestTags.TAILSCALE_MISSING).assertIsDisplayed()
    }

    @Test
    fun nothingIsAnnouncedWhenTheClientIsPresent() {
        show(HomeUiState(isTailscaleInstalled = true))

        composeRule.onNodeWithTag(HomeTestTags.TAILSCALE_MISSING).assertDoesNotExist()
    }

    @Test
    fun disablingTheAutomationGoesThroughTheButton() {
        var disableCount = 0
        show(HomeUiState(isAutomationEnabled = true), onDisableAutomation = { disableCount++ })

        composeRule.onNodeWithTag(HomeTestTags.DISABLE_AUTOMATION).performClick()

        assertEquals(1, disableCount)
        composeRule.onNodeWithTag(HomeTestTags.AUTOMATION_DISABLED).assertDoesNotExist()
    }

    @Test
    fun aDisabledAutomationIsStatedRatherThanLeftBlank() {
        show(HomeUiState(isAutomationEnabled = false))

        composeRule.onNodeWithTag(HomeTestTags.AUTOMATION_DISABLED).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.DISABLE_AUTOMATION).assertDoesNotExist()
    }

    @Test
    fun theLearningPromptOffersBothAnswers() {
        val answers = mutableListOf<Boolean>()
        show(HomeUiState(isLearningPromptVisible = true), onChooseLearning = { answers += it })

        composeRule.onNodeWithTag(HomeTestTags.LEARNING_PROMPT).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.LEARNING_DECLINE).performClick()
        composeRule.onNodeWithTag(HomeTestTags.LEARNING_ACCEPT).performClick()

        assertEquals(listOf(false, true), answers)
    }

    @Test
    fun noPromptOnceTheQuestionWasAnswered() {
        show(HomeUiState(isLearningPromptVisible = false))

        composeRule.onNodeWithTag(HomeTestTags.LEARNING_PROMPT).assertDoesNotExist()
    }

    @Test
    fun theOverrideCardAnnouncesTheMemorization() {
        // Le texte suit le sort réel du geste : mémorisé, il l'annonce au lieu
        // de promettre que les règles reprendront la main.
        show(
            HomeUiState(
                manualOverride = ManualOverride(TunnelState.ENABLED, RuleId("blacklisted-wifi")),
                willMemorizeManualGesture = true,
            ),
        )

        composeRule.onNode(
            hasAnyAncestor(hasTestTag(HomeTestTags.MANUAL_OVERRIDE)) and
                hasText("mémorisé", substring = true),
        ).assertExists()
    }

    @Test
    fun whileLoadingOnlyTheIndicatorIsShown() {
        // Les valeurs par défaut — tunnel indéterminé, aucun réseau — se
        // liraient comme des données : elles ne doivent pas apparaître.
        show(HomeUiState(isLoading = true))

        composeRule.onNodeWithTag(LoadingTestTags.INDICATOR).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.TUNNEL_STATE).assertDoesNotExist()
        composeRule.onNodeWithTag(HomeTestTags.SYNCHRONIZE).assertDoesNotExist()
    }
}
