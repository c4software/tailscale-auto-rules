# Journal des modifications

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).
Ce projet suit le [versionnage sémantique](https://semver.org/lang/fr/).

## [Non publié]

Première version fonctionnelle complète. Non encore publiée sur le Play Store :
voir les réserves de [TASKS.md](./TASKS.md).

### Ajouté

- **Moteur de règles** extensible (pattern Strategy). Ajouter une règle consiste
  à écrire une classe et une ligne d'enregistrement ; le moteur n'est jamais
  modifié.
- **Quatre règles** : mode avion, Wi-Fi de confiance, Wi-Fi non reconnu, réseau
  mobile. Priorités espacées de 100 pour qu'une règle future s'intercale sans
  renumérotation.
- **Pilotage du client Tailscale** par diffusion explicite vers son receveur
  exporté `IPNReceiver`.
- **Observation continue du réseau** par un service de premier plan : la bascule
  suit le changement de réseau en quelques secondes. Android impose la
  notification permanente qui va avec ; elle affiche l'état du tunnel et la
  règle appliquée, et n'est pas présentée comme un réglage puisqu'elle n'en est
  pas un.
- **Persistance** : Room pour les réseaux de confiance et le journal (500
  entrées, purge transactionnelle), DataStore pour les préférences.
- **Quatre écrans** : accueil, réseaux de confiance, journal, paramètres.
- **Notification d'état** persistante et réellement optionnelle.
- **Journal** des 500 derniers changements, avec la règle qui a décidé.
- **Reconnaissance des interventions manuelles** : un tunnel activé à la main
  sur un réseau de confiance (ou coupé depuis le client officiel) est signalé
  comme tel sur l'accueil et dans la notification, au lieu d'être attribué à
  une règle. Le geste est respecté ; les règles reprennent la main au prochain
  changement de réseau. Une entrée de journal plus jeune que dix secondes
  n'atteste de rien : la commande vient d'être envoyée et le tunnel n'a pas
  encore suivi — sans ce délai de grâce, chaque transition affichait
  fugitivement « Modifié manuellement ».
- Explications de permission avant chaque demande (localisation, notification).
- **Chaîne de publication** : un tag `v*` (ou un déclenchement manuel) construit
  en CI l'APK de production signé. Le keystore vit exclusivement dans les
  secrets du dépôt ; en son absence, `assembleRelease` produit un APK non
  signé.
- **Tests de rendu visuel** (Roborazzi) : 28 références couvrant les états les
  plus exposés des quatre écrans, en thème clair et sombre. Vérification
  volontairement hors CI — trop coûteuse par Pull Request — donc à la charge de
  l'auteur d'un changement d'interface.

### Corrigé

- **L'octroi de la localisation redémarre l'observation continue.** Les types
  du service de premier plan — dont « localisation », qui conditionne la
  lecture du SSID en arrière-plan — sont figés à son démarrage. Accordée
  service déjà lancé, la permission restait sans effet : le SSID demeurait
  expurgé et la règle des réseaux de confiance ne s'appliquait plus qu'à
  l'écran, via le bouton « Synchroniser ».
- **La notification laisse la bascule du tunnel se terminer** avant de relire
  son état : lu au moment même de l'événement, le réseau actif était encore en
  retard et la notification pouvait figer un état déjà faux.

### Notes de conception

- La notification est optionnelle **parce que** l'application n'emploie pas de
  service de premier plan, lequel l'imposerait sur Android 8 et suivants.
- L'accueil affiche l'état **constaté** du tunnel, jamais celui déduit de la
  dernière décision : une divergence reste ainsi visible.
- Un client Tailscale absent produit un état *indéterminé*, jamais *désactivé*.

### Réserves connues

- Le debounce ne s'applique pas au chemin des réveils ; l'effet est amorti par
  le court-circuit « état déjà atteint ».
- Une bascule du mode avion sans changement de réseau peut passer inaperçue.
- `lint` n'analyse pas les sources de test — plantage interne d'AGP 9.3.1.

Le détail et l'état de chaque point sont dans [TASKS.md](./TASKS.md).
