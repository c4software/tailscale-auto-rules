<!--
  Merci pour votre contribution. Les critères ci-dessous reprennent
  CONTRIBUTING.md ; ils ne sont pas décoratifs, la CI vérifie les trois
  premiers.
-->

## Ce que fait cette PR

<!-- Une ou deux phrases. Le *pourquoi* plutôt que le *quoi* : le diff dit déjà quoi. -->

## Vérifications

- [ ] `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug` passe
- [ ] Chaque comportement ajouté est couvert par un test
- [ ] `:domain` reste exempt de toute dépendance Android
- [ ] Aucun code mort, aucun `TODO` sans tâche dans `TASKS.md`
- [ ] La documentation impactée est à jour

## Si cette PR ajoute une règle

- [ ] `evaluate` reste pure — pas d'I/O, pas d'horloge, pas de journalisation
- [ ] Chaque branche est testée, `NO_DECISION` comprise
- [ ] La priorité est justifiée au regard de `SPECS.md` §4
- [ ] Le moteur n'a pas été modifié
