package fr.vbrosseau.tailscaleautorules.di

import javax.inject.Qualifier

/**
 * Portée de coroutines vivant aussi longtemps que le processus.
 *
 * Réservée au travail qui ne doit pas être annulé avec un écran : un cycle de
 * synchronisation déclenché par une diffusion, notamment.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
