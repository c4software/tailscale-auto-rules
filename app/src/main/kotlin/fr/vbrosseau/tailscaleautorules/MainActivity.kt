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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import fr.vbrosseau.tailscaleautorules.automation.AutomationCoordinator
import fr.vbrosseau.tailscaleautorules.presentation.LoadingIndicator
import fr.vbrosseau.tailscaleautorules.presentation.navigation.AppDestination
import fr.vbrosseau.tailscaleautorules.presentation.navigation.AppNavHost
import fr.vbrosseau.tailscaleautorules.presentation.navigation.AppNavigationBar
import fr.vbrosseau.tailscaleautorules.presentation.navigation.navigateToTopLevel
import fr.vbrosseau.tailscaleautorules.presentation.onboarding.OnboardingScreen
import fr.vbrosseau.tailscaleautorules.presentation.onboarding.OnboardingViewModel
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
 * Racine de l'interface : lanceurs de permission, portail de premier
 * lancement, puis ossature.
 *
 * Les lanceurs vivent ici, au-dessus du portail : le parcours de premier
 * lancement et les écrans de l'application demandent les mêmes permissions,
 * par les mêmes chemins.
 */
@Composable
private fun AppRoot(
    onLocationPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Chaque octroi est compté : le parcours de premier lancement s'en sert
    // pour avancer de lui-même — la page a rempli son office.
    var grantedPermissionCount by remember { mutableIntStateOf(0) }

    // La demande de permission a besoin de l'Activity : elle est lancée ici et
    // transmise à l'écran qui en a l'usage, plutôt qu'obtenue par un Context
    // reconstitué au fond de l'arbre.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) grantedPermissionCount++
    }

    // La localisation grossière est demandée conjointement : Android refuse la
    // permission fine seule depuis la version 12. Seule la permission fine
    // donne accès au SSID : c'est elle, et elle seule, qui vaut octroi.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            onLocationPermissionGranted()
            grantedPermissionCount++
        }
    }

    val requestNotificationPermission = {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val requestLocationPermission = {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    // Le parcours de premier lancement remplace l'ossature entière tant qu'il
    // n'est pas clos (SPECS.md §6.5) : barres et navigation n'ont pas de sens
    // avant que l'application ait été présentée.
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val onboarding by onboardingViewModel.uiState.collectAsStateWithLifecycle()

    when {
        // Sous une `Surface` : hors du `Scaffold`, rien d'autre ne pose le
        // fond ni la couleur de contenu du thème.
        onboarding.isLoading -> Surface(modifier = modifier.fillMaxSize()) {
            LoadingIndicator()
        }
        !onboarding.isDone -> OnboardingScreen(
            onRequestNotificationPermission = requestNotificationPermission,
            onRequestLocationPermission = requestLocationPermission,
            onFinish = onboardingViewModel::finish,
            modifier = modifier,
            grantedPermissionCount = grantedPermissionCount,
        )

        else -> MainScaffold(
            onRequestNotificationPermission = requestNotificationPermission,
            onRequestLocationPermission = requestLocationPermission,
            modifier = modifier,
        )
    }
}

/**
 * Ossature de l'application : barre de titre, barre de navigation, graphe.
 *
 * `TopAppBar` est encore expérimental dans Material 3 ; l'opt-in est local
 * plutôt que déclaré pour tout le module, afin que la dette reste visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    onRequestNotificationPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = AppDestination.forRoute(currentRoute)

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
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestLocationPermission = onRequestLocationPermission,
        )
    }
}
