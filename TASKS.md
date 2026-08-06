# Feuille de route

Ordre **impératif**. Aucune étape n'est sautée, aucune n'est fusionnée avec la
suivante. Chaque étape se termine par un commit vérifié ; les étapes
s'enchaînent sans validation intermédiaire (voir [AGENTS.md](./AGENTS.md) §1).

Légende : `[x]` terminé · `[~]` partiel · `[ ]` à faire

> **Les dix-sept étapes sont terminées.** L'étape 17 a remplacé le mécanisme
> d'observation retenu à l'étape 11, inopérant par construction ; l'automatisation
> en arrière-plan est désormais vérifiée sur terminal réel.
>
> Les points ouverts ne bloquent pas : deux dépendent d'outils tiers, deux se
> font hors du dépôt.
>
> | Point | Étape | Nature |
> |---|---|---|
> | Mode avion sans changement de réseau | 11 | mesure sur terminal |
> | `lint` sur les sources de test | 5 | défaut AGP 9.3.1 |
> | Robolectric sur l'API 37 | 3 | version à paraître |
> | Captures d'écran, procédure Play Console | 15 | hors du dépôt |
> | Protection de la branche `main` | 16 | interface GitHub |

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
- [x] Kover 0.9.9, seuil bloquant sur `:domain`, intégré à la CI (relevé à
      98 % à l'étape 16, le domaine étant intégralement couvert)

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

## 9. ViewModels `[x]`

Deux commits : le cas d'usage, puis les ViewModels.

- [x] `SynchronizeTunnelUseCase` — seul composant connaissant l'enchaînement
      complet. Deux formes d'appel : sans argument (relit le réseau, pour la
      synchronisation manuelle) et avec un contexte déjà stabilisé
- [x] `SynchronizationOutcome` — six issues distinctes plutôt qu'un booléen
- [x] `HomeViewModel`, `BlacklistViewModel`, `SettingsViewModel`,
      `JournalViewModel`, un `StateFlow<UiState>` chacun
