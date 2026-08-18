# Spécification fonctionnelle — Tailscale Auto Rules

Version du document : 1.1 · Dernière mise à jour : 2026-08-11

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
| paramètres | propres à la règle | Ex. les préférences par réseau pour la règle du même nom. |

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
- L'automatisation désactivée dans les paramètres → **aucune action**, et les
  règles ne sont même pas évaluées.
- Aucun client Tailscale installé → **aucune action**, signalée à l'utilisateur.
- Commande transmise mais refusée → **rien n'est consigné**.
- Sinon → commande au client Tailscale, puis écriture au journal.

#### Ce que « l'état a changé » signifie exactement

La version initiale de cette section disait « on n'écrit au journal que lorsque
l'état change **réellement** ». L'implémentation a montré que ce critère n'est
pas observable : le canal de commande du client officiel est asynchrone et sans
accusé de réception (§10.1). Attendre une confirmation reviendrait à sonder le
tunnel en boucle, sans garantie de terme.

Le critère retenu est donc : **une entrée est écrite lorsqu'une commande de
changement a été acceptée par le système, depuis un état constaté différent de
l'état visé.** Concrètement :

| Situation | Journal |
|---|---|
| État courant déjà égal à l'état visé | rien |
| Commande refusée ou client absent | rien |
| Commande transmise avec succès | une entrée |

L'écart résiduel — le client accepte la demande puis échoue silencieusement —
reste possible. Il est visible dans l'application : l'écran d'accueil affiche
l'état **constaté** du tunnel, pas la dernière décision. Une divergence entre
les deux se voit donc, au lieu d'être masquée.

#### Intervention manuelle de l'utilisateur

L'utilisateur peut changer l'état du tunnel sans passer par l'application —
typiquement l'activer depuis le client officiel alors qu'il est sur un réseau
de confiance. Ce geste est **mémorisé** : l'application enregistre une
**préférence de réseau** (§4.2), qui rejoue ce choix à chaque passage sur ce
réseau — changement de réseau, redémarrage et battement de secours compris.
Un nouveau geste sur le même réseau **remplace** la préférence — déclarée ou
apprise ; revenir au comportement automatique se fait en la supprimant sur
l'écran des réseaux (§6.2). L'enregistrement est consigné au journal sous la
règle « Préférence de réseau ».

Un redémarrage du terminal ne vaut jamais geste : il remet le tunnel dans son
état par défaut sans qu'aucune main n'y touche, et le journal de la session
précédente n'atteste plus de rien. La détection ne reprend qu'une fois la
décision réattestée par le premier cycle de la nouvelle session ; sans quoi
une préférence « toujours actif » se voyait remplacée par « toujours coupé »
à chaque redémarrage.

La mémorisation se coupe par le réglage « Apprendre mes gestes » (§6.3).
Coupée, le geste redevient éphémère : aucun cycle ne le combat tant que le
réseau ne change pas, et les règles reprennent la main au changement de réseau
suivant. Les préférences **déjà enregistrées** continuent en revanche de s'appliquer
tant qu'elles ne sont pas supprimées : le réglage gouverne l'apprentissage,
pas le rejeu.

Un geste n'est mémorisé que si le réseau courant est identifiable (§4.2) : en
Wi-Fi le SSID doit être lisible ; en cellulaire la préférence vaut pour toutes
les données mobiles ; en mode avion ou sans identifiant, rien n'est appris.
La mémorisation est retentée avant chaque cycle — battement de secours
compris — tant que le geste reste constaté : un instantané réseau perturbé au
moment de la bascule ne doit pas conduire l'automatisation à combattre le
geste au lieu de le mémoriser.

Le geste est **reconnu** (accueil et notification) sur un critère
précis : la décision courante des règles a déjà été appliquée — le journal en
atteste — et l'état constaté du tunnel la contredit pourtant. Une simple
divergence ne suffit pas : elle est normale pendant les quelques secondes qui
séparent un changement de réseau de l'application de la décision, et ne doit
pas s'afficher comme un geste de l'utilisateur. Le journal consignant la
commande à l'envoi — alors que le client met quelques secondes à l'exécuter —
une entrée plus jeune que dix secondes n'atteste de rien : sans ce délai de
grâce, chaque transition affichait fugitivement « Modifié manuellement ».

---

## 4. Règles de la version 1

