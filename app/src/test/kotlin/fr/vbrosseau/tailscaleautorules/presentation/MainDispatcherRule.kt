package fr.vbrosseau.tailscaleautorules.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Substitue le dispatcher principal pendant un test.
 *
 * `viewModelScope` s'exécute sur `Dispatchers.Main`, indisponible hors Android.
 * Sans cette règle, tout test de ViewModel échouerait au premier `launch`.
 *
 * Le dispatcher est *unconfined* : les coroutines démarrent immédiatement, si
 * bien qu'un `init` de ViewModel a produit son premier état dès la construction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
