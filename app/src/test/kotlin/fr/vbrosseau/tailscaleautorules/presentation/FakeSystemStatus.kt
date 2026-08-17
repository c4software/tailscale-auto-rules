package fr.vbrosseau.tailscaleautorules.presentation

/**
 * État de plateforme pilotable.
 *
 * Les autorisations qu'il porte se modifient hors de l'application ; pouvoir
 * les changer en cours de test est indispensable pour vérifier que l'interface
 * ne reste pas sur un état périmé.
 */
class FakeSystemStatus(
    var notificationsAllowed: Boolean = true,
    var ssidReadable: Boolean = true,
    var appInForeground: Boolean = true,
    var batteryExempted: Boolean = true,
    override var versionName: String = "0.1.0",
) : SystemStatus {

    override fun canNotify(): Boolean = notificationsAllowed

    override fun canReadSsid(): Boolean = ssidReadable

    override fun isAppInForeground(): Boolean = appInForeground

    override fun isIgnoringBatteryOptimizations(): Boolean = batteryExempted
}