Priorités : plus la valeur est basse, plus la règle est prioritaire.

| Prio. | Règle | Condition | Décision |
|---:|---|---|---|
| 100 | **Mode avion** | Mode avion actif | `DISABLE` |
| 150 | **Préférence de réseau** | Une préférence existe pour le réseau courant — déclarée ou apprise d'un geste | l'état choisi |
| 300 | **Wi-Fi** | Connecté en Wi-Fi, sans préférence | `ENABLE` |
| 400 | **Réseau mobile** | Connecté en cellulaire (LTE / 4G / 5G / NR) | `ENABLE` |
| — | *(aucune règle)* | Aucun réseau, ou réseau non couvert | `NO_DECISION` |

Dans tous les autres cas, chaque règle retourne `NO_DECISION`.

### 4.1 Détail — Mode avion

- Priorité maximale : elle prime sur toute autre considération.
- Elle se prononce uniquement quand le mode avion est **actif**. Mode avion
  inactif → `NO_DECISION` (et non `ENABLE`), afin de laisser les règles
  suivantes décider.

### 4.2 Détail — Préférence de réseau

Une seule notion couvre les réseaux de confiance d'hier et les gestes
appris : une **préférence par réseau**, à deux états fermes — tunnel
**toujours coupé** (le réseau est de confiance) ou **toujours actif** — et
dont l'**absence** vaut « automatique ».

- Deux sources, un même magasin : la **déclaration** sur l'écran des réseaux
  (§6.2), et l'**apprentissage** d'un geste manuel (§3.3). Un geste remplace
  la préférence du réseau, déclarée ou apprise — la dernière volonté gagne.
- Le réseau est identifié par une clé canonique :

| Réseau courant | Clé | Portée |
|---|---|---|
| Wi-Fi avec SSID lisible | `wifi:<ssid canonique>` | ce seul réseau |
| Cellulaire | `cellular` | toutes les données mobiles |
| Wi-Fi sans SSID, Ethernet, aucun réseau | — | pas de préférence |

- La comparaison du SSID est **insensible à la casse** et ignore les espaces
  de bordure ; elle ne dépend pas de la validation Internet — la confiance
  accordée à un réseau ne dépend pas de son accès Internet, et le VPN qui
  monte fait fugacement perdre sa validation au réseau porteur.
- Un SSID indisponible ne correspond à aucune clé : la règle s'abstient. La
  suite dépend du **pourquoi** :
  - **Permission absente, SSID masqué** : l'utilisateur a renoncé à identifier
    ses réseaux ; la règle « Wi-Fi » active le tunnel. En cas de doute, on
    protège la connexion.
  - **Lecture expurgée** (permission accordée, mais exécution hors des
    conditions du système : typiquement l'arrière-plan au boot, sans service
    de type « localisation ») : l'identité du réseau existe et une préférence
    « toujours coupé » peut la viser ; la règle « Wi-Fi » s'abstient **aussi**,
    et le tunnel reste en l'état jusqu'à une lecture complète. Sans cela,
    chaque redémarrage activait le tunnel sur le réseau de confiance de
    l'utilisateur.
- Priorité 150 : sous le mode avion — jamais d'application en avion — mais
  au-dessus des règles par défaut.
- Supprimer la préférence (glissement, §6.2) rend le réseau à l'automatisme.

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
- une carte signalant une **intervention manuelle** lorsque l'état constaté
  contredit une décision déjà appliquée (§3.3) : elle nomme la règle
  contredite et dit que le choix est mémorisé pour le réseau courant — ou
  simplement respecté jusqu'au prochain changement de réseau, si
  l'apprentissage est coupé ou le réseau non identifiable. Une fois le geste
  mémorisé, la préférence devient la décision courante et la carte se retire ;
- un bouton **Synchroniser** forçant un cycle immédiat ;
- un bouton **Désactiver l'automatisation** — remplacé, lorsqu'elle est déjà
  inactive, par une mention explicite renvoyant aux paramètres.

### 6.2 Réseaux

Un seul écran, une seule liste : les **préférences de réseau** (§4.2),
qu'elles aient été déclarées à la main ou apprises d'un geste. Les réseaux
absents de la liste suivent l'automatisme.

