# Feuille de route

Ordre **impératif**. Aucune étape n'est sautée, aucune n'est fusionnée avec la
suivante. Chaque étape se termine par un commit vérifié ; les étapes
s'enchaînent sans validation intermédiaire (voir [AGENTS.md](./AGENTS.md) §1).

Légende : `[x]` terminé · `[ ]` à faire

---

## 1. Initialisation du projet `[x]`

Squelette Gradle et chaîne de qualité opérationnels.

- [x] Wrapper Gradle 9.6.1, toolchain JDK 21 (Azul, via `gradle-daemon-jvm.properties`)
- [x] Catalogue de versions `gradle/libs.versions.toml`
- [x] Module `:app` — `minSdk 26`, `compileSdk 37`, `targetSdk 37`, JVM 17
- [x] Compose androidx (BOM) + Material 3, thème `AppTheme` avec couleur dynamique
- [x] `MainActivity` affichant un écran d'attente, prévisualisable
- [x] ktlint + Detekt (`config/detekt/detekt.yml`) + `.editorconfig`
- [x] Documentation de travail (les 7 fichiers racine) et licence MIT

**Vérifié :** `./gradlew ktlintCheck detekt lint test assembleDebug` → succès.

---

## 2. Architecture `[x]`

Matérialiser les couches, sans logique métier.

- [x] Module Kotlin/JVM `:domain`, sans SDK Android — classpath réduit à
      `kotlin-stdlib`, `:app` dépend de `:domain`
- [x] Paquet `domain/model/` ; les autres paquets de
      [ARCHITECTURE.md](./ARCHITECTURE.md) §2 sont créés avec leur contenu, pas
      en avance
- [x] Premiers types du modèle : `TunnelState`, `NetworkTransport`,
      `NetworkContext`, `RuleDecision`
- [x] 12 tests JVM : invariants de `NetworkContext`, valeurs par défaut,
      propriétés dérivées, égalité, revalidation par `copy`

**Vérifié :** `./gradlew ktlintCheck detekt lint test assembleDebug` → succès,
12 tests, 0 échec, hors émulateur.

---

## 3. Configuration Hilt `[x]`

- [x] Plugin Hilt 2.60.1 + KSP 2.3.11, `TailscaleAutoRulesApplication`
      (`@HiltAndroidApp`), `MainActivity` (`@AndroidEntryPoint`)
- [x] `DispatcherModule` — `@IoDispatcher`, `@DefaultDispatcher`,
      `@MainDispatcher`, seul endroit référençant `kotlinx.coroutines.Dispatchers`
- [x] Test Robolectric + `HiltAndroidRule` : construction du graphe et
      résolution de chaque qualifier

**Vérifié :** `./gradlew ktlintCheck detekt lint test assembleDebug` → succès,
14 tests, 0 échec.

> Robolectric 4.16.1 ne fournit pas d'image pour l'API 37 ; les tests JVM
> Android s'exécutent sur l'API 35 (`app/src/test/resources/robolectric.properties`).
> À relever dès qu'une version supporte l'API cible.

---

## 4. Couche Tailscale `[x]`

- [x] **Spike concluant** : `com.tailscale.ipn` expose `IPNReceiver`
      (`exported`, sans permission) avec les actions `CONNECT_VPN` et
      `DISCONNECT_VPN`. Résultat consigné dans [SPECS.md](./SPECS.md) §10.1
- [x] Interface `TailscaleController` dans `:domain` — `isAvailable`, `enable`,
      `disable`, `isRunning`
- [x] `FakeTailscaleController` dans `domain/testFixtures`, partagé par les
      deux modules
- [x] `AndroidTailscaleController` — diffusion explicite, `<queries>`,
      détection du tunnel via `ConnectivityManager`
