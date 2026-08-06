package fr.vbrosseau.tailscaleautorules.presentation

import fr.vbrosseau.tailscaleautorules.domain.model.BlacklistedSsid
import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import fr.vbrosseau.tailscaleautorules.presentation.blacklist.BlacklistError
import fr.vbrosseau.tailscaleautorules.presentation.blacklist.BlacklistScreen
import fr.vbrosseau.tailscaleautorules.presentation.blacklist.BlacklistUiState
import fr.vbrosseau.tailscaleautorules.presentation.home.HomeScreen
import fr.vbrosseau.tailscaleautorules.presentation.home.HomeUiState
import fr.vbrosseau.tailscaleautorules.presentation.journal.JournalScreen
import fr.vbrosseau.tailscaleautorules.presentation.journal.JournalUiState
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
        )
    }

    @Test
    fun accueilSansClientTailscale() = capture("accueil-sans-client") {
        // La carte d'alerte occupe le haut de l'écran : c'est l'état où une
        // régression de mise en page serait la plus visible.
        HomeScreen(
            uiState = HomeUiState(isTailscaleInstalled = false),
            onSynchronize = {},
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
        )
    }

    // --- Réseaux de confiance ---

    @Test
    fun blacklistRemplie() = capture("blacklist-remplie") {
        BlacklistScreen(
            uiState = BlacklistUiState(
                entries = listOf(
                    BlacklistedSsid(id = 1, value = "Maison"),
                    BlacklistedSsid(id = 2, value = "Bureau"),
                    BlacklistedSsid(id = 3, value = "Fibre Salon 5 GHz"),
                ),
                currentSsid = "Aéroport CDG",
            ),
            onAdd = {},
            onRename = { _, _ -> },
            onRemove = {},
            onAddCurrentSsid = {},
            onDismissError = {},
        )
    }

    @Test
    fun blacklistVide() = capture("blacklist-vide") {
        BlacklistScreen(
            uiState = BlacklistUiState(),
            onAdd = {},
            onRename = { _, _ -> },
            onRemove = {},
            onAddCurrentSsid = {},
            onDismissError = {},
        )
    }

    @Test
    fun blacklistAvecErreur() = capture("blacklist-erreur") {
        BlacklistScreen(
            uiState = BlacklistUiState(
                entries = listOf(BlacklistedSsid(id = 1, value = "Maison")),
                error = BlacklistError.DUPLICATE,
            ),
            onAdd = {},
            onRename = { _, _ -> },
            onRemove = {},
            onAddCurrentSsid = {},
            onDismissError = {},
        )
    }

    @Test
    fun blacklistSansPermissionDeLocalisation() = capture("blacklist-permission") {
        // Le texte d'explication est long : c'est le cas où un défaut de
        // contraste ou de débordement se verrait le plus.
        BlacklistScreen(
            uiState = BlacklistUiState(canReadSsid = false),
            onAdd = {},
            onRename = { _, _ -> },
            onRemove = {},
            onAddCurrentSsid = {},
            onDismissError = {},
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
                        ruleId = RuleId("blacklisted-wifi"),
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
            onStartOnBootChange = {},
            onImmediateModeChange = {},
            onPersistentNotificationChange = {},
            onVerboseLoggingChange = {},
            onRequestNotificationPermission = {},
            onOpenBatterySettings = {},
        )
    }

    @Test
    fun parametresAvecAvertissements() = capture("parametres-avertissements") {
        // Trois cartes conditionnelles à la fois : avis d'automatisation
        // désactivée, permission manquante, exemption de batterie.
        SettingsScreen(
            uiState = SettingsUiState(
                settings = AppSettings(
                    isServiceEnabled = false,
                    showPersistentNotification = true,
                ),
                canNotify = false,
                isIgnoringBatteryOptimizations = false,
                versionName = "0.1.0",
            ),
            onServiceEnabledChange = {},
            onStartOnBootChange = {},
            onImmediateModeChange = {},
            onPersistentNotificationChange = {},
            onVerboseLoggingChange = {},
            onRequestNotificationPermission = {},
            onOpenBatterySettings = {},
        )
    }
}