- [x] `MainDispatcherRule` pour substituer `Dispatchers.Main` en test
- [x] 38 tests (13 sur le cas d'usage, 25 sur les ViewModels)

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès, 166 tests, 0 échec.

> [SPECS.md](./SPECS.md) §3.3 a été précisé au passage : « on n'écrit au journal
> que lorsque l'état change réellement » n'était pas un critère observable, le
> canal de commande étant asynchrone et sans accusé de réception. Le critère
> retenu est désormais explicite, et l'écart résiduel documenté.

---

## 10. Interface Compose `[x]`

- [x] `AppNavHost` — seul endroit où l'état rejoint les écrans
- [x] Écran d'accueil ([SPECS.md](./SPECS.md) §6.1) : état du tunnel, réseau,
      SSID, dernier changement, bouton Synchroniser
- [x] Écran blacklist : CRUD complet + ajout du SSID courant en un geste
- [x] Libellés — `RuleId`, `TunnelState`, `NetworkTransport`, `BlacklistError`
      traduits **dans la présentation**, `when` exhaustifs sans branche `else`
- [x] `@Preview` sans injection sur les deux écrans
- [x] 23 tests Compose sous Robolectric, repérage par `testTag`
- [x] Explication préalable à la demande de permission de localisation, livrée
      à l'étape 14 : elle dépendait de `SystemStatus`, introduit à l'étape 12
- [x] Barre de navigation, livrée à l'étape 13 avec la quatrième destination

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès.

---

## 11. Notifications et déclenchement `[x]`

> **Écart assumé avec l'intitulé initial.** Cette étape prévoyait un *service de
> premier plan*. Sur Android 8 et suivants, un tel service **impose** une
> notification permanente — ce qui contredit [SPECS.md](./SPECS.md) §7, qui
> l'exige optionnelle et désactivée par défaut. La spécification l'emporte :
> l'application se fait **réveiller** par le système plutôt que de veiller
> elle-même.

- [x] Canal de notification, importance basse (sinon un son à chaque
      changement de réseau)
- [x] Notification persistante optionnelle : état du tunnel + règle appliquée
- [x] `POST_NOTIFICATIONS` demandée **uniquement** si l'option est activée ;
      son absence est un cas nominal
- [x] `NetworkCallbackTrigger` — `registerNetworkCallback(request, PendingIntent)` :
      le système réveille l'application, qui ne consomme rien entre-temps
- [x] `NetworkChangeReceiver` et `BootReceiver`, tous deux non exportés,
      prolongés par `goAsync()`
- [x] `AutomationCoordinator` — charnière entre préférences et plateforme
- [x] L'`Application` observe les préférences : un changement prend effet
      immédiatement, pas au redémarrage suivant
- [x] 16 tests (7 sur le notifier, 9 sur le coordinateur)

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès, 206 tests, 0 échec.

### Suivi ouvert

- [ ] **Bascule du mode avion sans changement de réseau.** Le réveil est
      déclenché par les changements réseau ; couper le mode avion alors que le
      Wi-Fi reste associé pourrait n'en produire aucun. À mesurer sur terminal
      réel avant d'ajouter un déclencheur dédié.
- [ ] **Debounce sur le chemin des réveils.** Chaque réveil déclenche une
      synchronisation. L'effet est amorti par le court-circuit « état déjà
      atteint » du cas d'usage : une rafale produit au plus une commande et une
      entrée de journal. À revoir si la mesure montre le contraire.

---

## 12. Paramètres `[x]`

- [x] Écran complet ([SPECS.md](./SPECS.md) §6.3) : quatre bascules, avis
      d'automatisation désactivée, à-propos
- [x] `SystemStatus` — abstraction de ce que seule la plateforme sait
      (permission de notification, exemption de batterie, version), pour que le
      ViewModel reste testable hors Android
- [x] `refreshSystemStatus()` — ces deux réglages se modifient **hors** de
      l'application ; sans reconstat au retour, l'écran resterait sur un état
      périmé
- [x] Demande de permission de notification proposée **uniquement** une fois
      l'option activée
- [x] Exemption d'optimisation de batterie proposée seulement si elle manque
- [x] Version et licence
- [x] 18 tests (9 sur le ViewModel, 9 sur l'écran)

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès, 220 tests, 0 échec.

> L'écran n'est pas encore atteignable : la barre de navigation arrive avec
> l'écran Journal (étape 13), les quatre destinations n'ayant de sens
> qu'ensemble.

---

## 13. Journal et navigation `[x]`

- [x] Écran du journal, du plus récent au plus ancien
- [x] Ancien état → nouvel état, règle lisible, horodatage localisé
- [x] Purge à 500 entrées — déjà garantie par le repository (étape 8), l'écran
      n'en refait rien
- [x] Effacement, confirmé par un dialogue car irréversible, et proposé
      seulement s'il y a quelque chose à effacer
- [x] `formatJournalTimestamp` — fuseau et langue en paramètres, donc testable
      sans dépendre des réglages de la machine
- [x] **Barre de navigation** entre les quatre destinations, pilotée par
      l'énumération `AppDestination`
- [x] Les quatre écrans sont désormais atteignables
- [x] 12 tests (8 sur l'écran, 4 en JVM pur sur la mise en forme)

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès, 232 tests, 0 échec.

> L'exemption d'optimisation de batterie ouvre la fiche de l'application dans
> les réglages système, et non `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` :
> cette dernière est proscrite par le Play Store hors des rares catégories qui
> la justifient, et son seul usage suffit à faire rejeter une publication.

---

## 14. Tests UI `[x]`

Écrits **au fil de l'eau** plutôt que repoussés ici, comme
[AGENTS.md](./AGENTS.md) §4 l'exige. Cette étape n'a donc comblé que les
manques.

- [x] Ajout d'un SSID (dialogue), suppression, renommage pré-rempli
- [x] Affichage de l'état sur l'accueil, y compris client absent et SSID
      indisponible
- [x] Bascule de chacun des quatre paramètres
- [x] Journal : transitions, règle, horodatage, effacement confirmé
- [x] Barre de navigation : sélection, route inconnue, unicité des routes
- [x] Explications de permission (localisation, notification)

**Total : 43 tests Compose** sous Robolectric, repérés par `testTag`.

### Rendu visuel (Roborazzi)

Ajouté après coup : les tests ci-dessus vérifient *ce qui est affiché*, pas *à
quoi cela ressemble*.

- [x] Roborazzi 1.70.0, rendu graphique natif de Robolectric
- [x] **22 références** — les 11 états les plus exposés des quatre écrans, en
      thème clair **et** sombre
- [x] Références versionnées dans `app/src/test/screenshots/` : une revue voit
      le changement visuel dans le diff
- [x] Couleur dynamique désactivée et format d'écran figé, sans quoi les
      références dépendraient du fond d'écran ou de la configuration par défaut
      de Robolectric
- [x] **Hors CI et hors commande de vérification standard**, délibérément : le
      rendu graphique natif coûte plusieurs minutes de temps machine, trop cher
      à chaque Pull Request
- [x] Tas à 2 Go et recyclage de JVM (`forkEvery`), appliqués **uniquement** aux
      exécutions Roborazzi — sans quoi la CI paierait ces réglages pour rien

> **Conséquence assumée.** Une régression visuelle n'est rattrapée
> automatiquement par personne. Le filet repose sur l'auteur d'un changement
> d'interface, qui lance `:app:verifyRoborazziDebug` avant de committer. C'est
> rappelé dans [AGENTS.md](./AGENTS.md) §4, [CONTRIBUTING.md](./CONTRIBUTING.md)
> et le gabarit de Pull Request.
>
> À reconsidérer si des régressions visuelles passent réellement en revue — le
> coût serait alors justifié.

**Ce que ça a trouvé dès la première exécution :** sur les cartes d'action, les
boutons « Autoriser » et « Ouvrir les réglages » conservaient la couleur
primaire et devenaient illisibles sur le fond teinté en thème sombre. Aucune
assertion textuelle ne pouvait le détecter.

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès, 243 tests, 0 échec.

---

## 15. Documentation `[x]`

- [x] README : ce que l'application **ne fait pas** (pas de veille permanente,
      pas de lecture de position, aucun envoi), commande de vérification à jour
- [x] ARCHITECTURE.md aligné sur le code livré, §9.1 sur le déclenchement,
      25 décisions d'architecture tracées
- [x] SPECS.md §10.1 clos par le résultat du spike, §3.3 précisé par
      l'implémentation
- [x] CHANGELOG, réserves connues comprises

### Reste à faire avant publication

- [ ] **Captures d'écran réelles** — demandent un terminal ou un émulateur ;
      les emplacements sont en place dans le README.
- [ ] **Procédure de publication détaillée** — la trame est dans le README ;
      elle se complète au premier envoi réel sur la Play Console.

---

## 16. GitHub Actions `[x]`

Un workflow minimal existait dès l'étape 1, pour que le dépôt ne soit jamais
sans filet. Cette étape le complète.

- [x] Workflow de PR : `ktlintCheck`, `detekt`, `lint`, `test`,
      `:domain:koverVerify`, `assembleDebug` — **en étapes séparées**, pour que
      la première défaillance se nomme elle-même
- [x] Rapport de couverture publié dans le résumé de la PR
- [x] Cache Gradle, **en lecture seule hors de `main`** : une branche de PR ne
      doit pas pouvoir empoisonner le cache partagé
- [x] `permissions: contents: read` — ce workflow vérifie, il ne publie rien
- [x] Gabarit de Pull Request reprenant les critères de `CONTRIBUTING.md`
- [x] Rapports des deux modules conservés en artefacts

### À faire dans l'interface GitHub

La protection de branche ne se configure pas depuis le dépôt. Sur
`Settings → Branches → Add rule` pour `main` :

- [ ] *Require a pull request before merging*
- [ ] *Require status checks to pass* → cocher **Compilation, lint, tests, couverture**
- [ ] *Require branches to be up to date before merging*
- [ ] *Do not allow bypassing the above settings*

Tant que ce n'est pas fait, la CI signale sans bloquer.

---

## 17. Observation réseau en arrière-plan `[x]`

**L'automatisation ne fonctionne pas hors de l'application.** Constaté sur
Pixel 10 Pro : passer en 5G n'active pas le tunnel, revenir en Wi-Fi ne le
désactive pas. Seule une ouverture de l'application produit un effet.

### Ce qui a été mesuré

Le mécanisme retenu à l'étape 11 —
`ConnectivityManager.registerNetworkCallback(NetworkRequest, PendingIntent)` —
ne peut pas tenir sa promesse. Deux faits, tirés du journal système :

1. L'inscription livre l'état courant **immédiatement**, dans la milliseconde ;
2. `ConnectivityService` la **relâche cinq secondes après cette livraison**.

```
22:19:00.820  REGISTER … to trigger PendingIntent{5d98ada}
22:19:00.821  ConnectivityService: Sending PendingIntent{5d98ada}
22:19:00.893  ConnectivityService: Finished sending PendingIntent{5d98ada}
22:19:05.898  RELEASE  … callbackRequest: 57492
```

Une inscription ne vaut donc que pour **un seul réveil, consommé sur-le-champ**.
Aucun changement ultérieur n'est jamais observé. Réarmer à chaque réveil
rétablit la couverture mais chaque réarmement provoque sa propre livraison
immédiate : **463 réveils en 50 secondes**, mesuré. Les deux issues sont
inacceptables.

Conclusion : observer le réseau en arrière-plan exige un processus vivant. La
décision d'architecture n°14 tombe.

### Ce qui la remplace — arbitré avec le porteur du projet

Un réglage « réactivité immédiate », laissé à l'utilisateur :

- [x] **Activé** — `TunnelWatchService`, service de premier plan et notification
      permanente. Bascule instantanée. La notification devient le prix explicite
      de l'instantanéité.
- [x] **Désactivé** — `PeriodicSyncWorker`, vérification toutes les 15 min
      (minimum imposé par la plateforme), sans notification ni service.
- [x] `AndroidAutomationTrigger` bascule entre les deux et n'en laisse jamais
      deux actifs
- [x] `AppSettings` porte le réglage, ainsi que les deux règles dérivées
      `notificationIsUnavoidable` et `notificationIsVisible`
- [x] `SPECS.md` §7.1 énonce le compromis au lieu de promettre une notification
      toujours optionnelle ; §8 corrige le caractère des permissions
- [x] `ARCHITECTURE.md` §9.1 et décision n°21 : cause mesurée consignée
- [x] `NetworkCallbackTrigger` et `NetworkChangeReceiver` retirés
- [x] Le service consomme `NetworkObserver.observe()`, donc le debounce du
      domaine s'applique enfin — ce qui clôt le point ouvert de l'étape 11
- [x] WorkManager reçoit la fabrique de workers de Hilt, son auto-initialisation
      étant retirée du manifeste

**Vérifié sur Pixel 10 Pro**, processus tué par `am kill`, aller-retour
Wi-Fi ↔ cellulaire :

```
23:15:17  Contexte observé : CELLULAR, validé  → mobile-network → ENABLED
23:16:18  Contexte observé : WIFI, validé      → other-wifi     → ENABLED
```

Le service redémarre seul après la mort du processus (`START_STICKY`) et traite
les deux bascules.

### Deux défauts découverts par cette vérification, corrigés

**1. Le Wi-Fi mettait des minutes à l'emporter sur le cellulaire.**
Android conserve le réseau mobile actif *et validé* longtemps après une bascule
vers le Wi-Fi. Le réseau retenu était le premier livré parmi les validés, donc
le cellulaire : le tunnel ne réagissait qu'au démontage effectif du mobile.

- [x] `PreferredNetwork` départage comme Android le fait pour son réseau par
      défaut : validé d'abord, puis filaire > Wi-Fi > cellulaire
- [x] `current()` laisse retomber la rafale d'inscription avant de décider,
      la première émission ne portant qu'un seul réseau

**2. Le SSID était illisible en arrière-plan.**
`ssid=null` dès que l'application n'était plus au premier plan, permission de
localisation pourtant accordée : la règle des réseaux de confiance ne pouvait
jamais s'appliquer. Android ne laisse un service accéder à la localisation —
dont relève le SSID — que s'il se déclare de type `location`.

- [x] `TunnelWatchService` déclare `specialUse|location`, et n'ajoute le second
      type **que si la permission est accordée** : le déclarer sans la détenir
      fait rejeter le démarrage du service

**Revérifié sur appareil**, processus tué, aller-retour complet :

```
23:36:04  CELLULAR              → mobile-network   → Applied DISABLED→ENABLED
23:36:29  WIFI, ssid=::1        → blacklisted-wifi → Applied ENABLED→DISABLED
```
