# Prompt d'initialisation

> Ce fichier conserve, tel quel, le prompt qui a servi à amorcer le projet.
> Il ne doit plus être modifié : il constitue la trace de l'intention initiale.
> Les règles applicables au quotidien vivent dans [AGENTS.md](./AGENTS.md), la
> spécification fonctionnelle dans [SPECS.md](./SPECS.md).

---

## Mission

Tu es un ingénieur Android senior.

Tu développes une application Android native open source destinée à être publiée sur GitHub et sur le Google Play Store.

L'objectif est de produire un code de qualité production, maintenable pendant plusieurs années.

Ne cherche jamais à terminer le projet d'un seul coup.

Travaille uniquement par petits incréments.

Chaque incrément doit rester :

- compilable ;
- testé ;
- documenté ;
- sans régression.

Avant chaque implémentation importante :

- explique brièvement le choix technique retenu ;
- implémente uniquement cette étape ;
- ajoute les tests associés ;
- vérifie que tout compile.

Ne crée jamais de dette technique volontaire.

N'ajoute jamais de TODO inutiles.

Ne laisse jamais de code mort.

Si plusieurs solutions existent, privilégie toujours :

- la simplicité ;
- la lisibilité ;
- la testabilité ;
- la maintenabilité ;
- les API Android officielles.

---

## Projet

Créer une application Android compagnon de Tailscale.

Nom provisoire : **Tailscale Auto Rules**

L'application ne remplace PAS Tailscale.

Elle automatise uniquement l'activation ou la désactivation du tunnel selon des règles configurables.

Le comportement doit être totalement transparent pour l'utilisateur.

---

## Objectifs

Automatiser le tunnel selon :

- mode avion
- connexion mobile
- réseau Wi-Fi
- futures règles

Le moteur doit être entièrement extensible.

L'ajout d'une nouvelle règle ne devra nécessiter aucune modification du moteur existant.

Utiliser un pattern Strategy.

---

## Technologies

- Kotlin
- Jetpack Compose
- Material 3
- MVVM
- Repository Pattern
- StateFlow
- Coroutines
- Hilt
- Room
- DataStore
- Timber
- Gradle Kotlin DSL
- ktlint
- Detekt

Toujours utiliser les dernières APIs stables.

Ne jamais utiliser d'API Android dépréciée.

---

## SDK

- Min SDK : 26
- Target SDK : dernière version stable

---

## Architecture

Séparer clairement :

```
presentation/
domain/
data/
```

Le domaine ne dépend jamais d'Android.

Toute la logique métier doit pouvoir être exécutée dans un test JVM.

Aucune dépendance Android dans le moteur de règles.

Toute interaction avec Android doit passer par une abstraction.

---

## Contrôle de Tailscale

Créer une interface : `TailscaleController`

Exemple :

- `enable()`
- `disable()`
- `isRunning()`

Le moteur ne dépend jamais de l'implémentation.

Prévoir plusieurs implémentations :

- officielle
- mock
- fake
- future implémentation si l'API évolue

Ne jamais coupler le code métier à une implémentation Android.

---

## Surveillance réseau

Utiliser : `ConnectivityManager.NetworkCallback`

Observer :

- Wi-Fi
- Cellulaire
- Ethernet
- Validation Internet
- Changement de réseau

Observer également :

- Mode avion
- BOOT_COMPLETED

Prévoir un debounce afin d'éviter les changements rapides de réseau.

Le moteur ne doit recalculer les règles que lorsque cela est nécessaire.

---

## Règles

Une règle possède :

- `enabled`
- `priority`
- paramètres

Elle retourne :

- `ENABLE`
- `DISABLE`
- `NO_DECISION`

Le moteur :

- trie les règles
- les évalue
- applique uniquement la première décision

Aucune règle ne doit connaître les autres.

---

## Règles initiales

### Mode avion

Priorité maximale.

Si activé → désactiver Tailscale

### Réseau mobile

Si connecté en LTE / 4G / 5G / NR → activer Tailscale

### Wi-Fi blacklisté

L'utilisateur configure une liste de SSID.

Si connecté sur un SSID blacklisté → désactiver Tailscale

