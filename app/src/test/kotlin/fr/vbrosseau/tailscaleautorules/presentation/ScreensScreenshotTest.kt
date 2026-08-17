package fr.vbrosseau.tailscaleautorules.presentation

import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreference
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import fr.vbrosseau.tailscaleautorules.domain.usecase.ManualOverride
import fr.vbrosseau.tailscaleautorules.presentation.networks.NetworksError
import fr.vbrosseau.tailscaleautorules.presentation.networks.NetworksScreen
import fr.vbrosseau.tailscaleautorules.presentation.networks.NetworksUiState
import fr.vbrosseau.tailscaleautorules.presentation.home.HomeScreen
import fr.vbrosseau.tailscaleautorules.presentation.home.HomeUiState
import fr.vbrosseau.tailscaleautorules.presentation.journal.JournalScreen
import fr.vbrosseau.tailscaleautorules.presentation.journal.JournalUiState
import fr.vbrosseau.tailscaleautorules.presentation.onboarding.OnboardingScreen
import fr.vbrosseau.tailscaleautorules.presentation.settings.SettingsScreen
import fr.vbrosseau.tailscaleautorules.presentation.settings.SettingsUiState
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

/**
 * Références visuelles des quatre écrans.
 *
 * Les états retenus ne sont pas les plus simples mais les plus **exposés** :
 * ceux où une régression de mise en page se verrait immédiatement à l'usage —
 * carte d'alerte, liste remplie, état vide, message d'erreur.
 */
class ScreensScreenshotTest : ScreenshotTest() {

    private val paris = ZoneId.of("Europe/Paris")

    private val lastChange = JournalEntry(
        id = 1,
        epochMillis = 1_770_000_000_000,
        previousState = TunnelState.DISABLED,
        newState = TunnelState.ENABLED,
        ruleId = RuleId("other-wifi"),
    )

    // --- Accueil ---

    @Test
    fun accueilNominal() = capture("accueil-nominal") {
        HomeScreen(
            uiState = HomeUiState(
                tunnelState = TunnelState.ENABLED,
                transport = NetworkTransport.WIFI,
                ssid = "Aéroport CDG",
                lastChange = lastChange,
            ),
            onSynchronize = {},
            onDisableAutomation = {},
        )
    }

    @Test
    fun accueilSansClientTailscale() = capture("accueil-sans-client") {
        // La carte d'alerte occupe le haut de l'écran : c'est l'état où une
        // régression de mise en page serait la plus visible.
        HomeScreen(
            uiState = HomeUiState(isTailscaleInstalled = false),
            onSynchronize = {},
            onDisableAutomation = {},
        )
    }

    @Test
    fun accueilEnCoursDeSynchronisation() = capture("accueil-synchronisation") {
        HomeScreen(
            uiState = HomeUiState(
                tunnelState = TunnelState.DISABLED,
                transport = NetworkTransport.CELLULAR,
                isSynchronizing = true,
            ),
            onSynchronize = {},
            onDisableAutomation = {},
        )
    }

    @Test
    fun accueilAutomatisationCoupee() = capture("accueil-automatisation-coupee") {
        // La carte remplace le bouton : c'est elle qui doit rester lisible,
        // en particulier en thème sombre.
        HomeScreen(
            uiState = HomeUiState(
                tunnelState = TunnelState.DISABLED,
                transport = NetworkTransport.WIFI,
                ssid = "Aéroport CDG",
                isAutomationEnabled = false,
            ),
            onSynchronize = {},
            onDisableAutomation = {},
        )
    }

    @Test
    fun accueilTunnelModifieManuellement() = capture("accueil-modification-manuelle") {
        // La carte doit se distinguer de celle de l'automatisation coupée :
        // ici l'automatisation veille toujours, elle constate un geste.
        HomeScreen(
            uiState = HomeUiState(
                tunnelState = TunnelState.ENABLED,
                transport = NetworkTransport.WIFI,
                ssid = "Maison",
                lastChange = JournalEntry(
                    id = 2,
                    epochMillis = 1_770_000_000_000,
                    previousState = TunnelState.ENABLED,
                    newState = TunnelState.DISABLED,
                    ruleId = RuleId("network-preference"),
                ),
                manualOverride = ManualOverride(
                    observedState = TunnelState.ENABLED,
                    ruleId = RuleId("network-preference"),
                ),
                // Le cas nominal depuis les exceptions dynamiques : le geste
                // est mémorisé, et la carte l'annonce.
                willMemorizeManualGesture = true,
            ),
            onSynchronize = {},
            onDisableAutomation = {},
        )
    }


    // --- Premier lancement ---

    @Test
    fun onboardingBienvenue() = capture("onboarding-bienvenue") {
        OnboardingScreen(
            onRequestNotificationPermission = {},
            onRequestLocationPermission = {},
            onFinish = {},
        )
    }

    @Test
    fun onboardingApprentissage() = capture("onboarding-apprentissage") {
        // La dernière page porte les deux issues du parcours : c'est elle qui
        // doit rester lisible, boutons compris, en thème sombre.
        OnboardingScreen(
            onRequestNotificationPermission = {},
            onRequestLocationPermission = {},
            onFinish = {},
            initialPage = 3,
        )
    }

    // --- Réseaux ---

