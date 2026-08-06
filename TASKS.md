# Feuille de route

Ordre **impératif**. Aucune étape n'est sautée, aucune n'est fusionnée avec la
suivante. Chaque étape se termine par une validation explicite (voir
[AGENTS.md](./AGENTS.md) §1).

Légende : `[x]` terminé · `[ ]` à faire

---

## 1. Initialisation du projet `[x]`

Squelette Gradle et chaîne de qualité opérationnels.

- [x] Wrapper Gradle 9.6.1, toolchain JDK 21 (Azul, via `gradle-daemon-jvm.properties`)
- [x] Catalogue de versions `gradle/libs.versions.toml`
- [x] Module `:app` — `minSdk 26`, `compileSdk 36`, `targetSdk 36`, JVM 17
- [x] Compose androidx (BOM) + Material 3, thème `AppTheme` avec couleur dynamique
- [x] `MainActivity` affichant un écran d'attente, prévisualisable
- [x] ktlint + Detekt (`config/detekt/detekt.yml`) + `.editorconfig`
- [x] Documentation de travail (les 7 fichiers racine) et licence MIT

**Vérifié :** `./gradlew ktlintCheck detekt lint test assembleDebug` → succès.

### Suivi ouvert

- [ ] **Passer à `compileSdk`/`targetSdk` 37.** Les versions récentes
  d'AndroidX (`core-ktx` 1.19+, `activity` 1.13+) l'exigent. La plateforme
  `android-37` n'était pas installée sur le poste d'initialisation ; `core-ktx`
  et `activity` ont donc été fixés à des versions compatibles 36. À traiter
  après installation via le SDK Manager, puis remonter les deux versions dans
  le catalogue.

---

## 2. Architecture `[ ]`

Matérialiser les couches, sans logique métier.

- [ ] Module Kotlin/JVM `:domain`, sans SDK Android
- [ ] Arborescence de paquets décrite dans [ARCHITECTURE.md](./ARCHITECTURE.md) §2
- [ ] Premiers types du modèle : `TunnelState`, `NetworkTransport`,
      `NetworkContext`, `RuleDecision`
- [ ] Tests JVM sur ces types (invariants, valeurs par défaut)

**Fait quand** `:domain` compile sans dépendance Android et que ses tests
tournent hors émulateur.

---

## 3. Configuration Hilt `[ ]`

- [ ] Plugin Hilt + KSP, `@HiltAndroidApp`, `@AndroidEntryPoint`
- [ ] `DispatcherModule` — dispatchers qualifiés et injectables
- [ ] Test d'intégration vérifiant la construction du graphe

**Fait quand** l'application démarre avec Hilt actif et un graphe vide mais
valide.

---

## 4. Couche Tailscale `[ ]`

> ⚠️ **Commencer par un spike** — voir [SPECS.md](./SPECS.md) §10.1. Aucune
> ligne de code métier ne doit présupposer son issue.

- [ ] Spike : déterminer comment piloter le client officiel, et documenter le
      résultat dans SPECS.md §10.1
- [ ] Interface `TailscaleController` dans `:domain`
- [ ] `FakeTailscaleController` (tests) et `NoOpTailscaleController` (client absent)
- [ ] Implémentation de production selon le résultat du spike
- [ ] Détection de la présence du client Tailscale
- [ ] Tests des trois implémentations

---

## 5. Observation réseau `[ ]`

- [ ] `NetworkObserver` (interface domaine) + implémentation
      `ConnectivityManager.NetworkCallback` en `callbackFlow`
- [ ] Prise en compte : Wi-Fi, cellulaire, Ethernet, validation Internet
- [ ] Observation du mode avion
- [ ] Lecture du SSID, avec gestion explicite de l'indisponibilité
- [ ] Debounce à fenêtre **injectée** + `distinctUntilChanged`
- [ ] `FakeNetworkObserver`
- [ ] Tests du debounce en temps virtuel

---

## 6. Moteur de règles `[ ]`

- [ ] Contrat `Rule` + `RuleEngine` (filtre, tri, première décision ferme)
- [ ] Règle mode avion (prio 100)
- [ ] Règle Wi-Fi blacklisté (prio 200)
- [ ] Règle Wi-Fi non blacklisté (prio 300)
- [ ] Règle réseau mobile (prio 400)
- [ ] Enregistrement des règles via `@IntoSet`

**Fait quand** ajouter une règle ne demande aucune modification du moteur.

---

## 7. Tests unitaires du moteur `[ ]`

- [ ] Ordre de priorité, y compris priorités égales (tri stable)
- [ ] Arrêt à la première décision ferme
- [ ] Règles désactivées ignorées
- [ ] Ensemble de règles vide → `NO_DECISION`
- [ ] Chaque branche de chaque règle, `NO_DECISION` compris
- [ ] Mesure de couverture et vérification de l'objectif ~100 % sur `:domain`

---

## 8. Persistance `[ ]`

- [ ] Room : entité + DAO blacklist (unicité du SSID, insensible à la casse)
- [ ] Room : entité + DAO journal, purge au-delà de 500 entrées
- [ ] DataStore : préférences de [SPECS.md](./SPECS.md) §6.3
- [ ] DataStore : `enabled` / `priority` par règle
- [ ] Implémentation des repositories du domaine
- [ ] Tests DAO, tests DataStore, tests de la purge

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
