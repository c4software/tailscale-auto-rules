package fr.vbrosseau.tailscaleautorules.domain.repository

import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleSettings
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Les Fakes servent de référence à tous les tests en aval : s'ils divergent du
 * contrat, ils masquent les bogues qu'ils devraient révéler. Ils sont donc
 * couverts comme du code de production.
 */
class FakeRepositoriesTest {
    // --- Blacklist ---

    @Test
    fun anAddedSsidBecomesVisibleAndUsableByTheEngine() =
        runTest {
            val repository = FakeBlacklistRepository()

            assertTrue(repository.add("Maison").isSuccess)

            assertEquals(listOf("Maison"), repository.observeAll().first().map { it.value })
            assertEquals(setOf("Maison"), repository.currentSsids())
        }

    @Test
    fun aDuplicateIsRejectedOnItsCanonicalForm() =
        runTest {
            val repository = FakeBlacklistRepository(listOf("Maison"))

            val result = repository.add("  maison ")

            assertIs<DuplicateSsidException>(result.exceptionOrNull())
            assertEquals(1, repository.observeAll().first().size)
        }

    @Test
    fun renamingAnEntryKeepsItsIdentity() =
        runTest {
            val repository = FakeBlacklistRepository(listOf("Maison"))
            val id = repository.observeAll().first().single().id

            assertTrue(repository.update(id, "Maison Fibre").isSuccess)

            val entry = repository.observeAll().first().single()
            assertEquals(id, entry.id)
            assertEquals("Maison Fibre", entry.value)
        }

    @Test
    fun renamingAnEntryToItselfIsAllowed() =
        runTest {
            // Sans exclusion de l'entrée courante, une simple correction de casse
            // se heurterait à son propre doublon.
            val repository = FakeBlacklistRepository(listOf("maison"))
            val id = repository.observeAll().first().single().id

            assertTrue(repository.update(id, "Maison").isSuccess)
            assertEquals("Maison", repository.observeAll().first().single().value)
        }

    @Test
    fun renamingOntoAnotherEntryIsRejected() =
        runTest {
            val repository = FakeBlacklistRepository(listOf("Maison", "Bureau"))
            val bureauId = repository.observeAll().first().last().id

            val result = repository.update(bureauId, "maison")

            assertIs<DuplicateSsidException>(result.exceptionOrNull())
            assertEquals("Bureau", repository.observeAll().first().last().value)
        }

    @Test
    fun removingAnEntryLeavesTheOthersUntouched() =
        runTest {
            val repository = FakeBlacklistRepository(listOf("Maison", "Bureau"))
            val id = repository.observeAll().first().first().id

            repository.remove(id)

            assertEquals(listOf("Bureau"), repository.observeAll().first().map { it.value })
        }

    // --- Journal ---

    @Test
    fun theMostRecentEntryComesFirst() =
        runTest {
            val clock = FakeClock(1_000)
            val repository = FakeJournalRepository(clock)

            repository.record(TunnelState.DISABLED, TunnelState.ENABLED, RuleId("mobile-network"))
            clock.advanceBy(5_000)
            repository.record(TunnelState.ENABLED, TunnelState.DISABLED, RuleId("airplane-mode"))

            val entries = repository.observeRecent().first()
            assertEquals(RuleId("airplane-mode"), entries.first().ruleId)
            assertEquals(6_000, entries.first().epochMillis)
            assertEquals(1_000, entries.last().epochMillis)
        }

    @Test
    fun theJournalNeverExceedsItsCapacity() =
        runTest {
            val repository = FakeJournalRepository()

            repeat(JournalRepository.MAX_ENTRIES + 50) { index ->
                val from = if (index % 2 == 0) TunnelState.DISABLED else TunnelState.ENABLED
                val to = if (index % 2 == 0) TunnelState.ENABLED else TunnelState.DISABLED
                repository.record(from, to, RuleId("rule-$index"))
            }

            val entries = repository.observeRecent().first()
            assertEquals(JournalRepository.MAX_ENTRIES, entries.size)
            // Ce sont bien les plus anciennes qui disparaissent.
            assertEquals(RuleId("rule-549"), entries.first().ruleId)
            assertEquals(RuleId("rule-50"), entries.last().ruleId)
        }

    @Test
    fun clearingEmptiesTheJournal() =
        runTest {
            val repository = FakeJournalRepository()
            repository.record(TunnelState.DISABLED, TunnelState.ENABLED, RuleId("a"))

            repository.clear()

            assertTrue(repository.observeRecent().first().isEmpty())
        }

    // --- Préférences ---

    @Test
    fun appSettingsStartOnTheirDocumentedDefaults() =
        runTest {
            val settings = FakeSettingsRepository().observeAppSettings().first()

            assertTrue(settings.isServiceEnabled)
            assertTrue(settings.startOnBoot)
            assertTrue(!settings.showPersistentNotification, "Rien d'intrusif par défaut.")
            assertTrue(!settings.verboseLogging)
        }

    @Test
    fun updatingSettingsTouchesOnlyTheRequestedField() =
        runTest {
            val repository = FakeSettingsRepository()

            repository.updateAppSettings { it.copy(showPersistentNotification = true) }

            val settings = repository.observeAppSettings().first()
            assertTrue(settings.showPersistentNotification)
            assertTrue(settings.isServiceEnabled, "Les autres préférences ne bougent pas.")
        }

    @Test
    fun onlyOverriddenRuleSettingsAreStored() =
        runTest {
            // Ne stocker que les écarts évite de migrer la persistance à chaque
            // nouvelle règle livrée.
            val repository = FakeSettingsRepository()
            assertTrue(repository.currentRuleSettings().isEmpty())

            val override = RuleSettings(isEnabled = false, priority = 150)
            repository.setRuleSettings(RuleId("airplane-mode"), override)

            assertEquals(mapOf(RuleId("airplane-mode") to override), repository.currentRuleSettings())
        }

    @Test
    fun resettingARuleRemovesItsOverride() =
        runTest {
            val repository =
                FakeSettingsRepository(
                    initialRuleSettings = mapOf(RuleId("a") to RuleSettings(isEnabled = false, priority = 1)),
                )

            repository.resetRuleSettings(RuleId("a"))

            assertTrue(repository.observeRuleSettings().first().isEmpty())
        }
}
