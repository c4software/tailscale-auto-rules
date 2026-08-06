package fr.vbrosseau.tailscaleautorules.domain.settings

/**
 * Préférences de l'utilisateur (SPECS.md §6.3).
 *
 * Les valeurs par défaut sont celles d'une première installation : le service
 * automatise dès l'installation, mais rien d'intrusif — ni notification
 * permanente, ni journalisation verbeuse — n'est activé sans demande.
 */
data class AppSettings(
    val isServiceEnabled: Boolean = true,
    val startOnBoot: Boolean = true,
    val showPersistentNotification: Boolean = false,
    val verboseLogging: Boolean = false,
) {
    companion object {
        val Defaults = AppSettings()
    }
}
