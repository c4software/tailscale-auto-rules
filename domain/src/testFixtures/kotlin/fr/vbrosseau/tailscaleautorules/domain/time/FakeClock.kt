package fr.vbrosseau.tailscaleautorules.domain.time

/**
 * Horloge pilotée, pour les tests.
 *
 * Elle n'avance que sur ordre : un test qui vérifie un ordre chronologique doit
 * pouvoir produire des horodatages distincts sans attendre réellement.
 */
class FakeClock(private var nowMillis: Long = 0L) : Clock {
    /** Instant du dernier boot ; zéro par défaut, soit un appareil jamais éteint. */
    var bootMillis: Long = 0L

    override fun nowEpochMillis(): Long = nowMillis

    override fun bootEpochMillis(): Long = bootMillis

    /** Avance l'horloge et renvoie la nouvelle valeur. */
    fun advanceBy(millis: Long): Long {
        nowMillis += millis
        return nowMillis
    }

    fun setTo(millis: Long) {
        nowMillis = millis
    }
}
