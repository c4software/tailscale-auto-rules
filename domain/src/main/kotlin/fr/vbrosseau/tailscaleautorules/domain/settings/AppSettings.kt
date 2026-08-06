package fr.vbrosseau.tailscaleautorules.domain.settings

/**
 * Préférences de l'utilisateur (SPECS.md §6.3).
 *
 * Les valeurs par défaut sont celles d'une première installation : l'application
 * automatise dès l'installation, car c'est sa raison d'être ; rien d'autre —
 * journalisation verbeuse notamment — n'est activé sans demande.
 *
 * **La notification n'y figure pas, et c'est délibéré.** Observer le réseau
 * exige un service de premier plan, auquel Android impose une notification
 * permanente. En faire un réglage laisserait croire à un choix qui n'existe
 * pas : elle est visible exactement lorsque l'automatisation est active.
 */
data class AppSettings(
    val isServiceEnabled: Boolean = true,
    val startOnBoot: Boolean = true,
    val verboseLogging: Boolean = false,
) {
    /** Vrai lorsque l'état du tunnel doit être visible dans le volet système. */
    val notificationIsVisible: Boolean
        get() = isServiceEnabled

    companion object {
        val Defaults = AppSettings()
    }
}
