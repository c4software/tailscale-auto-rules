package fr.vbrosseau.tailscaleautorules.domain.repository

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
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

    // --- Exceptions dynamiques ---

    @Test
    fun aMemorizedGestureBecomesVisibleAndUsableByTheEngine() =
        runTest {
            val repository = FakeNetworkPreferenceRepository()

            repository.upsert(NetworkPreferenceKey("wifi:maison"), "Maison", TunnelState.ENABLED)

            assertEquals("Maison", repository.observeAll().first().single().ssid)
            assertEquals(
                mapOf(NetworkPreferenceKey("wifi:maison") to TunnelState.ENABLED),
                repository.current(),
            )
        }

    @Test
    fun aNewGestureReplacesTheExceptionOfTheSameNetwork() =
        runTest {
            // SPECS.md §3.3 : une seule mémoire par réseau, l'identité de
            // l'entrée survit au remplacement.
            val clock = FakeClock(1_000)
            val repository = FakeNetworkPreferenceRepository(clock)
            repository.upsert(NetworkPreferenceKey.Cellular, null, TunnelState.DISABLED)
            val id = repository.observeAll().first().single().id

            clock.advanceBy(5_000)
            repository.upsert(NetworkPreferenceKey.Cellular, null, TunnelState.ENABLED)

            val entry = repository.observeAll().first().single()
            assertEquals(id, entry.id)
            assertEquals(TunnelState.ENABLED, entry.desiredState)
            assertEquals(6_000, entry.epochMillis)
        }

    @Test
    fun theMostRecentGestureComesFirst() =
        runTest {
            val clock = FakeClock(1_000)
            val repository = FakeNetworkPreferenceRepository(clock)

            repository.upsert(NetworkPreferenceKey("wifi:maison"), "Maison", TunnelState.ENABLED)
            clock.advanceBy(5_000)
            repository.upsert(NetworkPreferenceKey.Cellular, null, TunnelState.DISABLED)

            val entries = repository.observeAll().first()
            assertEquals(NetworkPreferenceKey.Cellular, entries.first().key)
            assertEquals(NetworkPreferenceKey("wifi:maison"), entries.last().key)
        }

    @Test
    fun removingAnExceptionLeavesTheOthersUntouched() =
        runTest {
            val repository = FakeNetworkPreferenceRepository()
            repository.upsert(NetworkPreferenceKey("wifi:maison"), "Maison", TunnelState.ENABLED)
            repository.upsert(NetworkPreferenceKey.Cellular, null, TunnelState.DISABLED)
            val id = repository.observeAll().first().first { it.ssid == "Maison" }.id

            repository.remove(id)

            assertEquals(listOf(NetworkPreferenceKey.Cellular), repository.observeAll().first().map { it.key })
        }

    @Test
    fun renamingAPreferenceKeepsItsIdentity() =
        runTest {
            val repository = FakeNetworkPreferenceRepository()
            repository.upsert(NetworkPreferenceKey.forWifi("Maison"), "Maison", TunnelState.DISABLED)
            val id = repository.observeAll().first().single().id

            assertTrue(repository.update(id, "Maison Fibre").isSuccess)

            val entry = repository.observeAll().first().single()
            assertEquals(id, entry.id)
            assertEquals("Maison Fibre", entry.ssid)
            assertEquals(NetworkPreferenceKey.forWifi("Maison Fibre"), entry.key)
        }

    @Test
    fun renamingOntoAnotherPreferenceIsRejected() =
        runTest {
            val repository = FakeNetworkPreferenceRepository()
            repository.upsert(NetworkPreferenceKey.forWifi("Maison"), "Maison", TunnelState.DISABLED)
            repository.upsert(NetworkPreferenceKey.forWifi("Bureau"), "Bureau", TunnelState.DISABLED)
            val bureauId = repository.observeAll().first().first { it.ssid == "Bureau" }.id

            val result = repository.update(bureauId, "  maison ")

            assertIs<DuplicateSsidException>(result.exceptionOrNull())
        }

    @Test
    fun renamingAPreferenceToItselfIsAllowed() =
        runTest {
            // Sans exclusion de l'entrée courante, une simple correction de
            // casse se heurterait à son propre doublon.
            val repository = FakeNetworkPreferenceRepository()
            repository.upsert(NetworkPreferenceKey.forWifi("maison"), "maison", TunnelState.DISABLED)
            val id = repository.observeAll().first().single().id

            assertTrue(repository.update(id, "Maison").isSuccess)
            assertEquals("Maison", repository.observeAll().first().single().ssid)
        }

    // --- Préférences ---

    @Test
    fun appSettingsStartOnTheirDocumentedDefaults() =
        runTest {
            val settings = FakeSettingsRepository().observeAppSettings().first()

            assertTrue(settings.isServiceEnabled)
            assertTrue(settings.startOnBoot)
            assertTrue(!settings.verboseLogging, "Rien d'intrusif par défaut.")
        }

    @Test
    fun updatingSettingsTouchesOnlyTheRequestedField() =
        runTest {
            val repository = FakeSettingsRepository()

            repository.updateAppSettings { it.copy(verboseLogging = true) }

            val settings = repository.observeAppSettings().first()
            assertTrue(settings.verboseLogging)
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
