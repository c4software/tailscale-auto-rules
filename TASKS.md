# Feuille de route

Ordre **impératif**. Aucune étape n'est sautée, aucune n'est fusionnée avec la
suivante. Chaque étape se termine par un commit vérifié ; les étapes
s'enchaînent sans validation intermédiaire (voir [AGENTS.md](./AGENTS.md) §1).

Légende : `[x]` terminé · `[~]` partiel · `[ ]` à faire

> **Les dix-sept étapes de la version initiale sont terminées.** L'étape 17 a
> remplacé le mécanisme d'observation retenu à l'étape 11, inopérant par
> construction ; l'automatisation en arrière-plan est vérifiée sur terminal
> réel. Les étapes 18 à 23 ajoutent les **exceptions dynamiques** —
> mémorisation des gestes manuels par réseau ([SPECS.md](./SPECS.md) §3.3 et
> §4.5).
>
> Les points ouverts ne bloquent pas : deux dépendent d'outils tiers, un se
> mesure sur terminal, un se règle hors du dépôt.
>
> | Point | Étape | Nature |
> |---|---|---|
> | Mode avion sans changement de réseau | 11 | mesure sur terminal |
> | `lint` sur les sources de test | 5 | défaut AGP 9.3.1 |
> | Robolectric sur l'API 37 | 3 | version à paraître |
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

- [x] **Captures d'écran réelles** — six écrans par langue, capturés sur
      émulateur Pixel 6 (API 36) depuis l'étiquette `v0.4.1`, dans
      [`store/screenshots/`](./store/screenshots). États réels : préférences
      saisies dans l'application, journal issu de bascules Wi-Fi ↔ données
      mobiles.
- [x] **Dossier de soumission** — textes des deux fiches, icône 512, image mise
      en avant, réponses aux formulaires (sécurité des données, contenu,
      déclarations de service de premier plan) et politique de confidentialité,
      dans [`store/`](./store).
- [x] **Politique de confidentialité publiée par la CI** — le workflow
      `pages.yml` la régénère sur GitHub Pages à chaque modification poussée sur
      `main` ; le texte déclaré à Google ne peut pas diverger du dépôt. URL dans
      [`store/README.md`](./store/README.md).
- [x] **Procédure de publication détaillée** — l'ordre de saisie est dans
      [`store/README.md`](./store/README.md) ; il a été confirmé par l'envoi réel
      sur la Play Console, l'application est publiée.

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

### Ce qui la remplace

- [x] `TunnelWatchService`, service de premier plan, observe le réseau en
      continu et bascule en quelques secondes
- [x] `AndroidAutomationTrigger` le démarre et l'arrête selon l'automatisation
- [x] `NetworkCallbackTrigger` et `NetworkChangeReceiver` retirés
- [x] Le service consomme `NetworkObserver.observe()`, donc le debounce du
      domaine s'applique enfin — ce qui clôt le point ouvert de l'étape 11
- [x] `SPECS.md` §7.1 et §8, `ARCHITECTURE.md` §9.1 et décisions n°21 / 21b :
      cause mesurée consignée

- [x] La notification n'est pas un réglage : elle est visible exactement quand
      l'automatisation l'est. L'écran des paramètres l'**explique** au lieu de
      l'offrir — un interrupteur grisé promettrait un choix inexistant, et
      coché-désactivé il se lirait presque comme éteint

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

---

## 18. Exceptions dynamiques — spécification `[x]`

Le geste manuel devient une **mémoire par réseau** ([SPECS.md](./SPECS.md)
§3.3), là où la spécification affirmait l'inverse — le geste était respecté
jusqu'au changement de réseau suivant. Amender la spécification **avant** le
code : elle est la référence de toutes les étapes qui suivent.

- [x] §3.3 réécrit : le geste est mémorisé, un nouveau geste remplace
      l'exception, la suppression se fait à l'écran des réseaux de confiance
- [x] §4 : règle « Exception dynamique », priorité 150 ; détail en §4.5
      (clés canoniques `wifi:<ssid>` / `cellular`, portées, cas exclus)
- [x] §6.1 : la carte d'intervention manuelle annonce la mémorisation ;
      invitation unique au premier lancement
