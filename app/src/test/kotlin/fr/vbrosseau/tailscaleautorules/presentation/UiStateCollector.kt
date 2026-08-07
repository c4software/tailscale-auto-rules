package fr.vbrosseau.tailscaleautorules.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Maintient un abonné sur [flow] pendant toute la durée du test.
 *
 * Les ViewModels publient leur état en `WhileSubscribed` : sans écran — donc
 * sans abonné — les sources ne sont pas observées et `uiState.value` resterait
 * figé sur l'état initial. Le collecteur vit dans `backgroundScope` et meurt
 * avec le test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.keepCollecting(flow: Flow<*>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
}