### Autres Wi-Fi

Tout Wi-Fi non blacklisté → activer Tailscale

### Aucun réseau

Ne rien faire.

---

## Évolutivité

Prévoir facilement :

- whitelist Wi-Fi
- BSSID
- regex SSID
- sécurité WPA
- VPN déjà actif
- localisation
- heure
- calendrier
- batterie
- recharge
- Bluetooth
- Android Auto
- Ethernet
- Hotspot
- Captive Portal
- DNS
- Exit Node
- Funnel
- Serve

L'ajout d'une nouvelle règle ne doit pas casser les anciennes.

---

## Persistance

Room :

- journal
- blacklist

DataStore :

- préférences

Ne jamais mélanger les deux.

---

## Interface

### Accueil

Afficher :

- état du tunnel
- type de réseau
- SSID
- dernière décision
- bouton « Synchroniser »

### Blacklist Wi-Fi

CRUD complet.

Ajouter rapidement le SSID actuel.

### Paramètres

- activation du service
- démarrage automatique
- logs
- notification persistante
- optimisation batterie
- version
- licence

---

## Journal

Conserver les 500 derniers événements.

Chaque événement contient :

- date
- ancien état
- nouvel état
- règle
- raison

---

## Notification

Notification persistante optionnelle.

Afficher :

- Tunnel : Activé / Désactivé
- Raison : Mobile / Maison / Mode avion / etc.

---

## Permissions

Demander uniquement les permissions indispensables.

L'application doit respecter les exigences du Google Play Store.

---

## Qualité

Le code doit respecter :

- SOLID
- Clean Architecture
- séparation des responsabilités
- injection de dépendances
- aucun singleton métier
- aucune logique métier dans les ViewModels
- aucune logique métier dans les Composables

Les Composables affichent uniquement l'état.

---

## Tests

Les tests font partie de chaque fonctionnalité.

Ne jamais développer une fonctionnalité sans ses tests.

### Tests unitaires

Tester :

- moteur de règles
- priorité
- blacklist
- réseau mobile
- mode avion
- debounce
- repositories
- datastore
- room
- viewmodels

Objectif : ≈100 % de couverture sur le moteur de règles.

### Tests d'intégration

Créer des Fake pour :

- `FakeTailscaleController`
- `FakeNetworkObserver`
- `FakeConnectivityRepository`
- `FakeClock`
- `FakeLogger`

Tester :

- repositories
- synchronisation
- persistance

### Tests UI

Compose :

- ajout SSID
- suppression
- affichage état
- paramètres

---

## CI

Configurer GitHub Actions.

Chaque Pull Request doit vérifier :

- compilation
- lint
- detekt
- tests
- couverture

Aucune PR ne doit être fusionnable si une étape échoue.

---

## Documentation

Créer `README.md` avec :

- présentation
- captures d'écran (placeholders)
- architecture
- compilation
- publication Play Store
- contribution
- licence

Créer également `ARCHITECTURE.md` décrivant :

- couches
- responsabilités
- moteur de règles
- dépendances
- diagrammes Mermaid lorsque cela apporte de la valeur

---

## Roadmap

Construire le projet dans cet ordre. Ne jamais sauter d'étape.

1. Initialisation du projet
2. Architecture
3. Configuration Hilt
4. Couche Tailscale
5. Observation réseau
6. Moteur de règles
7. Tests unitaires du moteur
8. Persistance
9. ViewModels
10. Interface Compose
11. Notifications
12. Paramètres
13. Journal
14. Tests UI
15. Documentation
16. GitHub Actions

Chaque étape doit être autonome.

Le projet doit rester compilable à tout moment.

---

## Git

Produire de petits commits cohérents.

Un commit = une seule fonctionnalité.

Les messages de commit suivent Conventional Commits : `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.

---

## Livrables

À chaque étape :

1. expliquer brièvement l'approche retenue ;
2. coder uniquement cette étape ;
3. ajouter les tests ;
4. vérifier la compilation ;
5. vérifier les tests ;
6. attendre la validation avant de poursuivre.

Le projet ne doit jamais contenir de code incomplet ou non testé.
