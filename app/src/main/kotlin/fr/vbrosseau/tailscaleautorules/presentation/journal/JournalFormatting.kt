package fr.vbrosseau.tailscaleautorules.presentation.journal

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Met en forme l'horodatage d'une entrée de journal.
 *
 * Le domaine stocke des millisecondes depuis l'époque : une valeur, sans fuseau
 * ni langue. La mise en forme appartient donc à la présentation, seule à
 * connaître les deux.
 *
 * Le fuseau et la langue sont des paramètres afin que les tests ne dépendent
 * pas des réglages de la machine qui les exécute.
 */
fun formatJournalTimestamp(
    epochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.SHORT)
    .withLocale(locale)
    .withZone(zoneId)
    .format(Instant.ofEpochMilli(epochMillis))
