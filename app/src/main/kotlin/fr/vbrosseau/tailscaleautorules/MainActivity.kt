package fr.vbrosseau.tailscaleautorules

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import fr.vbrosseau.tailscaleautorules.automation.AutomationCoordinator
import fr.vbrosseau.tailscaleautorules.presentation.navigation.AppDestination
import fr.vbrosseau.tailscaleautorules.presentation.navigation.AppNavHost
import fr.vbrosseau.tailscaleautorules.presentation.navigation.AppNavigationBar
import fr.vbrosseau.tailscaleautorules.presentation.navigation.navigateToTopLevel
import fr.vbrosseau.tailscaleautorules.presentation.theme.AppTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var coordinator: AutomationCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                AppRoot(onLocationPermissionGranted = ::onLocationPermissionGranted)
            }
        }
    }

    private fun onLocationPermissionGranted() {
        lifecycleScope.launch { coordinator.onLocationPermissionGranted() }
    }
}

/**
 * Ossature commune : barre de titre, barre de navigation, graphe.
 *
 * `TopAppBar` est encore expérimental dans Material 3 ; l'opt-in est local
 * plutôt que déclaré pour tout le module, afin que la dette reste visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(
    onLocationPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = AppDestination.forRoute(currentRoute)

    // La demande de permission a besoin de l'Activity : elle est lancée ici et
    // transmise à l'écran qui en a l'usage, plutôt qu'obtenue par un Context
    // reconstitué au fond de l'arbre.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    // La localisation grossière est demandée conjointement : Android refuse la
    // permission fine seule depuis la version 12. Seule la permission fine
    // donne accès au SSID : c'est elle, et elle seule, qui vaut octroi.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            onLocationPermissionGranted()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(currentDestination?.labelRes ?: R.string.app_name))
                },
            )
        },
        bottomBar = {
            AppNavigationBar(
                currentRoute = currentRoute,
                onSelect = navController::navigateToTopLevel,
            )
        },
    ) { innerPadding ->
        AppNavHost(
            modifier = Modifier.padding(innerPadding),
            navController = navController,
            onRequestNotificationPermission = {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onRequestLocationPermission = {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
        )
    }
}
