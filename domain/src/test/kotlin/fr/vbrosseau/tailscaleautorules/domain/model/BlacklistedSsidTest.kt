package fr.vbrosseau.tailscaleautorules.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlacklistedSsidTest {
    @Test
    fun aBlankSsidCannotBeStored() {
        listOf("", "   ", "\t").forEach { blank ->
            assertFailsWith<IllegalArgumentException> { BlacklistedSsid(id = 1, value = blank) }
        }
    }

    @Test
    fun theCanonicalFormIgnoresCaseAndSurroundingSpaces() {
        assertEquals("maison", "  MaIsOn ".asSsidKey())
        assertEquals("maison".asSsidKey(), " Maison ".asSsidKey())
    }

    @Test
    fun theCanonicalFormPreservesInnerSpacing() {
        // Deux réseaux « Café du coin » et « Café ducoin » sont distincts :
        // normaliser trop agressivement les confondrait.
        assertEquals("café du coin", " Café du coin ".asSsidKey())
    }
}