- Un interrupteur **Réseau mobile** active ou désactive la règle du même nom
  (§4.3). Il vit sur cet écran plutôt qu'aux paramètres : c'est là que
  l'utilisateur décide sur quels réseaux le tunnel monte. Sa bascule déclenche
  une synchronisation immédiate (§5) ; la désactivation laisse l'état du tunnel
  inchangé, aucune règle ne se prononçant plus (§3.2).
- Chaque entrée montre le réseau — SSID, ou « Données mobiles » — et son
  comportement : **tunnel coupé** ou **tunnel actif**, modifiable sur place ;
  le changement déclenche une synchronisation immédiate (§5).
- Ajout manuel d'un réseau avec choix du comportement — « coupé » par défaut :
  c'est le geste de confiance d'hier. Renommage d'un SSID. Un SSID en doublon
  est refusé au renommage, avec un message explicite ; l'ajout d'un réseau
  déjà connu remplace simplement son comportement.
- Action d'ajout rapide du **SSID courant**, désactivée si le SSID est
  indisponible.
- Un **glissement latéral** supprime la préférence : le réseau revient à
  l'automatisme, par une synchronisation immédiate.

### 6.3 Paramètres

| Réglage | Type | Défaut |
|---|---|---|
| Service d'automatisation actif | interrupteur | activé |
| Apprendre mes gestes (§3.3) | interrupteur | activé |
| Démarrage automatique au boot | interrupteur | activé |
| Notification persistante | interrupteur | désactivé |
| Journalisation détaillée | interrupteur | désactivé |
| Exemption d'optimisation de batterie | action | — |
| Revoir le premier lancement (§6.5) | action | — |
| Version de l'application | information | — |
| Licence | information | MIT |

L'apprentissage est proposé une seule fois, à la dernière page du premier
lancement (§6.5) : le choix — l'activer ou non — s'enregistre dans ce réglage,
que l'écran des paramètres permet de reprendre à tout moment.

### 6.4 Journal

- Les **500 derniers** événements, du plus récent au plus ancien.
- Chaque entrée : date/heure, ancien état, nouvel état, règle déclenchante,
  raison lisible.
- Au-delà de 500, les entrées les plus anciennes sont purgées.

### 6.5 Premier lancement

Au premier démarrage, un parcours d'accueil remplace l'application le temps de
quatre pages, qui avancent **au bouton seul** — le glissement est désactivé,
pour qu'aucune page de permission ne soit franchie sans que sa demande ait été
posée :

| Page | Contenu | Action |
|---|---|---|
| 1. Bienvenue | Ce que fait l'application — et ce qu'elle ne fait pas : elle pilote le client officiel, elle ne remplace aucune pile VPN | Continuer |
| 2. Notification | Pourquoi une notification permanente est imposée (§7) | Autoriser les notifications, puis Continuer |
| 3. Localisation | Pourquoi lire un SSID exige la localisation, et ce qui n'est **pas** lu (§8) | Autoriser la localisation, puis Continuer |
| 4. Apprentissage | La mémorisation des gestes (§3.3) | Activer · Ne pas activer |

Sur une page de permission, le bouton unique du bas porte la demande : le
premier appui la pose, et l'appui suivant — le bouton redevenant
« Continuer » — passe à la page suivante. Chaque demande est ainsi précédée de
son explication — ce sont ces pages qui tiennent le rôle d'écran préalable
exigé par §8 — et chacune peut être refusée : les cartes d'explication de
l'application (§6.2, §6.3) rattrapent un refus au moment où la fonctionnalité
en a réellement besoin. Un octroi fait avancer le parcours de lui-même — la
page a rempli son office ; un refus laisse la main, sans jamais enfermer le
parcours.

Le parcours ne se montre qu'une fois : la réponse de la dernière page vaut
choix d'apprentissage (§6.3) et clôture le premier lancement, quelle qu'elle
soit. Il peut être revu à tout moment depuis les paramètres (§6.3) — sa
clôture obéit alors aux mêmes règles.

---

## 7. Notification

Persistante lorsqu'elle est visible, elle affiche :

- **Tunnel :** Activé / Désactivé
- **Raison :** libellé court de la règle ayant décidé (« Réseau mobile »,
  « Mode avion », « Préférence de réseau »…) — ou la
  mention d'une intervention manuelle lorsque l'état constaté contredit une
  décision déjà appliquée (§3.3) : attribuer à une règle un état qu'elle n'a
  pas produit serait un mensonge. Un geste mémorisé s'affiche sous
  « Préférence de réseau » : c'est désormais cette règle qui maintient l'état
  voulu par l'utilisateur.

