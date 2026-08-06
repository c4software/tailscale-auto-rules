package fr.vbrosseau.tailscaleautorules.presentation.journal

import org.junit.Test
import java.time.ZoneId
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test JVM pur : la mise en forme d'une date ne demande pas Android.
 */
class JournalFormattingTest {

    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun aTimestampIsRenderedInTheGivenZone() {
        // 2026-02-02T02:40:00Z → 03:40 à Paris en hiver.
        val formatted = formatJournalTimestamp(1_770_000_000_000, paris, Locale.FRANCE)

        assertTrue(formatted.contains("03:40"), "Attendu une heure locale, obtenu « $formatted ».")
    }

    @Test
    fun theZoneActuallyChangesTheResult() {
        val instant = 1_770_000_000_000

        val atParis = formatJournalTimestamp(instant, paris, Locale.FRANCE)
        val atUtc = formatJournalTimestamp(instant, ZoneId.of("UTC"), Locale.FRANCE)

        assertTrue(atParis != atUtc, "Le fuseau doit être pris en compte.")
    }

    @Test
    fun theLocaleActuallyChangesTheResult() {
        val instant = 1_770_000_000_000

        val french = formatJournalTimestamp(instant, paris, Locale.FRANCE)
        val american = formatJournalTimestamp(instant, paris, Locale.US)

        assertTrue(french != american, "La langue doit être prise en compte.")
    }

    @Test
    fun theEpochIsHandledLikeAnyOtherInstant() {
        val formatted = formatJournalTimestamp(0, ZoneId.of("UTC"), Locale.FRANCE)

        assertEquals("01/01/1970 00:00", formatted)
    }
}
