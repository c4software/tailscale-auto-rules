# Notification figée : la raison affichée vient du journal, pas de la règle courante

## Contexte

Constat terrain : sur données mobiles, la notification affiche
« **Tunnel activé** / Raison : **Wi-Fi de confiance** ». Les deux lignes se
contredisent — un Wi-Fi de confiance *désactive* le tunnel — et rien ne la
remet à jour, même après avoir tué l'application.

**Cause.** `AutomationCoordinator.currentStatus()`
(`app/src/main/kotlin/…/automation/AutomationCoordinator.kt:138`) compose deux
sources de nature différente :

| Ligne affichée | Source | Fraîcheur |
|---|---|---|
| Titre (état du tunnel) | `TailscaleController` | constaté, toujours à jour |
| Raison (règle) | **dernière entrée du journal** | dernier *changement d'état* |

Or `SynchronizeTunnelUseCase.apply()` (`domain/…/usecase/SynchronizeTunnelUseCase.kt:79`)
n'écrit **rien** au journal quand le tunnel est déjà dans l'état visé —
volontairement, pour ne pas le saturer. Séquence observée :

1. Wi-Fi de confiance → `BlacklistedWifiRule` désactive → journal : `blacklisted-wifi`.
2. Le tunnel est réactivé (à la main, ou par un cycle antérieur), puis passage en 4G.
3. `MobileNetworkRule` décide ENABLE ; le tunnel l'est déjà → `AlreadyInTargetState`,
   **aucune entrée de journal**.
4. La notification relit la même dernière entrée : « Wi-Fi de confiance », à jamais.

Le journal étant persisté en Room, tuer l'application ne change rien : le
redémarrage relit la même entrée périmée.

**Intention.** La raison affichée doit décrire **la règle qui s'applique
maintenant**, réévaluée à chaque rafraîchissement. Le journal redevient ce
qu'il est : un historique des changements. S'y ajoutent trois filets contre le
« ça ne s'update plus » : rafraîchir la notification à chaque démarrage du
service, un battement périodique de secours, et des traces exploitables sur
l'appareil.

---

## Étape 1 — La raison vient de la règle courante

### 1.1 Factoriser l'évaluation (domaine)

Le même bloc `engine.evaluate(RuleContext(network, blacklist, settings))` est
déjà écrit deux fois (`SynchronizeTunnelUseCase.kt:51`,
`DetectManualOverrideUseCase.kt:84`) et le serait une troisième.

Nouveau `domain/src/main/kotlin/…/domain/usecase/EvaluateRulesUseCase.kt` :

```kotlin
class EvaluateRulesUseCase(
    private val blacklistRepository: BlacklistRepository,
    private val settingsRepository: SettingsRepository,
    private val engine: RuleEngine,
) {
    suspend operator fun invoke(networkContext: NetworkContext): RuleEvaluation
}
```

`SynchronizeTunnelUseCase` et `DetectManualOverrideUseCase` le prennent en
dépendance à la place du triplet `blacklistRepository` / `settingsRepository` /
`engine` (`settingsRepository` reste requis dans `SynchronizeTunnelUseCase`
pour `isServiceEnabled`).

### 1.2 Éviter la double évaluation

`DetectManualOverrideUseCase` gagne une surcharge qui accepte une évaluation
déjà calculée — c'est la comparaison des lignes 100-104 qui est réutilisée,
sans relire le réseau ni réévaluer le moteur :

```kotlin
suspend operator fun invoke(
    evaluation: RuleEvaluation,
    tunnelState: TunnelState,
    lastChange: JournalEntry?,
): ManualOverride?
```

Les deux surcharges publiques existantes délèguent à celle-ci ; la fenêtre de
grâce `CommandSettleGrace` et la sémantique décrite dans la KDoc sont
inchangées, donc `DetectManualOverrideUseCaseTest` reste valide.

### 1.3 Décrire l'état affichable (domaine)

Nouveau `domain/src/main/kotlin/…/domain/usecase/DescribeTunnelStatusUseCase.kt`,
qui rassemble ce que la notification a besoin de dire, **en une seule lecture
du réseau et une seule évaluation** :

```kotlin
data class TunnelStatus(
    val state: TunnelState,          // constaté via TailscaleController
    val ruleId: RuleId?,             // règle qui s'applique MAINTENANT
    val isManuallyOverridden: Boolean,
)

class DescribeTunnelStatusUseCase(
    private val networkObserver: NetworkObserver,
    private val evaluateRules: EvaluateRulesUseCase,
    private val detectManualOverride: DetectManualOverrideUseCase,
    private val journalRepository: JournalRepository,
    private val controller: TailscaleController,
) { suspend operator fun invoke(): TunnelStatus }
```

Le journal n'y sert plus qu'à alimenter la détection de geste manuel.
Quand aucune règle ne se prononce (`RuleEvaluation.NoDecision` — transport
inconnu, SSID illisible), `ruleId` vaut `null` et la notification affiche
« Aucune règle appliquée pour l'instant » : dire qu'on ne sait pas vaut mieux
que ressortir une règle périmée.

### 1.4 Câblage

- `AutomationCoordinator` : `currentStatus()` délègue à
  `DescribeTunnelStatusUseCase`. Les dépendances `detectManualOverride`,
  `journalRepository` et `controller` disparaissent du constructeur, ainsi que
  `tunnelState()` et la classe privée `TunnelStatus`.
