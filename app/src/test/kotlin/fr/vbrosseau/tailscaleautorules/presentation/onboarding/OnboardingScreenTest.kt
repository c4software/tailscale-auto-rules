package fr.vbrosseau.tailscaleautorules.presentation.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * L'écran est sans état applicatif : il se teste en vérifiant que les pages
 * avancent et que chaque rappel part du bon bouton.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var notificationRequests = 0
    private var locationRequests = 0
    private val finishes = mutableListOf<Boolean>()

    private fun show(initialPage: Int = 0) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                OnboardingScreen(
                    onRequestNotificationPermission = { notificationRequests++ },
                    onRequestLocationPermission = { locationRequests++ },
                    onFinish = { finishes += it },
                    initialPage = initialPage,
                )
            }
        }
    }

    @Test
    fun theJourneyStartsOnTheWelcomePage() {
        show()

        composeRule.onNodeWithTag(OnboardingTestTags.page(0)).assertIsDisplayed()
        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).assertIsDisplayed()
    }

    @Test
    fun continueWalksThroughEveryPageUpToTheLearningChoice() {
        show()

        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.page(1)).assertIsDisplayed()

        // Sur une page de permission, le premier appui demande et laisse la
        // page en place ; c'est le second qui avance.
        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.page(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.page(2)).assertIsDisplayed()

        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.page(3)).assertIsDisplayed()
        // La dernière page remplace « Continuer » par les deux issues.
        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).assertDoesNotExist()
        composeRule.onNodeWithTag(OnboardingTestTags.LEARNING_ACCEPT).assertIsDisplayed()
        composeRule.onNodeWithTag(OnboardingTestTags.LEARNING_DECLINE).assertIsDisplayed()
    }

    @Test
    fun eachPermissionRequestLeavesFromItsOwnPage() {
        // L'explication précède la demande : elle part du bouton de la page qui
        // explique, jamais avant (SPECS.md §8). Et une seule fois : un refus ne
        // doit pas enfermer le parcours, le second appui passe à la suite.
        show(initialPage = 1)
        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).performClick()
        assertEquals(1, notificationRequests)

        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).performClick()
        assertEquals(1, notificationRequests)
        composeRule.onNodeWithTag(OnboardingTestTags.page(2)).assertIsDisplayed()

        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).performClick()
        assertEquals(1, locationRequests)

        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).performClick()
        assertEquals(1, locationRequests)
        composeRule.onNodeWithTag(OnboardingTestTags.page(3)).assertIsDisplayed()
    }

    @Test
    fun aSwipeDoesNotSkipAPage() {
        // Le glissement est retiré : sans lui, aucune page de permission ne
        // peut être franchie sans que sa demande ait été posée.
        show()

        composeRule.onNodeWithTag(OnboardingTestTags.PAGER).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(OnboardingTestTags.page(0)).assertIsDisplayed()
    }

    @Test
    fun grantingAPermissionAdvancesTheJourneyByItself() {
        // L'octroi vaut « continuer » : la page a rempli son office. Le refus,
        // lui, n'incrémente pas le compteur — la main reste à l'utilisateur.
        var grants by mutableStateOf(0)
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                OnboardingScreen(
                    onRequestNotificationPermission = {},
                    onRequestLocationPermission = {},
                    onFinish = {},
                    initialPage = 1,
                    grantedPermissionCount = grants,
                )
            }
        }
        composeRule.onNodeWithTag(OnboardingTestTags.page(1)).assertIsDisplayed()

        grants = 1
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(OnboardingTestTags.page(2)).assertIsDisplayed()
    }

    @Test
    fun theLearningPageCarriesBothOutcomes() {
        show(initialPage = 3)

        composeRule.onNodeWithTag(OnboardingTestTags.LEARNING_DECLINE).performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.LEARNING_ACCEPT).performClick()

        assertEquals(listOf(false, true), finishes)
    }
}
