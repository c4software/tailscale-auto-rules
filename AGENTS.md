# AGENTS.md — Règles de développement

Contrat de travail pour tout agent (Claude Code, Codex, …) ou développeur
humain intervenant sur ce dépôt. Il prévaut sur toute habitude personnelle.

Documents liés : [SPECS.md](./SPECS.md) (le quoi) ·
[ARCHITECTURE.md](./ARCHITECTURE.md) (le comment) · [TASKS.md](./TASKS.md)
(l'ordre) · [PROMPT.md](./PROMPT.md) (l'intention initiale, figée).

---

## 1. Méthode de travail

**Un commit par étape de [TASKS.md](./TASKS.md), dans l'ordre. Les étapes
s'enchaînent sans demander de validation.**

Pour chaque étape :

1. Énoncer brièvement le choix technique retenu, et pourquoi.
2. Implémenter **cette étape uniquement**.
3. Écrire les tests dans le même incrément que le code.
4. Lancer la vérification complète (§5) et **rapporter la sortie réelle**.
5. Mettre à jour la documentation impactée.
6. Committer, puis passer à l'étape suivante.

Si une étape se révèle plus grosse que prévu, la découper en plusieurs commits
— mais ne jamais fusionner deux étapes en un seul.

La granularité est **le commit, pas la conversation** : c'est lui qui rend le
travail relisible et réversible étape par étape. C'est ce qui rend l'avance
autonome sûre.

### Quand s'arrêter quand même

L'enchaînement automatique ne dispense pas de savoir s'interrompre. Quatre cas,
et seulement ceux-là :

- **La vérification (§5) échoue et la corriger demande un arbitrage** — abaisser
  une version, relâcher une règle de qualité, renoncer à un test.
- **La spécification est ambiguë sur une règle métier.** Ne jamais trancher en
  silence sur un comportement visible par l'utilisateur.
- **Une action sortante ou difficilement réversible** : `git push`, publication,
  réécriture d'historique, suppression de données.
- **Un choix structurant s'impose** qui contredirait
  [ARCHITECTURE.md](./ARCHITECTURE.md) ou [SPECS.md](./SPECS.md).

Hors de ces cas : décider, documenter la décision dans le message de commit, et
continuer.

> Note : [PROMPT.md](./PROMPT.md) demandait une validation à chaque étape. Ce
> fichier est figé — il conserve l'intention initiale — mais la règle applicable
> est celle ci-dessus, qui l'a remplacée sur ce point.

---

## 2. Interdits

- ❌ Livrer du code qui ne compile pas, ou une fonctionnalité sans ses tests.
- ❌ Laisser du code mort, une classe inutilisée, un paramètre ignoré.
- ❌ Écrire un `TODO` sans tâche correspondante dans [TASKS.md](./TASKS.md).
- ❌ Utiliser une API Android dépréciée.
- ❌ Importer `android.*`, `androidx.*`, Room, DataStore, Hilt ou Compose depuis
  `:domain`.
