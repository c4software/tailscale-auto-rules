# Exceptions dynamiques — mémoire des gestes manuels par réseau

## Contexte

Aujourd'hui, un geste manuel sur le tunnel (activer/couper Tailscale à la main) est
*reconnu* et affiché (`DetectManualOverrideUseCase`, carte d'accueil, notification),
mais jamais mémorisé : au prochain changement de réseau — et même au heartbeat de
15 min — les règles reprennent la main (SPECS §3.3 l'affirme explicitement).

Objectif : transformer ce constat en **mémoire par réseau**. Si l'utilisateur active
Tailscale sur le Wi-Fi X, il sera actif la prochaine fois sur X ; s'il le coupe, il
restera coupé — idem en 4G/5G (une seule mémoire « données mobiles », le cellulaire
n'ayant aucun identifiant). Chaque nouveau geste sur un réseau **remplace** la
mémoire précédente (upsert, jamais de suppression implicite) ; le retour au
comportement automatique se fait en supprimant l'entrée dans l'UI. L'apprentissage
est **actif par défaut**, proposé au premier lancement et débrayable dans les
paramètres.

## Analyse de complexité

Globalement **L**, découpable en 6 étapes dont aucune ne dépasse M.

| Lot | Taille |
|---|---|
| Réécriture SPECS §3.3/§6/§7/§9 + TASKS | S |
| Domaine : modèle, repository, règle `network-exception` (prio 150) | M |
| Domaine : capture du geste (`RecordManualOverrideUseCase`) | M |
| Data : Room v2 (première migration du projet) + DataStore + DI | M |
| Automation : branchement dans `TunnelWatchService`/`AutomationCoordinator` | M |
| UI : réglage + choix au premier lancement + liste des exceptions | M |

### Risques

1. **Élevé — faux apprentissage via un autre VPN.** `observeRunning()` détecte « un
   VPN actif », pas « Tailscale actif » (limite Android). Mitigations : geste
   filtré par la grâce de 10 s, exceptions visibles et supprimables dans l'UI,
   journalisation explicite, interrupteur d'apprentissage. Point ouvert SPECS §10.
2. **Élevé — boucle de rétroaction journal/détection.** La détection exige
   `lastChange.newState == targetState`. L'enregistrement d'une exception doit
   donc **aussi écrire une entrée de journal** (`ruleId = network-exception`),
   sinon le geste suivant devient indétectable.
3. **Moyen — races temporelles** (debounce réseau 2 s, settle tunnel 2 s, grâce
   10 s, heartbeat 15 min). Un `Mutex` dans `AutomationCoordinator` sérialise
   cycle et capture ; l'attribution du geste au réseau reste best-effort.
4. **Moyen — première migration Room** (v1 → v2) : ajouter `room-testing`, test de
   migration obligatoire.
5. **Faible — sentinelle cellulaire globale** : à assumer en SPECS.

## Sémantique retenue

### Clé réseau (`NetworkExceptionKey.from(NetworkContext): NetworkExceptionKey?`)
- Wi-Fi avec SSID → `wifi:<asSsidKey(ssid)>` (même canonicalisation que la blacklist).
- Cellulaire → sentinelle `cellular` (une seule mémoire « données mobiles »).
- SSID null, mode avion, réseau non validé, pas de réseau → pas de clé : ni
  apprentissage ni rejeu.

### Cycle
```
geste manuel détecté (grâce 10 s passée, service actif, apprentissage actif, clé dérivable)
  → upsert exception(clé, état observé)          [toujours upsert, jamais delete]
  → journal : record(ancien, observé, ruleId = network-exception)
prochains cycles sur ce réseau
  → NetworkExceptionRule (priorité 150, entre AirplaneMode 100 et Blacklisted 200)
    impose l'état mémorisé ; survit au heartbeat et aux changements de réseau
re-geste sur le même réseau
  → l'exception est remplacée par le nouvel état (même mécanisme)
retour au comportement automatique
  → suppression de l'entrée dans l'UI (écran blacklist)
```

### Cas limites
- Mode avion : `AirplaneModeRule` (100) reste au-dessus ; capture refusée.
- Service désactivé : aucun cycle, aucun journal → aucune capture.
- Apprentissage coupé : plus de capture, mais les exceptions **existantes**
  continuent de se rejouer (la liste UI sert à purger).
- Exception sur un SSID blacklisté : 150 < 200 → l'exception gagne toujours.

## Étapes (TASKS.md 18 → 23)

Chaque étape : tests inclus, `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug`,
commit, puis étape suivante (enchaînement autonome, relecture par commit).

### 18 — Spécifier
`SPECS.md` : réécrire §3.3 (le geste devient une mémoire), ajouter §4.5 (règle
`network-exception`, prio 150, tableau des clés), amender §6.1/§6.2 (section
exceptions sur l'écran réseaux de confiance), §6.3 (réglage « Apprendre mes
gestes » + question au premier lancement), §7 (notification), §9 (table Room
`network_exception`), §10 (point ouvert autre VPN). `TASKS.md` (18-23),
`ARCHITECTURE.md`, `CHANGELOG.md`.

### 19 — Domaine : modèle, repository, règle
Créer `domain/…/domain/model/NetworkException.kt` (+ `NetworkExceptionKey`),
`domain/…/domain/repository/NetworkExceptionRepository.kt` (`observeAll`,
`current(): Map<NetworkExceptionKey, TunnelState>`, `upsert`, `remove`),
`domain/…/domain/rule/NetworkExceptionRule.kt` (calque de `BlacklistedWifiRule`),
`FakeNetworkExceptionRepository` en testFixtures. Modifier `Rule.kt`
(`RuleContext.networkExceptions` — extension prévue par le doc du type),
`Priorities.kt` (150), `EvaluateRulesUseCase.kt` (alimenter le contexte).
Tests exhaustifs de chaque branche d'`evaluate` (ARCHITECTURE §3.4), Kover ≥ 98 %.

### 20 — Domaine : capture du geste
Créer `domain/…/domain/usecase/RecordManualOverrideUseCase.kt` :
entrées `(networkContext, ManualOverride)` ; gardes (apprentissage activé,
service activé, clé dérivable, pas de mode avion) ; **upsert** systématique +
entrée de journal `ruleId = network-exception` (risque 2). Modifier
`AppSettings.kt` (`isLearningEnabled = true` par défaut) + `FakeSettingsRepository`.

### 21 — Data : Room v2 + DataStore + DI
Créer `NetworkExceptionEntity` (table `network_exception` : `network_key` TEXT
unique, `desired_state`, `created_epoch_millis`), `NetworkExceptionDao`,
`RoomNetworkExceptionRepository`, schéma `2.json`, `Migration1To2Test`
(ajouter `androidx.room:room-testing`). Modifier `AppDatabase.kt` (v2),
`DatabaseModule.kt`, `RepositoryModule.kt`, `SettingsKeys.kt` +
`DataStoreSettingsRepository.kt` (clés `learning.enabled` et
`learning.prompted` pour la question au premier lancement),
`RuleModule.kt` (`@Provides @IntoSet` — seul enregistrement requis),
`UseCaseModule.kt`.

### 22 — Automation : brancher la capture
`AutomationCoordinator.kt` : méthode `onTunnelStateSettled()` sous un `Mutex`
partagé avec `synchronize` — lit contexte + état + `lastChange`, appelle
`DetectManualOverrideUseCase` puis `RecordManualOverrideUseCase`, rafraîchit la
notification. `TunnelWatchService.watchTunnel()` : appeler
`coordinator.onTunnelStateSettled()` après le settle de 2 s. Tests
Robolectric du scénario complet (cycle → toggle → exception créée → cycle
idempotent → re-toggle → exception remplacée). Validation manuelle sur appareil
à consigner dans TASKS.md.

### 23 — UI : réglage, premier lancement, liste
Calque du commit 8bf3638 (coupure automatisme mobile) :
- `presentation/settings/` : `SwitchCard` « Apprendre mes gestes ».
- Premier lancement : carte/dialog unique sur l'accueil (`HomeScreen`) tant que
  `learning.prompted` est faux — deux boutons (activer / ne pas activer), pas
  d'écran d'onboarding dédié.
- `presentation/blacklist/` : section « Exceptions apprises » — liste
  **uniquement** des réseaux où un geste a été mémorisé (libellé SSID ou
  « Données mobiles » + comportement mémorisé actif/coupé). Suppression par
  **swipe** (`SwipeToDismissBox` Material 3 — aucun pattern de swipe n'existe
  encore dans l'app, c'est une première) ; la section est absente quand il n'y
  a aucune exception.
- `RuleLabels.kt`/`ModelLabels.kt` : libellé de `network-exception` (journal +
  notification). Strings dans `res/values/strings.xml`.
- Tests VM + écrans, `./gradlew :app:recordRoborazziDebug` et revue des captures.

## Fichiers réutilisés (ne pas réinventer)
- `DetectManualOverrideUseCase` (détection éprouvée, grâce 10 s) — inchangé.
- `Ssid.asSsidKey()` pour la canonicalisation des clés Wi-Fi.
- `RuleEngine` — strictement inchangé (première décision ferme gagne).
- Patron UI/persistance/cycle-immédiat du commit 8bf3638 (`SwitchCard`,
  `synchronizeTunnel()` après changement de réglage).
- Fakes de `domain/src/testFixtures/` + `Contexts`.

## Vérification de bout en bout
1. `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug` à chaque étape.
2. `Migration1To2Test` vert (données blacklist/journal préservées).
3. Sur appareil : couper Tailscale à la main sur un Wi-Fi blacklisté-inverse →
   attendre > 10 s → vérifier l'entrée « exception » au journal et dans l'écran
   réseaux → changer de réseau puis revenir → le tunnel reste coupé →
   supprimer l'exception → le cycle suivant remonte le tunnel.

## Hors périmètre
- Distinction des réseaux cellulaires (opérateur/PLMN) — sentinelle globale.
- Expiration automatique (TTL) — `created_epoch_millis` stocké pour un TTL futur.
- Identification fiable « Tailscale vs autre VPN » — limite plateforme, point ouvert.
- Export/synchronisation des exceptions.
