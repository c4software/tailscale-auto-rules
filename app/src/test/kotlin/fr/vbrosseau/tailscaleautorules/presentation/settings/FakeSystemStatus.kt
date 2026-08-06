package fr.vbrosseau.tailscaleautorules.presentation.settings

/**
 * État de plateforme pilotable.
 *
 * Les deux informations qu'il porte se modifient hors de l'application ; pouvoir
 * les changer en cours de test est indispensable pour vérifier que l'interface
 * ne reste pas sur un état périmé.
 */
class FakeSystemStatus(
    var notificationsAllowed: Boolean = true,
    var batteryExempted: Boolean = true,
    override var versionName: String = "0.1.0",
) : SystemStatus {

    override fun canNotify(): Boolean = notificationsAllowed

    override fun isIgnoringBatteryOptimizations(): Boolean = batteryExempted
}
