# Journal des modifications

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).
Ce projet suit le [versionnage sémantique](https://semver.org/lang/fr/).

## [1.1.0] — 2026-08-13

### Modifié

- **Le parcours de premier lancement demande la permission au bouton.** Sur les
  pages notification et localisation, la demande part désormais du bouton du
  bas : le premier appui la pose, l'appui suivant passe à la page suivante. Le
  bouton « Autoriser » interne, qui pouvait rester sous le pli, disparaît.
- **Glissement retiré du parcours de premier lancement** : les pages n'avancent
  plus qu'au bouton, pour qu'aucune page de permission ne soit franchie sans
  que sa demande ait été posée.

## [1.0.1] — 2026-08-13

Aucun changement de comportement : seuls le dossier de soumission et la
déclaration que le manifeste porte à l'intention de Google évoluent.

### Modifié

- **Déclarations de services de premier plan rédigées en anglais.** Le
  formulaire de la Play Console et son examen manuel se font dans cette langue,
  et Google compare la déclaration « usage particulier » au sous-type déclaré
  dans le manifeste : les deux sont désormais anglais, à l'identique.
- **Scénario de la vidéo de démonstration**, que les deux formulaires exigent,
  remplacé par celui réellement tourné sur appareil, et assorti des deux
  écueils qui ont invalidé les premières prises : la règle des données mobiles
  qui s'attribue la bascule pendant le trou de connectivité d'un changement de
  Wi-Fi, et l'écran d'accueil du téléphone qui expose des données personnelles.

### Corrigé

- **Un mode « réactivité immédiate » qui n'a jamais existé.** Le manifeste et le
  scénario de tournage évoquaient un réglage absent du code : le service observe
  le réseau tant que l'automatisation est activée, sans option dédiée.

## [1.0.0] — 2026-08-12

Première version publique. Le dossier de soumission — textes des deux fiches,
captures d'écran réelles, icône, image mise en avant, réponses aux formulaires —
est réuni dans [`store/`](./store), et la politique de confidentialité est
publiée sur GitHub Pages par la CI.

### Ajouté depuis la première version

- **Dossier de soumission Play Store.** Fiches anglaise et française, six
  captures par langue prises sur émulateur, icône 512 et image mise en avant,
  réponses aux formulaires de sécurité des données et de contenu, déclarations
  des types de service de premier plan, politique de confidentialité publiée
  automatiquement sur GitHub Pages.

- **Traduction anglaise.** L'anglais devient la langue par défaut — toute
  locale sans traduction dédiée l'obtient — et le français reste servi aux
  locales francophones.
- **Préférences de réseau : la blacklist et les gestes fusionnent.** Une seule
  notion par réseau — tunnel toujours coupé (le réseau de confiance d'hier) ou
  toujours actif, l'absence valant automatisme — alimentée par la déclaration
  sur l'écran des réseaux comme par les gestes appris, la dernière volonté
  gagnant quelle que soit son origine. L'écran des réseaux devient la liste
  unique : volonté modifiable sur place, ajout avec choix du comportement,
  renommage, glissement pour rendre le réseau à l'automatisme. Migration de
  base fusionnante (Room v3) : les réseaux de confiance existants deviennent
  des préférences « toujours coupé », sauf là où un geste plus récent a déjà
  tranché.
- **Parcours de premier lancement.** Quatre pages — bienvenue, notification,
  localisation, apprentissage — qui avancent d'un bouton ou d'un glissement.
  Chaque demande de permission y est précédée de son explication, et un refus
  reste rattrapé par les cartes de l'application au moment du besoin réel. La
  dernière page recueille le choix d'apprentissage et remplace l'invitation
  qui vivait sur l'accueil.
- **Exceptions dynamiques : l'application apprend vos gestes.** Activer ou
  couper Tailscale à la main sur un réseau mémorise ce choix — par SSID en
  Wi-Fi, globalement en données mobiles — et le rejoue à chaque retour sur ce
  réseau, changements de réseau, redémarrages et battement de secours compris.
  Un nouveau geste remplace la mémoire du réseau ; l'écran des réseaux liste
  les exceptions apprises et les supprime d'un glissement, ce qui rend
  immédiatement la main au comportement automatique. L'apprentissage est
  proposé au premier lancement, actif par défaut, et débrayable dans les
  paramètres — les exceptions déjà apprises continuent alors de se rejouer
  tant qu'elles ne sont pas supprimées. Limite assumée : Android ne disant pas
  quelle application porte le VPN, un autre VPN qui monte peut être pris pour
  un geste (SPECS §10.3) ; le cellulaire n'ayant aucun identifiant,
  l'exception y est globale. Première migration de base du projet (Room v2),
  éprouvée par un test contre les schémas versionnés.
- **Interrupteur « Réseau mobile »** sur l'écran des réseaux de confiance : la
  règle qui active le tunnel en données mobiles peut désormais être coupée sans
  toucher au reste de l'automatisation. La bascule déclenche une
  synchronisation immédiate ; la priorité éventuellement personnalisée de la
  règle est conservée.

### Ajouté

- **Version dérivée de l'étiquette Git.** `versionName` et `versionCode` ne se
  saisissent plus : deux sources de vérité pour une même version sont une
  divergence programmée. Publier se réduit à poser une étiquette.
- **Chaîne de publication éprouvée.** Elle vérifie avant de signer, constate que
  l'artefact est réellement signé plutôt que de le supposer, contrôle qu'il porte
  bien la version de l'étiquette, efface la clé quoi qu'il arrive, et crée la
  publication GitHub. Elle n'est jamais déclenchée par un `push`.
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
- **La raison affichée décrit le réseau courant**, et non plus la dernière
  entrée du journal. Le journal ne consigne que les changements d'état
  effectifs : une règle qui confirme un état déjà atteint — passer aux données
  mobiles alors que le tunnel est déjà actif — n'y laisse rien, et la
  notification restait sur la raison d'un changement révolu, jusqu'à afficher
  « Tunnel activé » sous « Raison : Wi-Fi de confiance ». Le journal étant
  persistant, ni le redémarrage du service ni celui de l'application n'y
  changeaient quoi que ce soit.
- **La notification est recalée à chaque démarrage du service.** Android exige
  une notification immédiate, donc publiée sur un état indéterminé ; le
  recalage n'avait lieu qu'au premier cycle. Quand l'observation tournait déjà,
  « État du tunnel indéterminé » pouvait rester affiché des heures.
- **Un battement de secours** relit le réseau toutes les quinze minutes. Un
  rappel du système qui cesse de livrer figeait jusqu'ici l'automatisation sans
  rien signaler ; la panne est désormais bornée à un quart d'heure.

### Notes de conception

- La notification est optionnelle **parce que** l'application n'emploie pas de
  service de premier plan, lequel l'imposerait sur Android 8 et suivants.
- L'accueil affiche l'état **constaté** du tunnel, jamais celui déduit de la
  dernière décision : une divergence reste ainsi visible.
- La notification décrit le **présent** — état constaté, règle applicable au
  réseau courant. Le journal, lui, atteste de ce qui a **eu lieu** ; les deux
  divergent dès qu'une règle confirme un état déjà atteint.
- Un client Tailscale absent produit un état *indéterminé*, jamais *désactivé*.

### Réserves connues

- Le debounce ne s'applique pas au chemin des réveils ; l'effet est amorti par
  le court-circuit « état déjà atteint ».
- Une bascule du mode avion sans changement de réseau peut passer inaperçue.
- `lint` n'analyse pas les sources de test — plantage interne d'AGP 9.3.1.

Le détail et l'état de chaque point sont dans [TASKS.md](./TASKS.md).