- ❌ Mettre de la logique métier dans un ViewModel ou un Composable.
- ❌ Créer un singleton métier (`object` porteur d'état) — la portée se déclare
  à Hilt.
- ❌ Appeler `System.currentTimeMillis()` ailleurs que dans l'implémentation de
  `Clock`.
- ❌ Introduire une dépendance sans justification écrite dans le message de
  commit.
- ❌ Anticiper : ne pas créer de structure « pour plus tard ». Une abstraction
  arrive avec son deuxième cas d'usage, pas avant.

En cas de choix entre plusieurs solutions, l'ordre de préférence est :
**simplicité → lisibilité → testabilité → maintenabilité → API Android
officielle**.

---

## 3. Conventions de code

Héritées du projet dont ce dépôt reprend l'architecture, et appliquées par
ktlint / Detekt / `.editorconfig`.

### Mise en forme

- Indentation 4 espaces (2 pour XML, YAML, TOML, JSON).
- Ligne à 120 colonnes maximum.
- **Trailing commas systématiques** sur les listes multi-lignes.
- Imports explicites, jamais d'étoile.

### Nommage

| Élément | Convention | Exemple |
|---|---|---|
| Fichier Kotlin | Nom de la déclaration principale | `AirplaneModeRule.kt` |
| Classe, interface, enum | `PascalCase` | `RuleEngine` |
| Fonction, propriété | `camelCase` | `evaluate`, `isEnabled` |
| `@Composable` | `PascalCase` | `HomeScreen` |
| Constante de fichier | `private val PascalCase` en tête de fichier | `private val ButtonHeight = 48.dp` |
| Test | `camelCase` descriptif, sans backticks | `anEnabledAirplaneModeDisablesTheTunnel` |
| Fake | Préfixe `Fake` | `FakeTailscaleController` |

### Documentation du code

- KDoc **en français**, sur ce qui n'est pas évident : un choix, une contrainte,
  une raison. Pas de paraphrase de la signature.
- Un commentaire explique **pourquoi**, jamais **quoi**.

Exemple du style attendu :

```kotlin
/**
 * Bulle reçue : coin haut-gauche rentrant vers l'avatar.
 *
 * Le `Button` Material3 n'accepte pas de `Brush` en arrière-plan : on compose
 * directement une `Row` cliquable.
 */
```

### Compose

- Un Composable public prend `modifier: Modifier = Modifier` en **premier
  paramètre optionnel**, après les paramètres obligatoires.
- Aucun calcul dans un Composable : il affiche `UiState`, il ne le dérive pas.
- Chaque écran a une `@Preview` privée qui fonctionne **sans injection**.
- Les dimensions récurrentes passent par `Spacing`, pas par des `.dp` épars.

---

## 4. Tests

- **Aucune fonctionnalité sans tests, dans le même commit.**
- Un test par comportement, nommé d'après le comportement observable.
- Le domaine se teste en JVM pur : ni Robolectric, ni émulateur, ni Android.
- Les doubles sont des **Fakes** versionnés, pas des mocks générés.
- Le temps et les dispatchers sont injectés ; les tests utilisent
  `kotlinx-coroutines-test` et son ordonnanceur virtuel. Jamais de
  `Thread.sleep`.
- Couverture visée : **~100 % sur `:domain`**. Chaque branche `NO_DECISION`
  incluse.

---

## 5. Vérification

Ce dépôt se construit avec le JDK embarqué d'Android Studio. `gradlew` lit
`JAVA_HOME` en priorité et ne consulte le `PATH` que si elle est absente :
**définir `JAVA_HOME` suffit, ne jamais bricoler le `PATH`.**

- **Agents Claude Code** — rien à faire : `JAVA_HOME` est déclarée dans
  `.claude/settings.local.json` (non versionné, car le chemin dépend de la
  machine). Lancer `./gradlew …` directement.
- **En shell interactif** :

  ```bash
  export JAVA_HOME=$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr
  ```

⚠️ Ne **jamais** préfixer une commande d'un `export PATH="$JAVA_HOME/bin:$PATH"`.
La valeur n'étant résolue qu'à l'exécution, la commande devient impossible à
rapprocher d'une règle d'autorisation : le harness redemande confirmation à
chaque appel, et aucune règle réutilisable ne peut être enregistrée.

### Écrire des commandes qui ne redemandent pas confirmation

Une commande n'est mémorisable dans une règle d'autorisation que si sa forme se
répète. Quatre habitudes suffisent à éviter l'essentiel des demandes :

| À faire | Plutôt que |
|---|---|
| Écrire les fichiers avec les outils **Write** et **Edit** | `cat > fichier <<'EOF'`, `sed -i '…'`, `python3 - <<'PY'` |
| `git commit -m "…"` (l'identité est dans `.git/config`) | `git -c user.email=… -c user.name=… commit …` |
| Un motif `grep` stable, ou lire la sortie complète | un `grep -E "…"` différent à chaque appel |
| Une commande de vérification unique (§5) | des variantes de tâches Gradle au coup par coup |

Les règles partagées vivent dans `.claude/settings.json`, versionné et sans
chemin machine. `git push` en est **volontairement absent** : une action
sortante se confirme.

Commande de vérification, à passer **avant tout commit** :

```bash
./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug
```

`koverVerify` échoue sous 98 % de couverture sur `:domain`. Le seuil constate
un acquis — le domaine est intégralement couvert — plutôt qu'il ne fixe un
objectif : le laisser plus bas autoriserait une régression silencieuse. La
pureté de `Rule.evaluate` rend ce niveau atteignable sans échafaudage, donc ne
pas l'atteindre signale une branche oubliée.

Correction automatique du formatage :

```bash
./gradlew ktlintFormat
```

Rien n'est déclaré terminé sans que cette commande soit passée **et sa sortie
réellement constatée**. En cas d'échec, rapporter la sortie ; ne jamais
annoncer un succès non observé.

### Définition de « terminé »

- [ ] `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug` passe.
- [ ] Les tests couvrent le comportement ajouté, y compris ses cas limites.
- [ ] Aucun code mort, aucun `TODO` orphelin.
- [ ] [ARCHITECTURE.md](./ARCHITECTURE.md) §9 reflète l'état réel du dépôt.
- [ ] La case correspondante de [TASKS.md](./TASKS.md) est cochée.
- [ ] Le commit suit §6.

---

## 6. Git

**Un commit = une seule fonctionnalité cohérente.** Format
[Conventional Commits](https://www.conventionalcommits.org/) :

```
<type>(<portée>): <description à l'impératif, en minuscule>

<corps facultatif : pourquoi, pas quoi>
```

Types : `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`, `ci`.

Portées usuelles : `domain`, `data`, `ui`, `di`, `rules`, `engine`, `network`,
`tailscale`, `journal`, `settings`, `gradle`.

```
feat(rules): ajouter la règle du mode avion
test(engine): couvrir la sélection par priorité
docs(architecture): décrire le cycle de synchronisation
```

Ne jamais committer : `local.properties`, un keystore, une clé, une capture de
build.

---

## 7. Ce qu'il faut faire quand on est bloqué

- **Le SDK Android manque une plateforme** → l'installer via le SDK Manager
  d'Android Studio ; ne pas contourner en abaissant silencieusement une version.
- **Une dépendance impose un `compileSdk` supérieur** → le signaler et proposer
  le choix, plutôt que de rétrograder la dépendance sans le dire.
- **La spécification est ambiguë** → poser la question. Ne pas trancher en
  silence sur une règle métier.
- **Une abstraction résiste** → le dire. Contourner une abstraction est une
  dette ; la corriger est une étape.
