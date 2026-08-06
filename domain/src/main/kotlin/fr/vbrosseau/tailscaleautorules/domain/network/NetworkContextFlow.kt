package fr.vbrosseau.tailscaleautorules.domain.network

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Fenêtre de stabilisation par défaut.
 *
 * Une transition réseau réelle produit une rafale d'événements — association,
 * obtention d'adresse, validation Internet — étalée sur un à deux secondes.
 * Évaluer chacun d'eux ferait osciller le tunnel sans raison.
 */
val DefaultStabilizationWindow: Duration = 2.seconds

/**
 * Stabilise un flux brut de contextes réseau.
 *
 * Deux filtres complémentaires, dans cet ordre :
 *
 * 1. [debounce] absorbe la rafale : seule la dernière valeur d'une fenêtre
 *    silencieuse est émise.
 * 2. [distinctUntilChanged] évite de réévaluer un contexte identique au
 *    précédent — c'est l'égalité structurelle de [NetworkContext] qui rend ce
 *    filtre efficace.
 *
 * La fenêtre est un paramètre, jamais une constante interne : les tests la
 * pilotent en temps virtuel, et elle reste ajustable sans toucher à la logique.
 */
@OptIn(FlowPreview::class)
fun Flow<NetworkContext>.stabilized(window: Duration = DefaultStabilizationWindow): Flow<NetworkContext> =
    debounce(window).distinctUntilChanged()
