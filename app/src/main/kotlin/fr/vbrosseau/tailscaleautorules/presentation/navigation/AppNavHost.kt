package fr.vbrosseau.tailscaleautorules.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.vbrosseau.tailscaleautorules.presentation.home.HomeScreen
import fr.vbrosseau.tailscaleautorules.presentation.home.HomeViewModel

/**
 * Graphe de navigation.
 *
 * Chaque destination récupère son ViewModel ici et transmet un état à un écran
 * sans état : c'est ce qui garde les écrans prévisualisables et testables sans
 * injection.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.HOME,
        modifier = modifier,
    ) {
        composable(AppRoutes.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            HomeScreen(uiState = uiState, onSynchronize = viewModel::synchronize)
        }
    }
}