- [x] Détection de la présence du client, `TailscaleUnavailableException`
- [x] 12 tests (6 sur le Fake, 6 Robolectric sur l'implémentation Android)

**Vérifié :** `./gradlew ktlintCheck detekt lint test assembleDebug` → succès,
26 tests, 0 échec.

> `NoOpTailscaleController` n'a pas été créé : l'absence de client est déjà un
> échec explicite d'`AndroidTailscaleController`. Une classe de plus n'aurait
> ajouté qu'un chemin de code à maintenir.

---

## 5. Observation réseau `[x]`

- [x] `NetworkObserver` (domaine) : `observe()` stabilisé et `current()` pour la
      synchronisation manuelle, qui ne doit jamais être retardée
- [x] `AndroidNetworkObserver` — `NetworkCallback` en `callbackFlow`, valeur
      initiale émise, désinscription dans `awaitClose`
- [x] Wi-Fi, cellulaire, Ethernet, validation Internet
- [x] Mode avion : `Settings.Global` + `ACTION_AIRPLANE_MODE_CHANGED`
- [x] SSID : `NetworkCapabilities.transportInfo` (API 31+), repli
      `WifiManager` avant ; valeur système de repli traitée comme indisponible
- [x] `stabilized(window)` — opérateur **du domaine**, fenêtre injectée,
      `debounce` + `distinctUntilChanged`
- [x] `FakeNetworkObserver` dans `testFixtures`, flux volontairement non
      stabilisé pour que les tests gardent la main sur la fenêtre
- [x] 13 tests (7 en temps virtuel sur le debounce, 6 Robolectric)

**Vérifié :** `./gradlew ktlintCheck detekt lint test assembleDebug` → succès,
39 tests, 0 échec.

### Suivi ouvert

- [ ] **Réactiver `lint` sur les sources de test.** Android Lint (AGP 9.3.1)
      plante sur ses propres composants d'analyse Kotlin en visitant nos tests
      (`SymbolLightClassForClassOrObject.isRecord`). `ignoreTestSources = true`
      dans `app/build.gradle.kts` le contourne ; les sources de production
      restent analysées. À lever dès qu'une version d'AGP corrige le plantage.

---

## 6. Moteur de règles `[x]`

- [x] Contrat `Rule` — `id`, `defaultSettings`, `evaluate(RuleContext)` pure
- [x] `RuleContext` — état réseau **et** configuration utilisateur, ce qui
      garde `evaluate` sans dépendance
- [x] `RuleEngine` — filtre les règles actives, trie par priorité puis par
      identifiant, s'arrête à la première décision ferme
- [x] `RuleEvaluation` — décision + règle l'ayant rendue, invariant vérifié
- [x] `AirplaneModeRule` (100), `BlacklistedWifiRule` (200), `OtherWifiRule`
      (300), `MobileNetworkRule` (400)
- [x] `RuleModule` — enregistrement via `@Provides @IntoSet`, `:domain` restant
      exempt de toute annotation d'injection

**Fait :** ajouter une règle = une classe dans `:domain/rule/` + une ligne dans
`RuleModule`. Le moteur n'est pas touché.

---

## 7. Tests unitaires du moteur `[x]`

- [x] Ordre de priorité, priorités égales départagées par identifiant (ordre
      total, vérifié sur 10 exécutions)
- [x] Arrêt à la première décision ferme, **et** non-évaluation des suivantes
- [x] Règles désactivées jamais évaluées ; toutes désactivées → `NO_DECISION`
- [x] Ensemble vide → `NO_DECISION`
- [x] Surcharge utilisateur de la priorité et de l'activation
- [x] Chaque branche de chaque règle, `NO_DECISION` compris
- [x] `ShippedRulesTest` rejoue le tableau de [SPECS.md](./SPECS.md) §4 avec
      l'ensemble réel de règles
- [x] Kover 0.9.9, seuil bloquant à 95 % sur `:domain`, intégré à la CI

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès, 81 tests, 0 échec.

Couverture `:domain` — **`rule` et `engine` : 100 % d'instructions et 100 % de
branches**. Total module : 95,5 % d'instructions, 100 % de branches.

---

## 8. Persistance `[x]`

Découpée en trois commits — contrats, Room, DataStore — la traiter d'un bloc
aurait produit un diff illisible.

- [x] Contrats du domaine : `BlacklistRepository`, `JournalRepository`,
      `SettingsRepository`, plus l'abstraction `Clock`
- [x] Room : blacklist avec **index unique sur la forme canonique** du SSID —
      l'unicité est garantie par la base, pas par une vérification applicative
- [x] Room : journal, purge à 500 entrées **dans la transaction d'insertion**
- [x] Schémas Room exportés et versionnés (`app/schemas/`)
- [x] DataStore : préférences de [SPECS.md](./SPECS.md) §6.3
- [x] DataStore : `isEnabled` / `priority` par règle, clés dérivées de
      l'identifiant — ajouter une règle ne demande aucune migration
- [x] Quatre Fakes appliquant réellement ces règles, unicité et purge comprises
- [x] 47 tests : 12 sur les Fakes, 21 Room sur base SQLite en mémoire, 10 sur
      DataStore, 4 sur les nouveaux modèles

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès, 128 tests, 0 échec.

---

## 9. ViewModels `[ ]`

- [ ] `SynchronizeTunnelUseCase` dans `:domain` + ses tests
- [ ] `HomeViewModel`, `BlacklistViewModel`, `SettingsViewModel`, `JournalViewModel`
- [ ] Un `StateFlow<UiState>` par ViewModel, aucune logique métier
- [ ] Tests avec Fakes et dispatcher de test

---

## 10. Interface Compose `[ ]`

- [ ] `AppNavHost` et destinations
- [ ] Écran d'accueil ([SPECS.md](./SPECS.md) §6.1)
- [ ] Écran blacklist, CRUD complet + ajout du SSID courant
- [ ] Composants réutilisables, `@Preview` sans injection
- [ ] Écran d'explication préalable à la demande de permission de localisation

---

## 11. Notifications `[ ]`

- [ ] Canal de notification
- [ ] Notification persistante optionnelle (tunnel + raison)
- [ ] Demande de `POST_NOTIFICATIONS` **uniquement** si l'option est activée
- [ ] Service de premier plan et son cycle de vie
- [ ] Receiver `BOOT_COMPLETED`

---

## 12. Paramètres `[ ]`

- [ ] Écran de paramètres complet ([SPECS.md](./SPECS.md) §6.3)
- [ ] Exemption d'optimisation de batterie
- [ ] Version et licence
- [ ] Tests du ViewModel

---

## 13. Journal `[ ]`

- [ ] Écran du journal, du plus récent au plus ancien
- [ ] Ancien état → nouvel état, règle, raison, horodatage
- [ ] Purge vérifiée à 500 entrées
- [ ] Action d'effacement

---

## 14. Tests UI `[ ]`

- [ ] Ajout d'un SSID
- [ ] Suppression d'un SSID
- [ ] Affichage de l'état sur l'accueil
- [ ] Bascule des paramètres

---

## 15. Documentation `[ ]`

- [ ] README complété : captures d'écran réelles, procédure de publication
- [ ] ARCHITECTURE.md aligné sur le code livré
- [ ] SPECS.md §10.1 clos par le résultat du spike
- [ ] CHANGELOG

---

## 16. GitHub Actions `[ ]`

- [ ] Workflow de PR : `ktlintCheck`, `detekt`, `lint`, `test`, `assembleDebug`
- [ ] Rapport de couverture
- [ ] Cache Gradle
- [ ] Branche `main` protégée : aucune fusion si une étape échoue

> Un workflow minimal est présent dès l'étape 1 pour que le dépôt ne soit jamais
> sans filet. Cette étape le complète (couverture, matrice, publication).
