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
- **Réveil par le système** via `registerNetworkCallback(NetworkRequest,
  PendingIntent)` : aucun processus permanent, aucune notification imposée.
- **Persistance** : Room pour les réseaux de confiance et le journal (500
  entrées, purge transactionnelle), DataStore pour les préférences.
- **Quatre écrans** : accueil, réseaux de confiance, journal, paramètres.
- **Notification d'état** persistante et réellement optionnelle.
- **Journal** des 500 derniers changements, avec la règle qui a décidé.
- Explications de permission avant chaque demande (localisation, notification).

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
