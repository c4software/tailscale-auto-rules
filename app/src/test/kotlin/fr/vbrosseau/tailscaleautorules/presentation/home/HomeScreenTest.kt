package fr.vbrosseau.tailscaleautorules.presentation.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
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

    private fun show(uiState: HomeUiState, onSynchronize: () -> Unit = {}) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                HomeScreen(uiState = uiState, onSynchronize = onSynchronize)
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
}
