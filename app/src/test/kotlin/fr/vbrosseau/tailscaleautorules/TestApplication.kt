package fr.vbrosseau.tailscaleautorules

import android.app.Application

/**
 * Application employée par les tests Robolectric, à la place de la vraie.
 *
 * [TailscaleAutoRulesApplication] lance à sa création une observation des
 * réglages qui vit aussi longtemps que le processus. Dans un test, ce processus
 * est celui de la JVM : la coroutine survivait donc au test qui l'avait
 * déclenchée et retombait sur un environnement Robolectric déjà démonté. Le
 * symptôme était trompeur — un `UncaughtExceptionsBeforeTest` frappait le test
 * *suivant*, souvent dans une classe sans rapport.
 *
 * Un test unitaire n'a de toute façon pas à démarrer l'application entière :
 * chacun compose les objets qu'il éprouve.
 */
class TestApplication : Application()