Elle se rafraîchit à chaque cycle **et** à chaque mouvement du tunnel constaté
hors cycle — activé à la main sur un réseau de confiance, coupé depuis le
client officiel — sans quoi elle décrirait un état périmé.

Elle porte une action **Désactiver l'automatisation** : persistante et non
rejetable, elle doit offrir le moyen de faire cesser ce qu'elle décrit sans
repasser par l'application. L'action bascule la préférence — la même que
l'interrupteur des paramètres — ce qui arrête le service et retire la
notification.

### 7.1 Elle n'est pas un réglage

Elle est visible exactement lorsque l'automatisation est active, et se retire
avec elle.

Observer le réseau exige un processus vivant, donc un service de premier plan,
auquel Android impose une notification depuis la version 8. Cette contrainte
n'est pas contournable : le seul mécanisme qui l'aurait permis —
`registerNetworkCallback(NetworkRequest, PendingIntent)` — ne délivre qu'un
réveil unique avant que le système ne relâche l'inscription. Mesuré sur
appareil ; les relevés sont à l'étape 17 de `TASKS.md`.

Une vérification périodique sans service a été envisagée pour s'en dispenser.
Elle est écartée : la plateforme n'accepte pas de période plus courte que
quinze minutes, et une application censée suivre le réseau qui réagirait un
quart d'heure plus tard ne rendrait pas le service promis.

L'écran des paramètres **explique** donc la notification au lieu de l'offrir.
Un interrupteur, fût-il grisé, laisserait croire à un choix qui n'existe pas —
et un interrupteur coché puis désactivé se lit presque comme un interrupteur
éteint.

### 7.2 Le rappel de démarrage

Au redémarrage du terminal, le service ne peut pas repartir seul dès que la
localisation est accordée : c'est une permission de premier plan, et Android
refuse de démarrer depuis l'arrière-plan le service de type « localisation »
qu'impose la lecture du SSID. Une notification ponctuelle invite alors à
ouvrir l'application pour démarrer la synchronisation ; l'application ne
demande pas `ACCESS_BACKGROUND_LOCATION`, qui aurait levé la restriction au
prix d'une permission trop invasive.
Elle vit sur un canal « Rappels » distinct, d'importance normale : elle attend
un geste, là où la notification d'état se consulte. Rejetable, à disparition
automatique au toucher, elle est retirée dès que le service démarre.

Elle peut mettre une à deux minutes à paraître : Android livre
`BOOT_COMPLETED` aux applications une à une, dans une file que l'application
ne contrôle pas.

---

## 8. Permissions

| Permission | Motif | Caractère |
|---|---|---|
| `ACCESS_NETWORK_STATE` | Observer le réseau | Indispensable |
| `RECEIVE_BOOT_COMPLETED` | Redémarrer le service après un boot | Indispensable si l'option est activée |
| `POST_NOTIFICATIONS` | Notification d'état (API 33+) | Demandée dès que l'automatisation est active |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Observation continue du réseau | Indispensable |
| `FOREGROUND_SERVICE_LOCATION` | Lire le SSID depuis le service : Android le classe comme donnée de localisation, et un service n'y accède que de ce type | Employée uniquement si la localisation est accordée |
| `ACCESS_FINE_LOCATION` | Lecture du SSID courant | Demandée **uniquement** lorsqu'une règle Wi-Fi est activée, avec écran d'explication préalable |
| `FOREGROUND_SERVICE` + type | Service d'observation | Selon l'architecture retenue à l'étape correspondante |

Règle transverse : **aucune permission n'est demandée tant que la fonctionnalité
qui la justifie n'est pas activée par l'utilisateur.** Chaque demande est
précédée d'un écran expliquant l'usage, conformément aux exigences du Play
Store. Au premier lancement, les pages du parcours d'accueil (§6.5) tiennent
ce rôle — les fonctionnalités concernées, automatisation et règles Wi-Fi,
étant actives par défaut — et un refus y reste sans conséquence : les cartes
d'explication de l'application redemandent au moment du besoin réel.

---

## 9. Persistance

