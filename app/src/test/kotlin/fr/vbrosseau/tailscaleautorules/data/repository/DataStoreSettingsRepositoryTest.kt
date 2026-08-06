package fr.vbrosseau.tailscaleautorules.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import fr.vbrosseau.tailscaleautorules.data.local.SettingsKeys
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleId
import fr.vbrosseau.tailscaleautorules.domain.rule.RuleSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Éprouve la persistance réelle des préférences, sur un fichier temporaire.
 *
 * Aucun besoin d'Android ici : DataStore Preferences s'exécute en JVM pure dès
 * lors qu'on lui fournit un fichier.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        scope = TestScope(UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { temporaryFolder.newFile("settings.preferences_pb") },
        )
        repository = DataStoreSettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun anEmptyStoreYieldsTheDocumentedDefaults() = runTest {
        val settings = repository.observeAppSettings().first()

        assertTrue(settings.isServiceEnabled)
        assertTrue(settings.startOnBoot)
        assertTrue(!settings.verboseLogging)
    }

    @Test
    fun anUpdatedPreferenceIsPersistedAndLeavesTheOthersAlone() = runTest {
        repository.updateAppSettings { it.copy(verboseLogging = true) }

        val settings = repository.observeAppSettings().first()
        assertTrue(settings.verboseLogging)
        assertTrue(settings.isServiceEnabled)
        assertTrue(settings.startOnBoot)
    }

    @Test
    fun successiveUpdatesAccumulate() = runTest {
        repository.updateAppSettings { it.copy(isServiceEnabled = false) }
        repository.updateAppSettings { it.copy(verboseLogging = true) }

        val settings = repository.observeAppSettings().first()
        assertTrue(!settings.isServiceEnabled)
        assertTrue(settings.verboseLogging)
    }

    @Test
    fun noRuleIsOverriddenByDefault() = runTest {
        // Ne stocker que les écarts évite une migration à chaque règle ajoutée.
        assertTrue(repository.currentRuleSettings().isEmpty())
    }

    @Test
    fun aRuleOverrideIsStoredAndReadBack() = runTest {
        val override = RuleSettings(isEnabled = false, priority = 150)

        repository.setRuleSettings(RuleId("airplane-mode"), override)

        assertEquals(
            mapOf(RuleId("airplane-mode") to override),
            repository.observeRuleSettings().first(),
        )
    }

    @Test
    fun severalRulesCoexistWithoutInterference() = runTest {
        repository.setRuleSettings(RuleId("airplane-mode"), RuleSettings(false, 10))
        repository.setRuleSettings(RuleId("mobile-network"), RuleSettings(true, 20))

        assertEquals(
            mapOf(
                RuleId("airplane-mode") to RuleSettings(false, 10),
                RuleId("mobile-network") to RuleSettings(true, 20),
            ),
            repository.currentRuleSettings(),
        )
    }

    @Test
    fun aRuleIdentifierContainingDotsIsRoundTripped() = runTest {
        // Les clés sont dérivées de l'identifiant : un point dans celui-ci ne
        // doit pas tronquer le nom au moment de la relecture.
        val ruleId = RuleId("wifi.blacklist.v2")

        repository.setRuleSettings(ruleId, RuleSettings(isEnabled = true, priority = 42))

        assertEquals(
            RuleSettings(isEnabled = true, priority = 42),
            repository.currentRuleSettings()[ruleId],
        )
    }

    @Test
    fun resettingARuleRestoresItsDefaults() = runTest {
        repository.setRuleSettings(RuleId("airplane-mode"), RuleSettings(false, 10))

        repository.resetRuleSettings(RuleId("airplane-mode"))

        assertTrue(repository.currentRuleSettings().isEmpty())
    }

    @Test
    fun aPartiallyWrittenOverrideIsIgnored() = runTest {
        // Écriture interrompue ou migration incomplète : mieux vaut revenir aux
        // valeurs par défaut qu'appliquer une surcharge à moitié définie.
        dataStore.edit { it[SettingsKeys.ruleEnabled("orpheline")] = false }

        assertTrue(repository.currentRuleSettings().isEmpty())
        assertNull(repository.currentRuleSettings()[RuleId("orpheline")])
    }
}
