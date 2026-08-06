package fr.vbrosseau.tailscaleautorules.automation

/**
 * Réaligne la notification d'état sur les réglages et l'état réel du tunnel.
 *
 * Existe pour que l'écran des paramètres déclare exactement ce dont il a besoin.
 * Il dépendait du coordinateur entier alors qu'il n'en emploie qu'une opération,
 * ce qui rendait son test tributaire de toute la mécanique d'automatisation —
 * client Tailscale, journal, moteur de règles — sans rien y éprouver.
 */
fun interface NotificationRefresher {

    /**
     * Publie, met à jour ou retire la notification selon les réglages courants.
     *
     * Appelée au retour sur un écran : la permission de notification se donne
     * hors de l'application, et sans ce rappel elle ne serait prise en compte
     * qu'à la modification suivante d'un réglage.
     */
    suspend fun refreshNotificationIfEnabled()
}
