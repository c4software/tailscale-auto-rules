package fr.vbrosseau.tailscaleautorules.domain.rule

/**
 * Priorités des règles livrées, rassemblées ici pour être comparables d'un
 * coup d'œil — c'est leur ordre relatif qui porte le sens, pas leur valeur.
 *
 * L'échelle est espacée de 100 afin qu'une règle future puisse s'intercaler
 * sans renuméroter les existantes.
 */
object Priorities {
    const val AIRPLANE_MODE = 100
    const val BLACKLISTED_WIFI = 200
    const val OTHER_WIFI = 300
    const val MOBILE_NETWORK = 400
}