- [x] §6.2 : section « Exceptions apprises », suppression par glissement
- [x] §6.3 : réglage « Apprendre mes gestes », activé par défaut
- [x] §7 : la notification attribue l'état mémorisé à « Exception dynamique »
- [x] §9 : table Room des exceptions, une entrée par clé réseau
- [x] §10.3 : point ouvert — un VPN tiers peut être pris pour un geste
- [x] Étapes 18 à 23 consignées ici

---

## 19. Exceptions dynamiques — domaine : modèle et règle `[x]`

- [x] `NetworkException` + `NetworkExceptionKey` (fabrique
      `from(NetworkContext)`, clés de [SPECS.md](./SPECS.md) §4.5,
      canonicalisation partagée avec la blacklist)
- [x] Contrat `NetworkExceptionRepository` (`observeAll`, `current`, `upsert`,
      `remove`) + `FakeNetworkExceptionRepository` dans `testFixtures`
- [x] `RuleContext.networkExceptions` — extension prévue par le type, les
      règles existantes ne changent pas
- [x] `NetworkExceptionRule`, priorité 150 (`Priorities`), chaque branche
      testée, `NO_DECISION` compris
- [x] `ShippedRulesTest` rejoue le tableau §4 complété

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès. La règle n'est pas encore enregistrée dans `RuleModule` : le rejeu
n'arrive qu'avec sa source de données, à l'étape 21 — comme l'écran des
paramètres avait précédé sa navigation (étape 12).

---

## 20. Exceptions dynamiques — domaine : capture du geste `[x]`

- [x] `RecordManualOverrideUseCase` — gardes : apprentissage activé, service
      activé, clé dérivable, pas de mode avion ; **upsert** systématique de
      l'état observé ; entrée de journal sous `network-exception`, sans quoi
      le geste suivant serait indétectable (le détecteur compare au journal)
- [x] `AppSettings.isLearningEnabled` (défaut : activé) — le Fake, qui stocke
      l'objet entier, n'avait rien à changer
- [x] Tests JVM : chaque garde, création, remplacement, journalisation

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès. La préférence retombe sur sa valeur par défaut tant que la clé
DataStore n'existe pas (étape 21).

---

## 21. Exceptions dynamiques — data : Room v2 et DataStore `[x]`

- [x] `NetworkExceptionEntity` — table `network_exception`, index unique sur
      la clé réseau, état désiré, horodatage de création
