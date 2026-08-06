package fr.vbrosseau.tailscaleautorules.presentation.journal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId
import java.util.Locale
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class JournalScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var clearCount = 0

    private fun show(uiState: JournalUiState) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                JournalScreen(
                    uiState = uiState,
                    onClear = { clearCount++ },
                    // Fuseau et langue fixés : le rendu ne dépend pas des
                    // réglages de la machine qui exécute les tests.
                    zoneId = ZoneId.of("Europe/Paris"),
                    locale = Locale.FRANCE,
                )
            }
        }
    }

    private val enabling = JournalEntry(
        id = 1,
        epochMillis = 1_770_000_000_000,
        previousState = TunnelState.DISABLED,
        newState = TunnelState.ENABLED,
        ruleId = RuleId("mobile-network"),
    )

    private val disabling = JournalEntry(
        id = 2,
        epochMillis = 1_770_003_600_000,
        previousState = TunnelState.ENABLED,
        newState = TunnelState.DISABLED,
        ruleId = RuleId("blacklisted-wifi"),
    )

    @Test
    fun anEmptyJournalIsStatedRatherThanLeftBlank() {
        show(JournalUiState())

        composeRule.onNodeWithTag(JournalTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(JournalTestTags.LIST).assertDoesNotExist()
    }

    @Test
    fun clearingIsNotOfferedWhenThereIsNothingToClear() {
        show(JournalUiState())

        composeRule.onNodeWithTag(JournalTestTags.CLEAR).assertDoesNotExist()
    }

    @Test
    fun eachEntryReadsAsATransition() {
        show(JournalUiState(entries = listOf(disabling, enabling)))

        composeRule.onNodeWithTag(JournalTestTags.transition(2))
            .assertTextEquals("Activé → Désactivé")
        composeRule.onNodeWithTag(JournalTestTags.transition(1))
            .assertTextEquals("Désactivé → Activé")
    }

    @Test
    fun eachEntryNamesTheRuleThatDecided() {
        show(JournalUiState(entries = listOf(disabling, enabling)))

        composeRule.onNodeWithTag(JournalTestTags.rule(2))
            .assertTextEquals("Wi-Fi de confiance")
        composeRule.onNodeWithTag(JournalTestTags.rule(1))
            .assertTextEquals("Réseau mobile")
    }

    @Test
    fun anUnknownRuleFallsBackInsteadOfBreakingTheScreen() {
        // Une entrée écrite par une version connaissant une règle depuis
        // retirée ne doit pas empêcher de consulter l'historique.
        show(JournalUiState(entries = listOf(enabling.copy(ruleId = RuleId("règle-disparue")))))

        composeRule.onNodeWithTag(JournalTestTags.rule(1)).assertTextEquals("Règle inconnue")
    }

    @Test
    fun eachEntryCarriesItsTimestamp() {
        show(JournalUiState(entries = listOf(enabling)))

        composeRule.onNodeWithTag(JournalTestTags.timestamp(1)).assertIsDisplayed()
    }

    @Test
    fun clearingIsConfirmedBeforeBeingApplied() {
        show(JournalUiState(entries = listOf(enabling)))

        composeRule.onNodeWithTag(JournalTestTags.CLEAR).performClick()

        // Le dialogue est ouvert, mais rien n'est encore effacé.
        assertEquals(0, clearCount)

        composeRule.onNodeWithTag(JournalTestTags.CLEAR_CONFIRM).performClick()

        assertEquals(1, clearCount)
    }

    @Test
    fun theConfirmationClosesAfterApplying() {
        show(JournalUiState(entries = listOf(enabling)))

        composeRule.onNodeWithTag(JournalTestTags.CLEAR).performClick()
        composeRule.onNodeWithTag(JournalTestTags.CLEAR_CONFIRM).performClick()

        composeRule.onNodeWithTag(JournalTestTags.CLEAR_CONFIRM).assertDoesNotExist()
    }
}