| Donnée | Support | Motif |
|---|---|---|
| Préférences de réseau (§4.2) | Room | Collection, une entrée par clé réseau, contrainte d'unicité |
| Journal (500 entrées) | Room | Collection ordonnée avec purge |
| Préférences (§6.3) | DataStore Preferences | Valeurs scalaires isolées |
| Configuration des règles (`enabled`, `priority`) | DataStore Preferences | Valeurs scalaires par règle |

Les deux supports ne sont **jamais** mélangés : aucune préférence en base,
aucune collection en DataStore.

---

## 10. Points ouverts

### 10.1 Pilotage effectif du client Tailscale — *résolu*

**Le client officiel prévoit explicitement ce cas d'usage.** Le spike de
l'étape 4 a établi que `com.tailscale.ipn` déclare un receveur de diffusion
exporté, dont le commentaire de code est sans ambiguïté :

```java
/** IPNReceiver allows external applications to start the VPN. */
public class IPNReceiver extends BroadcastReceiver {
    public static final String INTENT_CONNECT_VPN    = "com.tailscale.ipn.CONNECT_VPN";
    public static final String INTENT_DISCONNECT_VPN = "com.tailscale.ipn.DISCONNECT_VPN";
```

Il est déclaré `android:exported="true"` sans permission requise : une
application tierce peut lui adresser une diffusion. Une troisième action,
`com.tailscale.ipn.USE_EXIT_NODE`, sélectionne un nœud de sortie — hors périmètre
de la version 1, mais elle confirme que le canal est prévu pour durer.

Trois contraintes en découlent, portées par le contrat `TailscaleController` :

1. **Aucun accusé de réception.** Le receveur enfile un `WorkManager` et rend
   la main. Un `Result` réussi signifie « demande transmise », jamais « tunnel
   actif ». Seul `isRunning()` fait foi sur l'état réel, et il peut mettre un
   instant à refléter la demande.
2. **La diffusion doit être explicite** — composant désigné nommément. Une
   diffusion implicite n'atteindrait pas le receveur et serait de surcroît
   restreinte depuis Android 8.
3. **La visibilité du paquet doit être déclarée** dans notre manifeste
   (`<queries>`), sans quoi Android 11+ répond « paquet introuvable » et
   l'application conclut à tort à l'absence du client.

**Limite résiduelle assumée.** Android n'expose pas quelle application porte un
tunnel VPN actif. `isRunning()` détecte donc « un VPN est actif », et non
« *ce* VPN est actif ». C'est suffisant tant que l'utilisateur n'emploie qu'un
seul VPN, ce qui est le cas nominal ; l'approximation reste documentée dans le
code plutôt que masquée.

**Risque de pérennité.** Ce canal n'est pas contractuel : Tailscale peut le
modifier. C'est précisément pourquoi il est isolé derrière une interface (voir
[ARCHITECTURE.md](./ARCHITECTURE.md) §4). En cas de rupture, seule
`AndroidTailscaleController` est à reprendre ; ni le moteur, ni les règles, ni
l'interface ne sont concernés.

### 10.2 Portée de la version 1

Les règles listées en §4 constituent la version 1. Les axes d'évolution
(whitelist, BSSID, regex, horaires, batterie, Bluetooth, Exit Node…) sont
mentionnés dans [PROMPT.md](./PROMPT.md) et doivent rester réalisables **sans
modifier le moteur**. Ils ne sont pas planifiés à ce stade.

### 10.3 Apprentissage et VPN tiers

Android ne dit pas quelle application porte le tunnel actif (§10.1). Un autre
VPN qui monte ou descend peut donc être pris pour un geste sur Tailscale, et
créer une préférence erronée. Trois garde-fous : le délai de grâce de dix
secondes (§3.3), la visibilité des préférences sur l'écran des réseaux —
supprimables en un geste —, et le réglage « Apprendre mes
gestes ». Le cas nominal — un seul VPN sur le terminal — n'est pas affecté.

---

## 11. Critères d'acceptation transverses

- Toute décision appliquée est justifiable par une règle nommée et retrouvable
  au journal.
- Aucune décision n'est appliquée si l'état visé est déjà l'état courant.
- Le moteur et les règles s'exécutent intégralement dans un test JVM, sans
  émulateur ni instrumentation.
- L'application reste fonctionnelle si le client Tailscale est absent : elle le
  signale et n'entre pas en erreur.
