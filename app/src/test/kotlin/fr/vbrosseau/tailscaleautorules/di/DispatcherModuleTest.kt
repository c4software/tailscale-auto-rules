package fr.vbrosseau.tailscaleautorules.di

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Vérifie que le graphe se construit et que chaque qualifier livre le bon
 * dispatcher.
 *
 * La validité du graphe est déjà contrôlée à la compilation par le processeur
 * Hilt ; ce que le compilateur ne peut pas voir, c'est une inversion entre deux
 * qualifiers de même type. C'est précisément ce que ce test couvre.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class DispatcherModuleTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    @Before
    fun injectDependencies() {
        hiltRule.inject()
    }

    @Test
    fun eachQualifierResolvesToItsOwnDispatcher() {
        assertSame(Dispatchers.IO, ioDispatcher)
        assertSame(Dispatchers.Default, defaultDispatcher)
        assertSame(Dispatchers.Main, mainDispatcher)
    }

    @Test
    fun theThreeDispatchersAreDistinct() {
        // Une inversion de qualifiers compile sans erreur : seule une
        // vérification à l'exécution la détecte.
        assertNotSame(ioDispatcher, defaultDispatcher)
        assertNotSame(ioDispatcher, mainDispatcher)
        assertNotSame(defaultDispatcher, mainDispatcher)
    }
}
