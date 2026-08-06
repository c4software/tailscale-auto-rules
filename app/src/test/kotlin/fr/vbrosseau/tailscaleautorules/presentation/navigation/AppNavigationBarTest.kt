package fr.vbrosseau.tailscaleautorules.presentation.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AppNavigationBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val selections = mutableListOf<AppDestination>()

    private fun show(currentRoute: String?) {
        composeRule.setContent {
            AppTheme(dynamicColor = false) {
                AppNavigationBar(
                    currentRoute = currentRoute,
                    onSelect = { selections += it },
                )
            }
        }
    }

    @Test
    fun everyDestinationIsReachable() {
        show(AppRoutes.HOME)

        AppDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag(NavigationTestTags.item(destination)).assertIsDisplayed()
        }
    }

    @Test
    fun theCurrentDestinationIsTheOnlyOneSelected() {
        show(AppRoutes.JOURNAL)

        composeRule.onNodeWithTag(NavigationTestTags.item(AppDestination.JOURNAL))
            .assertIsSelected()
        AppDestination.entries
            .filter { it != AppDestination.JOURNAL }
            .forEach { destination ->
                composeRule.onNodeWithTag(NavigationTestTags.item(destination))
                    .assertIsNotSelected()
            }
    }

    @Test
    fun anUnknownRouteSelectsNothingRatherThanGuessing() {
        // Une destination hors barre — un futur écran de détail — ne doit pas
        // faire apparaître une sélection arbitraire.
        show("une-route-inconnue")

        AppDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag(NavigationTestTags.item(destination)).assertIsNotSelected()
        }
    }

    @Test
    fun selectingADestinationReportsIt() {
        show(AppRoutes.HOME)

        composeRule.onNodeWithTag(NavigationTestTags.item(AppDestination.SETTINGS)).performClick()

        assertEquals(listOf(AppDestination.SETTINGS), selections)
    }

    @Test
    fun everyDestinationMapsBackToItsRoute() {
        AppDestination.entries.forEach { destination ->
            assertEquals(destination, AppDestination.forRoute(destination.route))
        }
    }

    @Test
    fun theRoutesAreDistinct() {
        // Deux destinations partageant une route rendraient la sélection
        // ambiguë et la navigation imprévisible.
        val routes = AppDestination.entries.map { it.route }

        assertEquals(routes.size, routes.toSet().size)
    }
}
