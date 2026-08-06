package fr.vbrosseau.tailscaleautorules.domain.model

/**
 * Forme canonique d'un SSID, utilisée pour toute comparaison.
 *
 * SPECS.md §4.2 impose une comparaison insensible à la casse et indifférente
 * aux espaces de bordure. Centraliser la normalisation ici évite que deux
 * endroits du code comparent différemment — un « Maison » enregistré ne doit
 * jamais échouer à reconnaître un « maison » diffusé.
 */
fun String.asSsidKey(): String = trim().lowercase()