- Supprimer `currentNotification()` (`AutomationCoordinator.kt:123`) : **code
  mort**, aucun appelant — `startForeground` ne peut pas suspendre, et
  l'étape 2 couvre le besoin autrement.
- `app/src/main/kotlin/…/di/UseCaseModule.kt` : fournir les deux nouveaux cas
  d'usage, ajuster les deux existants.
- `HomeViewModel` n'est pas touché : l'écran affiche `lastChange` comme
  *historique*, ce qui est correct.

---

## Étape 2 — Ne plus laisser la notification sur « état indéterminé »

`TunnelWatchService.onStartCommand` (`…/automation/TunnelWatchService.kt:81`)
publie systématiquement `build(UNKNOWN, ruleId = null)`, mais ne relance
l'observation que si `watchJob == null`. Sur un second `onStartCommand`
(redémarrage `START_STICKY`, `onLocationPermissionGranted()` qui enchaîne
`disarm()` + `arm()`), la notification reste donc sur « État du tunnel
indéterminé » jusqu'au prochain changement de réseau — le flux étant
`debounce(2 s) + distinctUntilChanged`, cela peut durer des heures.

Correctif : après `startInForeground()`, **inconditionnellement** et hors de la
garde `watchJob == null` :

```kotlin
scope.launch { runCatching { coordinator.refreshNotificationIfEnabled() } }
```

---

## Étape 3 — Battement périodique de secours

Un troisième job dans le service, lancé sous la garde `watchJob == null` :
toutes les 15 minutes, `coordinator.synchronize()` (la forme sans contexte, qui
relit le réseau via `networkObserver.current()`). Un rappel
`ConnectivityManager` mort ou une capacité mémorisée devenue fausse ne peuvent
alors plus figer l'automatisation au-delà d'un quart d'heure.

Un cycle sans changement est sans coût : `AlreadyInTargetState` ne commande
rien et n'écrit rien.

---

## Étape 4 — Traces exploitables sur l'appareil

Le domaine reste pur (pas de Timber) ; les traces sont posées dans la couche
Android, au niveau `Timber.i` — le réglage `verboseLogging` existe déjà.

- `AutomationCoordinator.refreshNotification()` : journaliser le triplet
  effectivement affiché (état, `ruleId`, override) au lieu du message actuel
  sans donnée.
- `TunnelWatchService` : tracer chaque battement de l'étape 3, pour distinguer
  « l'observation est morte » de « rien n'a changé ».

---

## Tests (même commit, cf. AGENTS.md §133)

**Domaine** — `domain/src/test/…/usecase/DescribeTunnelStatusUseCaseTest.kt` (nouveau) :

- **le cas signalé** : journal contenant une vieille entrée `blacklisted-wifi`
  → DISABLED, contexte courant CELLULAR, tunnel ENABLED
  ⇒ `ruleId == mobile-network`, `isManuallyOverridden == false` ;
- aucune règle ne se prononce ⇒ `ruleId == null` ;
- geste manuel avéré (hors fenêtre de grâce) ⇒ `isManuallyOverridden == true` ;
- `TailscaleController` indisponible ⇒ `state == UNKNOWN`.

`EvaluateRulesUseCase` est couvert par les tests existants passant par lui ;
compléter si `koverVerify` (seuil 98 % sur `:domain`) le réclame. Adapter les
constructeurs dans `SynchronizeTunnelUseCaseTest` et
`DetectManualOverrideUseCaseTest`.

**Application** — `app/src/test/…/automation/AutomationCoordinatorTest.kt` :
adapter la construction du coordinateur (`setUp`, l. 78-101) et ajouter le test
de bout en bout du symptôme : journal `blacklisted-wifi`, observateur en
CELLULAR, tunnel actif ⇒ le texte posté est « Raison : Réseau mobile », pas
« Wi-Fi de confiance ».

Les étapes 2 et 3 vivent dans `TunnelWatchService`, qui n'a aucun test
(aucun `TunnelWatchServiceTest` n'existe, ni pour `BootReceiver`) : elles sont
vérifiées sur appareil, comme l'étape 17 de `TASKS.md`.

---

## Vérification

```bash
./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug
```

L'interface n'est pas touchée (aucun Composable modifié) : `verifyRoborazziDebug`
n'est pas requis.

**Sur appareil**, le scénario exact du signalement :

1. Se connecter à un Wi-Fi de confiance, laisser le tunnel se couper
   (notification : « Tunnel désactivé / Raison : Wi-Fi de confiance »).
2. Réactiver le tunnel à la main depuis le client Tailscale.
3. Couper le Wi-Fi, passer en données mobiles.
4. **Attendu** : « Tunnel activé / Raison : **Réseau mobile** » — et non plus
   « Wi-Fi de confiance ».
5. Tuer l'application, la relancer : la notification doit se recaler en
   quelques secondes au lieu de rester sur « État du tunnel indéterminé ».
6. `adb logcat -s TailscaleAutoRules` pour constater les battements de
   l'étape 3 et le contenu affiché.

## Livraison

Un commit par étape, dans l'ordre, chacun vérifié (AGENTS.md §1) :

1. `fix(notification): afficher la règle qui s'applique, pas la dernière consignée`
2. `fix(automation): recaler la notification à chaque démarrage du service`
3. `feat(automation): battement de secours de l'observation réseau`
4. `chore(automation): tracer l'état affiché et les battements`

Mettre à jour `CHANGELOG.md` avec les correctifs.
