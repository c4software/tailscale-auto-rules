package fr.vbrosseau.tailscaleautorules.presentation.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.page(2)).assertIsDisplayed()

        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.page(3)).assertIsDisplayed()
        // La dernière page remplace « Continuer » par les deux issues.
        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).assertDoesNotExist()
        composeRule.onNodeWithTag(OnboardingTestTags.LEARNING_ACCEPT).assertIsDisplayed()
        composeRule.onNodeWithTag(OnboardingTestTags.LEARNING_DECLINE).assertIsDisplayed()
    }

    @Test
    fun eachPermissionRequestLeavesFromItsOwnPage() {
        // L'explication précède la demande : le bouton vit dans la page qui
        // explique, jamais ailleurs (SPECS.md §8). Le défilement préalable
        // n'est pas une précaution : l'écran Robolectric par défaut est petit,
        // et un clic sous le pli se perd en silence.
        show(initialPage = 1)
        composeRule.onNodeWithTag(OnboardingTestTags.NOTIFICATION_GRANT)
            .performScrollTo()
            .performClick()
        assertEquals(1, notificationRequests)

        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.LOCATION_GRANT)
            .performScrollTo()
            .performClick()
        assertEquals(1, locationRequests)
    }

    @Test
    fun theLearningPageCarriesBothOutcomes() {
        show(initialPage = 3)

        composeRule.onNodeWithTag(OnboardingTestTags.LEARNING_DECLINE).performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.LEARNING_ACCEPT).performClick()

        assertEquals(listOf(false, true), finishes)
    }
}
