package fr.vbrosseau.tailscaleautorules.presentation.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.vbrosseau.tailscaleautorules.presentation.blacklist.BlacklistScreen
import fr.vbrosseau.tailscaleautorules.presentation.blacklist.BlacklistViewModel
import fr.vbrosseau.tailscaleautorules.presentation.home.HomeScreen
import fr.vbrosseau.tailscaleautorules.presentation.home.HomeViewModel
import fr.vbrosseau.tailscaleautorules.presentation.journal.JournalScreen
import fr.vbrosseau.tailscaleautorules.presentation.journal.JournalViewModel
import fr.vbrosseau.tailscaleautorules.presentation.settings.SettingsScreen
import fr.vbrosseau.tailscaleautorules.presentation.settings.SettingsViewModel

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
    onRequestNotificationPermission: () -> Unit = {},
    onRequestLocationPermission: () -> Unit = {},
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = AppRoutes.HOME,
        modifier = modifier,
    ) {
        composable(AppRoutes.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            HomeScreen(
                uiState = uiState,
                onSynchronize = viewModel::synchronize,
                onDisableAutomation = viewModel::disableAutomation,
            )
        }

        composable(AppRoutes.BLACKLIST) {
            val viewModel: BlacklistViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Une permission s'accorde dans une boîte de dialogue système :
            // l'application est mise en pause puis reprise. Sans ce reconstat,
            // l'explication resterait affichée alors qu'elle n'a plus lieu d'être.
            LifecycleResumeEffect(viewModel) {
                viewModel.refreshSystemStatus()
                onPauseOrDispose { }
            }

            BlacklistScreen(
                uiState = uiState,
                onAdd = viewModel::add,
                onRename = viewModel::rename,
                onRemove = viewModel::remove,
                onAddCurrentSsid = viewModel::addCurrentSsid,
                onDismissError = viewModel::dismissError,
                onRequestLocationPermission = onRequestLocationPermission,
            )
        }

        composable(AppRoutes.JOURNAL) {
            val viewModel: JournalViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            JournalScreen(uiState = uiState, onClear = viewModel::clear)
        }

        composable(AppRoutes.SETTINGS) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Même raison qu'au-dessus : la permission de notification et
            // l'exemption de batterie se règlent hors de l'application.
            LifecycleResumeEffect(viewModel) {
                viewModel.refreshSystemStatus()
                onPauseOrDispose { }
            }

            SettingsScreen(
                uiState = uiState,
                onServiceEnabledChange = viewModel::setServiceEnabled,
                onStartOnBootChange = viewModel::setStartOnBoot,
                onVerboseLoggingChange = viewModel::setVerboseLogging,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenBatterySettings = { context.openBatterySettings() },
            )
        }
    }
}

/**
 * Ouvre les réglages de batterie de l'application.
 *
 * On passe par la page de l'application plutôt que par
 * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` : cette dernière est proscrite
 * par le Play Store hors des rares catégories qui la justifient, et son usage
 * suffit à faire rejeter une publication.
 */
private fun Context.openBatterySettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { startActivity(intent) }
}
