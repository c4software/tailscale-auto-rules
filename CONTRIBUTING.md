# Contribuer à Tailscale Auto Rules

Merci de l'intérêt porté au projet. Ce guide décrit le parcours d'une
contribution. Les conventions de code et la définition de « terminé » vivent
dans [AGENTS.md](./AGENTS.md) — lisez-le avant d'écrire du code.

---

## Avant de commencer

1. **Ouvrez une issue** décrivant le problème ou la proposition, et attendez un
   retour. Une PR sans discussion préalable risque d'arriver au mauvais moment
   dans la feuille de route.
2. **Consultez [TASKS.md](./TASKS.md).** Le projet se construit dans un ordre
   défini. Une contribution portant sur une étape non encore atteinte sera
   mise en attente, pas rejetée.
3. **Consultez [SPECS.md](./SPECS.md).** Toute modification de comportement
   commence par une mise à jour de la spécification.

---

## Mise en place

**Prérequis :** JDK 21, SDK Android (plateforme 37). Android Studio est
recommandé mais pas requis.

```bash
git clone https://github.com/<votre-compte>/tailscale-auto-rules.git
cd tailscale-auto-rules

# Si vous utilisez le JDK embarqué d'Android Studio :
export JAVA_HOME=$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew assembleDebug
```

`local.properties` est généré automatiquement par Android Studio ; en ligne de
commande, créez-le avec `sdk.dir=/chemin/vers/Android/Sdk`. Il ne doit **jamais**
être committé.

---

## Cycle de contribution

1. Créez une branche depuis `main` : `feat/regle-horaire`, `fix/ssid-vide`…
2. Travaillez par **petits commits cohérents** — un commit, une chose.
3. Écrivez les tests **dans le même commit** que le code.
4. Vérifiez avant de pousser :

   ```bash
   ./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug
   ```

   Formatage automatique : `./gradlew ktlintFormat`.

   **Si — et seulement si — vous avez modifié l'interface**, vérifiez en plus le
   rendu visuel :

   ```bash
   ./gradlew :app:verifyRoborazziDebug
   ```

   Cette étape **n'est pas dans la CI** : elle coûte plusieurs minutes de temps
   machine, trop cher à chaque Pull Request. Personne ne la lancera donc à votre
   place.

   En cas de différence, regardez les images produites dans
   `app/build/outputs/roborazzi/`, assurez-vous que le changement est bien celui
   que vous vouliez, puis réenregistrez :

   ```bash
   ./gradlew :app:recordRoborazziDebug
   ```

   Les images mises à jour font partie de la PR : c'est ainsi que la revue voit
   le changement visuel.
5. Mettez à jour la documentation impactée
   ([ARCHITECTURE.md](./ARCHITECTURE.md), [SPECS.md](./SPECS.md),
   [TASKS.md](./TASKS.md)).
6. Ouvrez la Pull Request.

---

## Messages de commit

Format [Conventional Commits](https://www.conventionalcommits.org/) :

```
feat(rules): ajouter la règle de plage horaire
fix(network): ignorer les SSID vides renvoyés par le système
test(engine): couvrir les priorités égales
docs(readme): corriger la commande de compilation
```

Types : `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`, `ci`.

Le corps du message explique **pourquoi**, pas **quoi** — le diff dit déjà quoi.

---

## Critères d'acceptation d'une Pull Request

Une PR est fusionnable lorsque :

- [ ] la CI est verte — aucune exception ;
- [ ] chaque comportement ajouté est couvert par un test ;
- [ ] `:domain` reste exempt de toute dépendance Android ;
- [ ] aucun code mort, aucun `TODO` sans tâche associée ;
- [ ] la documentation impactée est à jour ;
- [ ] la PR reste focalisée : une seule intention, pas de refactoring
      opportuniste mêlé à une correction.

---

## Ajouter une règle

C'est la contribution la plus attendue, et la mieux balisée. Le moteur ne doit
**jamais** être modifié pour l'accueillir.

1. Créez la classe dans `:domain/rule/`, implémentant `Rule`.
2. Choisissez une `priority` cohérente avec le tableau de
   [SPECS.md](./SPECS.md) §4, et justifiez-la dans la PR.
3. `evaluate` doit rester **pure** : pas d'I/O, pas d'horloge, pas de
   journalisation.
4. Testez **chaque** branche, y compris celles retournant `NO_DECISION`.
5. Enregistrez la règle dans le module Hilt via `@IntoSet`.
6. Documentez-la dans [SPECS.md](./SPECS.md) §4.

Si votre règle a besoin d'une donnée absente de `NetworkContext`, ajoutez-la —
et mettez à jour les fabriques de test. Si elle vous demande de modifier le
moteur ou une règle existante, **arrêtez-vous et ouvrez une discussion** :
c'est le signe que l'abstraction est à revoir.

---

## Signaler un bug

Indiquez :

- version d'Android et modèle de terminal ;
- version de l'application et du client Tailscale ;
- règles activées et contenu de la blacklist (SSID anonymisés si besoin) ;
- comportement attendu et comportement observé ;
- extrait du journal de l'application autour de l'événement.

---

## Sécurité

Ne signalez **pas** de faille via une issue publique. Contactez directement le
mainteneur, en laissant un délai raisonnable avant toute divulgation.

---

## Licence

En contribuant, vous acceptez que votre contribution soit distribuée sous la
licence [MIT](./LICENSE) du projet.
