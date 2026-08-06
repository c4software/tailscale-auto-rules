package fr.vbrosseau.tailscaleautorules.domain.settings

/**
 * Préférences de l'utilisateur (SPECS.md §6.3).
 *
 * Les valeurs par défaut sont celles d'une première installation : l'application
 * automatise et réagit sans délai dès l'installation, car c'est sa raison
 * d'être ; rien d'autre — journalisation verbeuse notamment — n'est activé sans
 * demande.
 *
 * @param isImmediateModeEnabled bascule sans délai plutôt qu'une vérification
 *   espacée. Voir [notificationIsUnavoidable] : la plateforme en fait payer le
 *   prix en notification permanente.
 */
data class AppSettings(
    val isServiceEnabled: Boolean = true,
    val startOnBoot: Boolean = true,
    val isImmediateModeEnabled: Boolean = true,
    val showPersistentNotification: Boolean = false,
    val verboseLogging: Boolean = false,
) {
    /**
     * Vrai lorsque la notification permanente n'est plus un choix.
     *
     * Observer le réseau en continu exige un processus vivant, donc un service
     * de premier plan, auquel Android impose une notification depuis la
     * version 8. Le seul mécanisme qui permettait de s'en passer —
     * `registerNetworkCallback(NetworkRequest, PendingIntent)` — ne réveille
     * qu'une fois avant que le système ne le relâche : mesuré sur appareil, il
     * laissait l'automatisation inopérante dès le premier changement de réseau.
     *
     * La notification est donc le prix de l'instantanéité, et l'utilisateur
     * choisit de le payer ou non.
     */
    val notificationIsUnavoidable: Boolean
        get() = isServiceEnabled && isImmediateModeEnabled

    /** Vrai lorsque l'état du tunnel doit être visible dans le volet système. */
    val notificationIsVisible: Boolean
        get() = isServiceEnabled && (isImmediateModeEnabled || showPersistentNotification)

    companion object {
        val Defaults = AppSettings()
    }
}