- [x] `NetworkExceptionDao` (upsert transactionnel conservant l'identité) +
      `RoomNetworkExceptionRepository` (lignes illisibles écartées, comme le
      journal)
- [x] **Première migration Room du projet** : version 2, schéma exporté,
      `room-testing`, test de migration préservant blacklist et journal et
      éprouvant l'index unique
- [x] DataStore : `learning_enabled`, `learning_prompted` +
      `AppSettings.isLearningPrompted`
- [x] `EvaluateRulesUseCase` alimente le contexte depuis le repository — câblé
      ici plutôt qu'à l'étape 19 : la liaison Hilt exige l'implémentation
- [x] DI : DAO, migration, repository, `NetworkExceptionRule` dans
      `RuleModule` (seul enregistrement requis). Le rejeu est actif dès cette
      étape ; la capture arrive à l'étape 22

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès. Les schémas sont rattachés aux assets du variant de débogage : AGP
ne fusionne aucun asset pour les tests unitaires, Robolectric ne voit que
ceux du variant ; la release n'embarque rien.

---

## 22. Exceptions dynamiques — automation : brancher la capture `[~]`

- [x] `CaptureManualOverrideUseCase` (domaine) — relit réseau, état et journal
      une seule fois, enchaîne détection puis mémorisation ; le coordinateur
      reste une charnière sans logique métier
- [x] `AutomationCoordinator.onTunnelStateSettled()` — capture sous un `Mutex`
      partagé avec la synchronisation, pour qu'un battement concurrent ne
      bascule pas le tunnel entre le constat et la mémorisation
- [x] `TunnelWatchService.watchTunnel()` appelle le coordinateur après la
      fenêtre de stabilisation, au lieu du seul rafraîchissement de
      notification
- [x] Tests : cycle complet en JVM pur (geste → mémorisé → état auto-expliqué
      → nouveau geste → remplacé) + Robolectric côté coordinateur (geste
      mémorisé puis respecté par les cycles ; écho d'une commande ignoré)
- [~] **Validation sur appareil.** Faite sur émulateur (API 36, sans client
      Tailscale ni session Play) pour tout ce qui n'exige pas un vrai VPN :

      ```
      20:29:48  TunnelWatchService démarré, premier plan, notification posée
      20:29:50  Contexte observé : WIFI validé → Cycle terminé : TailscaleUnavailable
      ```

      - invitation du premier lancement affichée, « Activer » persisté
        (l'invitation ne revient pas après un arrêt forcé), interrupteur des
        paramètres coché en conséquence ;
      - deux exceptions injectées en base (build debug) : section « Exceptions
        apprises » rendue, plus récente d'abord, « Données mobiles » pour la
        sentinelle cellulaire ;
      - suppression par glissement constatée à l'écran **et** en base (la
        ligne disparaît de `network_exception`) ;
      - aucun crash au logcat.

      **Reste à valider sur le Pixel, avec le client Tailscale** : le cycle
      complet geste réel → mémorisation → rejeu au retour sur le réseau,
      battement de secours compris — l'émulateur ne porte aucun VPN, la
      détection ne peut pas s'y déclencher.

      **Premier essai sur Pixel : geste détecté mais non mémorisé.** Deux
      causes, corrigées : la clé de réseau exigeait la validation Internet,
      que le réseau porteur perd fugacement quand le VPN monte (la détection,
      alignée sur la blacklist, passait — la mémorisation refusait) ; et la
      capture ne courait qu'à la stabilisation du tunnel, sans reprise — un
      instantané perturbé à cet instant condamnait le geste à être combattu
      par le battement. La clé est désormais une pure identité (§4.5), et la
      capture est retentée avant chaque cycle.

      **Revérifié sur Pixel 10 Pro après correctif** — le geste (tunnel
      rallumé à la main sur le Wi-Fi de confiance « ::1 ») est mémorisé dès
      le cycle suivant :

      ```
      network_exception : wifi:::1 → ENABLED
      journal : network-exception DISABLED→ENABLED, après blacklisted-wifi ENABLED→DISABLED
      ```

      **Second défaut constaté sur Pixel, corrigé** : couper puis rallumer
      dans la foulée laissait l'exception sur « coupé ». Le contre-geste
      tombait dans le délai de grâce de la mémorisation précédente —
      invisible pour la détection — et rien ne repassait avant le battement.
      L'observation du tunnel reprend désormais le constat une seconde fois,
      une fois la grâce écoulée : le contre-geste est mémorisé en ~12 s.

      Reste à observer sur la durée : le rejeu après un aller-retour de
      réseau, et un battement de secours qui constate `AlreadyInTargetState`
      au lieu de combattre le geste.

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès.

---

## 23. Exceptions dynamiques — interface `[x]`

- [x] Paramètres : interrupteur « Apprendre mes gestes » — sa bascule marque
      aussi l'invitation comme posée, pour qu'elle ne revienne pas
- [x] Accueil : invitation unique au premier lancement (activer / ne pas
      activer), carte d'intervention manuelle reformulée — son texte suit le
      sort réel du geste : mémorisé, ou respecté si l'apprentissage est coupé
- [x] Réseaux de confiance : section « Exceptions apprises » — réseaux
      dérogés uniquement, comportement rejoué, **suppression par glissement**
      (`SwipeToDismissBox`, premier usage du geste dans l'application ; la
      suppression se déclenche à l'aboutissement du glissement, pas dans
      `confirmValueChange`, consulté plusieurs fois par un même geste),
      section absente si vide ; la suppression relance un cycle immédiat
- [x] Libellés de `network-exception` (journal, notification, accueil)
- [x] Tests ViewModels + écrans ;
      références Roborazzi réenregistrées et relues image par image —
      **et la relecture a servi** : sur la carte d'invitation en
      `secondaryContainer`, le bouton « Activer » en `FilledTonalButton` — du
      même conteneur — disparaissait en thème sombre ; passé en bouton plein

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès, 375 tests, 0 échec ; `:app:verifyRoborazziDebug` → succès,
32 références dont 4 nouvelles (invitation d'apprentissage, exceptions
apprises, en clair et sombre).

---

## 24. Parcours de premier lancement `[x]`

Quatre pages qui avancent d'un bouton ou d'un glissement
([SPECS.md](./SPECS.md) §6.5) : bienvenue, notification, localisation,
apprentissage. Les pages tiennent le rôle d'écran d'explication préalable aux
permissions (§8) ; l'invitation d'apprentissage de l'accueil, supplantée, est
retirée.

- [x] `AppSettings.isOnboardingDone` (défaut : faux) + clé DataStore
- [x] `presentation/onboarding/` : écran à pages (pager), points d'étape,
      boutons Autoriser / Continuer, dernière page Activer / Ne pas activer ;
      ViewModel qui clôt le parcours (apprentissage choisi + question posée)
- [x] `MainActivity`/`AppRoot` : le parcours remplace l'ossature tant que le
      premier lancement n'est pas clos ; les lanceurs de permission existants
      sont réutilisés
- [x] Accueil : retrait de l'invitation d'apprentissage (carte, état,
      action, chaînes, tests, références)
- [x] Tests écran + ViewModel ; références Roborazzi (pages en clair et
      sombre), captures relues

**Complément.** Un octroi de permission fait avancer le parcours de
lui-même — la page a rempli son office ; un refus laisse la main
(SPECS §6.5).

**Défaut de harnais corrigé au passage.** Les captures peignaient le fond du
thème sans poser de `Surface` : le texte hors carte restait noir dans toutes
les références sombres — un rendu que l'application, sous son `Scaffold`, n'a
jamais eu. Le harnais pose désormais une `Surface`, et les références sombres
des quatre écrans ont été réenregistrées avec leur vrai contraste.

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès ; `:app:verifyRoborazziDebug` → succès, 34 références.

---

## 25. Préférences de réseau — spécifier la fusion `[x]`

La blacklist et les exceptions dynamiques disent la même chose en deux
endroits : une décision ferme par réseau. Elles fusionnent en une seule
notion, la **préférence de réseau** ([SPECS.md](./SPECS.md) §4.2) — « toujours
coupé » (le réseau de confiance d'hier) ou « toujours actif », l'absence
valant automatisme — alimentée par la déclaration **et** par les gestes.

- [x] §4 : tableau ramené à quatre règles (100, 150, 300, 400) ; §4.2 absorbe
      la blacklist et l'ancien §4.5
- [x] §3.3, §6.1, §6.2 (écran unifié), §7, §9 (une seule table) réécrits
- [x] Étapes 26 à 28 consignées ici

---

## 26. Préférences de réseau — renommage `[x]`

Le renommage seul, pour que chaque commit compile : la sémantique ne bouge
pas ici.

- [x] `NetworkException*` → `NetworkPreference*` partout (modèle, clé,
      contrat, fake, règle, entité, DAO, repository Room, tests)
- [x] `RuleId("network-preference")` ; l'ancien identifiant reste traduisible
      pour les entrées de journal écrites avant la fusion
- [x] Table Room et schémas inchangés — la migration appartient à l'étape 27

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès.

---

## 27. Préférences de réseau — la fusion `[x]`

La suppression de la blacklist traverse les trois couches d'un seul tenant :
retirer la règle sans retirer son écran ne compilerait pas.

- [x] Domaine : `NetworkPreferenceRule` absorbe `BlacklistedWifiRule`
      (supprimée) ; `RuleContext` perd `blacklistedSsids` ;
      `BlacklistRepository` et son Fake supprimés ; le contrat des
      préférences gagne le renommage (`update`, conflit de doublon porté par
      l'index) et la fabrique de clé partagée `forWifi`
- [x] Data : table `network_preference` ; migration 2→3 **fusionnante** — les
      préférences copiées, la blacklist versée en « toujours coupé » là où
      aucun geste n'a déjà tranché (le geste, plus récent, gagne), les deux
      anciennes tables supprimées ; `RoomBlacklistRepository` supprimé ; DI
      réalignée
- [x] Interface : l'écran des réseaux devient la liste unique des
      préférences — volonté modifiable sur place (interrupteur, cycle
      immédiat), ajout avec choix du comportement (« coupé » pré-rempli),
      renommage d'un appui sur le nom, glissement pour rendre le réseau à
      l'automatisme
- [x] Tests : chaque branche de la règle unifiée, `ShippedRulesTest`
      fusionné, migration (fusion, conflit geste/déclaration — le geste
      gagne —, chaîne 1→2→3), ViewModel et écran réécrits

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès ; captures réenregistrées et relues.

---

## 28. Préférences de réseau — renommage de présentation `[x]`

L'écran unifié a été livré avec la fusion (étape 27) ; cette étape aligne les
noms sur la notion.

- [x] Paquet `presentation/blacklist` → `presentation/networks` ; classes,
      étiquettes de test, clés de chaînes et route renommées
- [x] Libellés : `network-preference` traduit ; « Wi-Fi de confiance » et
      « Exception dynamique » restent traduisibles pour l'historique du
      journal
- [x] Références Roborazzi renommées (`networks-*`), réenregistrées et relues
- [x] ARCHITECTURE.md (§2, §3.2, §9, décision n°32) et CHANGELOG à jour

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès, 362 tests, 0 échec ; `:app:verifyRoborazziDebug` → succès.

---

## 29. Traduction anglaise `[x]`

L'anglais devient la **langue par défaut** — toute locale sans traduction
dédiée l'obtient — et le français, langue de référence du projet, vit dans
`values-fr` : les utilisateurs francophones ne voient aucune différence.

- [x] `values/strings.xml` traduit en anglais (111 chaînes), le français
      déplacé tel quel dans `values-fr/`
- [x] Les tests Robolectric et les captures s'exécutent en `fr-rFR`
      (`robolectric.properties`) : les assertions textuelles et les
      références visuelles restent sur la langue de référence
- [x] `lint` passe — les deux langues portent les mêmes clés

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès ; `:app:verifyRoborazziDebug` → succès, références inchangées.

---

## 30. Premier lancement : la permission au bouton « Suivant » `[x]`

Le parcours avançait d'un glissement, ce qui permettait de franchir une page
de permission sans que sa demande ait jamais été posée, et le bouton
« Autoriser » vivait au milieu de la page, sous le texte — donc sous le pli
sur les petits écrans. La demande passe sur le bouton du bas
([SPECS.md](./SPECS.md) §6.5).

- [x] `HorizontalPager` en `userScrollEnabled = false` : le parcours n'avance
      que par le bouton
- [x] Le bouton unique du bas porte la demande de la page : premier appui,
      la demande part ; appui suivant, il redevient « Continuer » et avance
- [x] Mémoire des pages déjà demandées (`rememberSaveable`) : sans elle, un
      refus définitif serait un cul-de-sac — Android ne rouvre plus la boîte
      de dialogue, donc le bouton ne rendrait plus la main
- [x] Retrait des boutons `FilledTonalButton` internes et de leurs repères
      de test ; tests d'écran mis à jour, glissement inerte couvert

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès ; `:app:verifyRoborazziDebug` → succès, références inchangées (les
deux pages capturées ne portaient pas de bouton d'autorisation).

---

## 31. Démarrage au boot : rappel et localisation d'arrière-plan `[x]`

Constaté sur appareil : après un redémarrage du téléphone, le service ne
repartait pas. Une localisation « pendant l'utilisation » est une permission
de premier plan ; Android refuse alors de démarrer depuis `BOOT_COMPLETED` le
service de type « localisation » qu'impose la lecture du SSID — il mourait à
la naissance sur une `SecurityException` ([SPECS.md](./SPECS.md) §8,
décision n°33).

- [x] `AutomationCoordinator.applySettingsAfterBoot` : quand le démarrage
      serait rejeté, une notification « ouvrez l'application pour démarrer la
      synchronisation » remplace la tentative (SPECS.md §7.2) ; canal
      « Rappels » dédié, d'importance normale, retirée dès que le service
      démarre
- [x] `ACCESS_BACKGROUND_LOCATION` en option : carte d'explication aux
      paramètres, visible seulement quand elle servirait (localisation fine
      accordée, démarrage au boot actif) ; `SystemStatus` expose
      `canReadSsidInBackground()`
- [x] Dossier store : déclarations §2/§3 réécrites — l'application demande
      désormais la permission, facultative, et le dit
- [x] Tests coordinateur, notifier, écran et ViewModel ; référence
      `parametres-avertissements` réenregistrée et relue

**Vérifié :** `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`
→ succès, 377 tests, 0 échec ; `:app:verifyRoborazziDebug` → seule la capture
attendue divergeait, réenregistrée puis relue en clair et sombre.
