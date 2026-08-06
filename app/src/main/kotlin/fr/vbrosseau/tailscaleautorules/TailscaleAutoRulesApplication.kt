package fr.vbrosseau.tailscaleautorules

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Point d'entrée de l'application et racine du graphe d'injection.
 *
 * Elle ne porte aucune logique : tout ce qui doit vivre à l'échelle du
 * processus est déclaré `@Singleton` dans un module Hilt, jamais initialisé
 * ici. C'est ce qui permet de substituer n'importe quelle dépendance en test.
 */
@HiltAndroidApp
class TailscaleAutoRulesApplication : Application()