    @Test
    fun reseauxRemplis() = capture("networks-remplis") {
        // Trois volontés : coupé, actif, et les données mobiles.
        NetworksScreen(
            uiState = NetworksUiState(
                preferences = listOf(
                    NetworkPreference(
                        id = 1,
                        key = NetworkPreferenceKey("wifi:maison"),
                        ssid = "Maison",
                        desiredState = TunnelState.DISABLED,
                        epochMillis = 1_770_000_000_000,
                    ),
                    NetworkPreference(
                        id = 2,
                        key = NetworkPreferenceKey("wifi:bureau"),
                        ssid = "Bureau",
                        desiredState = TunnelState.ENABLED,
                        epochMillis = 1_770_000_000_000,
                    ),
                    NetworkPreference(
                        id = 3,
                        key = NetworkPreferenceKey.Cellular,
                        ssid = null,
                        desiredState = TunnelState.DISABLED,
                        epochMillis = 1_770_000_000_000,
                    ),
                ),
                currentSsid = "Aéroport CDG",
            ),
            onAdd = { _, _ -> },
            onRename = { _, _ -> },
            onRemove = {},
            onSetPreferenceEnabled = { _, _ -> },
            onAddCurrentSsid = {},
            onDismissError = {},
            onMobileRuleChange = {},
        )
    }


    @Test
    fun reseauxVides() = capture("networks-vides") {
        NetworksScreen(
            uiState = NetworksUiState(),
            onAdd = { _, _ -> },
            onRename = { _, _ -> },
            onRemove = {},
            onSetPreferenceEnabled = { _, _ -> },
            onAddCurrentSsid = {},
            onDismissError = {},
            onMobileRuleChange = {},
        )
    }

    @Test
    fun reseauxAvecErreur() = capture("networks-erreur") {
        NetworksScreen(
            uiState = NetworksUiState(
                preferences = listOf(
                    NetworkPreference(
                        id = 1,
                        key = NetworkPreferenceKey("wifi:maison"),
                        ssid = "Maison",
                        desiredState = TunnelState.DISABLED,
                        epochMillis = 1_770_000_000_000,
                    ),
                ),
                error = NetworksError.DUPLICATE,
            ),
            onAdd = { _, _ -> },
            onRename = { _, _ -> },
            onRemove = {},
            onSetPreferenceEnabled = { _, _ -> },
            onAddCurrentSsid = {},
            onDismissError = {},
            onMobileRuleChange = {},
        )
    }

    @Test
    fun reseauxSansPermissionDeLocalisation() = capture("networks-permission") {
        // Le texte d'explication est long : c'est le cas où un défaut de
        // contraste ou de débordement se verrait le plus.
        NetworksScreen(
            uiState = NetworksUiState(canReadSsid = false),
            onAdd = { _, _ -> },
            onRename = { _, _ -> },
            onRemove = {},
            onSetPreferenceEnabled = { _, _ -> },
            onAddCurrentSsid = {},
            onDismissError = {},
            onMobileRuleChange = {},
        )
    }

    // --- Journal ---

    @Test
    fun journalRempli() = capture("journal-rempli") {
        JournalScreen(
            uiState = JournalUiState(
                entries = listOf(
                    lastChange.copy(
                        id = 3,
                        epochMillis = 1_770_007_200_000,
                        previousState = TunnelState.ENABLED,
                        newState = TunnelState.DISABLED,
                        ruleId = RuleId("airplane-mode"),
                    ),
                    lastChange.copy(
                        id = 2,
                        epochMillis = 1_770_003_600_000,
                        previousState = TunnelState.ENABLED,
                        newState = TunnelState.DISABLED,
                        ruleId = RuleId("network-preference"),
                    ),
                    lastChange,
                ),
            ),
            onClear = {},
            zoneId = paris,
            locale = Locale.FRANCE,
        )
    }

    @Test
    fun journalEnChargement() = capture("journal-chargement") {
        // L'indicateur de chargement est commun aux quatre écrans : une seule
        // référence suffit, prise ici parce que le journal est l'écran où
        // l'état vide et l'état « pas encore lu » se confondent le plus.
        JournalScreen(
            uiState = JournalUiState(isLoading = true),
            onClear = {},
            zoneId = paris,
            locale = Locale.FRANCE,
        )
    }

    @Test
    fun journalVide() = capture("journal-vide") {
        JournalScreen(
            uiState = JournalUiState(),
            onClear = {},
            zoneId = paris,
            locale = Locale.FRANCE,
        )
    }

    // --- Paramètres ---

    @Test
    fun parametresNominaux() = capture("parametres-nominaux") {
        SettingsScreen(
            uiState = SettingsUiState(versionName = "0.1.0"),
            onServiceEnabledChange = {},
            onLearningEnabledChange = {},
            onStartOnBootChange = {},
            onVerboseLoggingChange = {},
            onRequestNotificationPermission = {},
            onRequestBackgroundLocation = {},
            onOpenBatterySettings = {},
            onReplayOnboarding = {},
        )
    }

    @Test
    fun parametresAvecAvertissements() = capture("parametres-avertissements") {
        // Les cartes conditionnelles réunies : explication de la notification,
        // permission manquante, localisation d'arrière-plan, exemption de
        // batterie.
        //
        // L'automatisation reste active, et c'est nécessaire : une permission
        // de notification ne manque que si une notification doit être affichée.
        SettingsScreen(
            uiState = SettingsUiState(
                settings = AppSettings(isServiceEnabled = true),
                canNotify = false,
                canReadSsid = true,
                canReadSsidInBackground = false,
                isIgnoringBatteryOptimizations = false,
                versionName = "0.1.0",
            ),
            onServiceEnabledChange = {},
            onLearningEnabledChange = {},
            onStartOnBootChange = {},
            onVerboseLoggingChange = {},
            onRequestNotificationPermission = {},
            onRequestBackgroundLocation = {},
            onOpenBatterySettings = {},
            onReplayOnboarding = {},
        )
    }
}
