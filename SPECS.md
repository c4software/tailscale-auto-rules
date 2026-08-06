# Spécification fonctionnelle — Tailscale Auto Rules

Version du document : 1.0 · Dernière mise à jour : 2026-08-06

Ce document décrit **ce que** fait l'application. Le **comment** est décrit dans
[ARCHITECTURE.md](./ARCHITECTURE.md), l'ordre de construction dans
[TASKS.md](./TASKS.md).

---

## 1. Positionnement

**Tailscale Auto Rules** est une application Android compagnon de
[Tailscale](https://tailscale.com). Elle **ne remplace pas** le client Tailscale
et n'implémente aucune pile VPN : elle se contente de **demander l'activation ou
la désactivation du tunnel** en fonction de règles configurables par
l'utilisateur.

Principes directeurs :

- **Transparence** — une fois configurée, l'application ne demande plus rien.
  Aucune interaction n'est nécessaire au quotidien.
- **Prévisibilité** — toute décision est justifiée par une règle nommée, et
  consignée au journal.
- **Non-intrusion** — aucune permission qui ne serve directement une règle
  activée.

---

## 2. Glossaire

| Terme | Définition |
|---|---|
| **Tunnel** | Le VPN géré par l'application Tailscale officielle. |
| **Règle** | Unité de décision autonome, activable, priorisée et paramétrable. |
| **Décision** | Résultat de l'évaluation d'une règle : `ENABLE`, `DISABLE` ou `NO_DECISION`. |
| **Moteur** | Composant qui trie les règles, les évalue et retient la première décision ferme. |
| **Contexte réseau** | Instantané de l'état observable du terminal, seule entrée du moteur. |
| **Synchronisation** | Cycle complet : capture du contexte → évaluation → application → journalisation. |

---

## 3. Modèle de décision

### 3.1 Contrat d'une règle

Une règle expose :

| Propriété | Type | Rôle |
|---|---|---|
| `id` | identifiant stable | Clé de persistance et d'affichage. Ne change jamais. |
| `enabled` | booléen | Une règle désactivée n'est pas évaluée. |
| `priority` | entier | Ordre d'évaluation. Plus la valeur est **basse**, plus la règle passe **tôt**. |
| paramètres | propres à la règle | Ex. la liste de SSID pour la règle blacklist. |

Elle expose une unique opération : à partir d'un **contexte réseau**, produire
une **décision**.

### 3.2 Algorithme du moteur

1. Ne retenir que les règles `enabled`.
2. Les trier par `priority` croissante. À priorité égale, l'ordre est celui de
   l'`id` (tri stable et reproductible).
3. Les évaluer dans cet ordre.
4. **S'arrêter à la première décision différente de `NO_DECISION`** et la
   retourner, accompagnée de l'identité de la règle et de sa raison.
5. Si aucune règle ne se prononce, le résultat global est `NO_DECISION` :
   **l'état du tunnel est laissé inchangé**.

**Invariants :**

- Une règle ne connaît jamais les autres règles, ni le moteur, ni l'état courant
  du tunnel.
- L'évaluation est une fonction pure : même contexte → même décision.
- L'évaluation n'a aucun effet de bord (ni I/O, ni journalisation, ni horloge).

### 3.3 Application de la décision

- Décision `ENABLE` alors que le tunnel est déjà actif → **aucune action**.
- Décision `DISABLE` alors que le tunnel est déjà inactif → **aucune action**.
- `NO_DECISION` → **aucune action**.
- Sinon → appel au contrôleur Tailscale, puis écriture d'une entrée au journal.

Autrement dit : **on n'écrit au journal que lorsque l'état change réellement.**

---

## 4. Règles de la version 1

Priorités : plus la valeur est basse, plus la règle est prioritaire.

| Prio. | Règle | Condition | Décision |
|---:|---|---|---|
| 100 | **Mode avion** | Mode avion actif | `DISABLE` |
| 200 | **Wi-Fi blacklisté** | Connecté en Wi-Fi, SSID présent dans la blacklist | `DISABLE` |
| 300 | **Wi-Fi non blacklisté** | Connecté en Wi-Fi, SSID absent de la blacklist | `ENABLE` |
| 400 | **Réseau mobile** | Connecté en cellulaire (LTE / 4G / 5G / NR) | `ENABLE` |
| — | *(aucune règle)* | Aucun réseau, ou réseau non couvert | `NO_DECISION` |

Dans tous les autres cas, chaque règle retourne `NO_DECISION`.

### 4.1 Détail — Mode avion

- Priorité maximale : elle prime sur toute autre considération.
- Elle se prononce uniquement quand le mode avion est **actif**. Mode avion
  inactif → `NO_DECISION` (et non `ENABLE`), afin de laisser les règles
  suivantes décider.

### 4.2 Détail — Wi-Fi blacklisté

- L'utilisateur gère une liste de SSID (voir §6.2).
- La comparaison est **insensible à la casse** et ignore les espaces de bordure.
- Un SSID indisponible (permission absente, SSID masqué) est traité comme
  **non blacklisté** : la règle retourne `NO_DECISION` et laisse la règle
  « Wi-Fi non blacklisté » activer le tunnel. Le choix est délibéré : en cas de
  doute, on protège la connexion.

### 4.3 Détail — Réseau mobile

- Se prononce sur tout transport cellulaire, sans distinction de génération. La
  granularité 4G / 5G / NR est un paramètre d'évolution, pas une condition
  d'entrée de la version 1.

### 4.4 Détail — Aucun réseau

- Absence de réseau, ou réseau sans validation Internet : aucune règle ne se
  prononce. L'état du tunnel est conservé tel quel.

---

## 5. Déclencheurs de synchronisation

Une synchronisation est déclenchée par :

| Déclencheur | Source |
|---|---|
| Changement de réseau (disponible, perdu, capacités modifiées) | `ConnectivityManager.NetworkCallback` |
| Bascule du mode avion | Diffusion système |
| Démarrage du terminal | `BOOT_COMPLETED` (si le démarrage automatique est activé) |
| Action manuelle | Bouton « Synchroniser » de l'écran d'accueil |
| Modification de la configuration | Ajout/suppression d'un SSID, activation/désactivation d'une règle |

**Debounce** — les changements réseau arrivent en rafale (association,
obtention d'adresse, validation Internet). Les déclencheurs réseau sont donc
regroupés sur une fenêtre glissante ; seule la dernière valeur de la fenêtre est
évaluée. La synchronisation manuelle n'est **jamais** retardée.

**Évaluation conditionnelle** — si le contexte réseau capturé est identique au
précédent, aucune évaluation n'a lieu.

---

## 6. Interface utilisateur

### 6.1 Accueil

Affiche, en lecture seule :

- l'état du tunnel (actif / inactif / inconnu) ;
- le type de réseau courant (Wi-Fi / cellulaire / Ethernet / aucun) ;
- le SSID courant, ou une mention explicite s'il est indisponible ;
- la dernière décision : règle déclenchante, sens de la décision, horodatage ;
- un bouton **Synchroniser** forçant un cycle immédiat.

### 6.2 Blacklist Wi-Fi

- Liste des SSID enregistrés.
- Ajout manuel, modification, suppression (CRUD complet).
- Action d'ajout rapide du **SSID courant**, désactivée si le SSID est
  indisponible.
- Un SSID en doublon est refusé, avec un message explicite.

### 6.3 Paramètres

| Réglage | Type | Défaut |
|---|---|---|
| Service d'automatisation actif | interrupteur | activé |
| Démarrage automatique au boot | interrupteur | activé |
| Notification persistante | interrupteur | désactivé |
| Journalisation détaillée | interrupteur | désactivé |
| Exemption d'optimisation de batterie | action | — |
| Version de l'application | information | — |
| Licence | information | MIT |

### 6.4 Journal

- Les **500 derniers** événements, du plus récent au plus ancien.
- Chaque entrée : date/heure, ancien état, nouvel état, règle déclenchante,
  raison lisible.
- Au-delà de 500, les entrées les plus anciennes sont purgées.

---

## 7. Notification

Optionnelle (désactivée par défaut). Lorsqu'elle est active, elle est
persistante et affiche :

- **Tunnel :** Activé / Désactivé
- **Raison :** libellé court de la règle ayant décidé (« Réseau mobile »,
  « Wi-Fi de confiance », « Mode avion »…)

---

## 8. Permissions

| Permission | Motif | Caractère |
|---|---|---|
| `ACCESS_NETWORK_STATE` | Observer le réseau | Indispensable |
| `RECEIVE_BOOT_COMPLETED` | Redémarrer le service après un boot | Indispensable si l'option est activée |
| `POST_NOTIFICATIONS` | Notification persistante (API 33+) | Demandée **uniquement** si l'option est activée |
| `ACCESS_FINE_LOCATION` | Lecture du SSID courant | Demandée **uniquement** lorsqu'une règle Wi-Fi est activée, avec écran d'explication préalable |
| `FOREGROUND_SERVICE` + type | Service d'observation | Selon l'architecture retenue à l'étape correspondante |

Règle transverse : **aucune permission n'est demandée tant que la fonctionnalité
qui la justifie n'est pas activée par l'utilisateur.** Chaque demande est
précédée d'un écran expliquant l'usage, conformément aux exigences du Play
Store.

---

## 9. Persistance

| Donnée | Support | Motif |
|---|---|---|
| Blacklist de SSID | Room | Collection interrogeable, CRUD, contrainte d'unicité |
| Journal (500 entrées) | Room | Collection ordonnée avec purge |
| Préférences (§6.3) | DataStore Preferences | Valeurs scalaires isolées |
| Configuration des règles (`enabled`, `priority`) | DataStore Preferences | Valeurs scalaires par règle |

Les deux supports ne sont **jamais** mélangés : aucune préférence en base,
aucune collection en DataStore.

---

## 10. Points ouverts

### 10.1 Pilotage effectif du client Tailscale

Le client Tailscale Android n'expose pas d'API publique documentée et stable
permettant à une application tierce d'activer ou de désactiver le tunnel. C'est
le principal risque du projet, et il est **fonctionnel**, non architectural.

L'architecture le circonscrit délibérément : le moteur, les règles, la
persistance et l'interface ne dépendent que de l'interface `TailscaleController`
(voir [ARCHITECTURE.md](./ARCHITECTURE.md) §4). Une implémentation `Fake`
permet de développer et de tester **l'intégralité** du projet sans dépendre de
la résolution de ce point.

Pistes à évaluer lors de l'étape « Couche Tailscale » :

1. Intent ou service exporté par le paquet `com.tailscale.ipn`, s'il en existe
   un de documenté ;
2. Tuile de réglages rapides / raccourci exposé par le client officiel ;
3. `VpnService.prepare` et gestion de l'application VPN toujours active ;
4. À défaut, mode « assisté » : l'application détecte et notifie, l'utilisateur
   confirme d'un geste.

**Cette étape doit commencer par un spike de vérification**, dont le résultat
peut modifier la présente section. Aucune ligne de code métier ne doit
présupposer l'issue.

### 10.2 Portée de la version 1

Les règles listées en §4 constituent la version 1. Les axes d'évolution
(whitelist, BSSID, regex, horaires, batterie, Bluetooth, Exit Node…) sont
mentionnés dans [PROMPT.md](./PROMPT.md) et doivent rester réalisables **sans
modifier le moteur**. Ils ne sont pas planifiés à ce stade.

---

## 11. Critères d'acceptation transverses

- Toute décision appliquée est justifiable par une règle nommée et retrouvable
  au journal.
- Aucune décision n'est appliquée si l'état visé est déjà l'état courant.
- Le moteur et les règles s'exécutent intégralement dans un test JVM, sans
  émulateur ni instrumentation.
- L'application reste fonctionnelle si le client Tailscale est absent : elle le
  signale et n'entre pas en erreur.
