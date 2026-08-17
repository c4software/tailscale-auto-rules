# Déclarations de permissions et de services de premier plan

Textes à coller dans **Play Console → Contenu de l'application**. Trois
déclarations sont obligatoires et font l'objet d'un examen manuel : le type de
service de premier plan `specialUse`, l'usage de la localisation, et la
localisation en arrière-plan (§3).

Le formulaire de la Play Console et son examen manuel sont en anglais : **les
textes cités ci-dessous sont en anglais**, la prose qui les entoure reste en
français. Le sous-type déclaré dans le manifeste
(`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`) est rédigé dans la même langue que la
déclaration du §1, que Google compare à ce champ.

---

## 1. Service de premier plan — `specialUse`

**Fonctionnalité concernée :** observation continue de l'état du réseau.
**Tâche à cocher dans le formulaire :** *Other*, seule option proposée.

> The app enables or disables the user's Tailscale VPN tunnel according to rules
> they configure: trusted network, mobile data, airplane mode. Reacting to a
> network change requires being notified by
> `ConnectivityManager.NetworkCallback` at all times, which only a living
> process allows — hence a foreground service.
>
> No predefined type describes this need: it is neither a data sync, nor a
> connected device, nor media playback, nor location tracking in the usual
> sense. Hence `specialUse`.
>
> Service-free alternatives were measured and ruled out:
> `registerNetworkCallback(NetworkRequest, PendingIntent)` delivers a single
> wake-up before the system releases the registration, and periodic checks are
> capped at fifteen minutes by the platform — an app meant to follow the network
> that reacted a quarter of an hour later would not deliver the advertised
> service.
>
> The service performs no network communication of its own. Its notification
> shows the tunnel state, the rule applied, and lets the user turn the
> automation off.

**Sous-type déclaré dans le manifeste** (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`) :

> Continuous monitoring of network state in order to enable or disable the
> Tailscale VPN tunnel according to rules defined by the user. No other
> foreground service type describes this need: it is neither a data sync, nor a
> connected device, nor location tracking.

---

## 2. Service de premier plan — `location`

**Tâche à cocher dans le formulaire :** *Other*. Aucune des cases proposées ne
convient — ni *Background location updates*, ni *User-initiated location
sharing*, ni *Navigation*, ni *Geofencing* : le type `location` n'est déclaré
que pour lire le SSID.

> This app does not access any geographic location. The foreground service
> declares the "location" type solely because Android classifies the Wi-Fi
> network name (SSID) as location data since Android 10: without this type, the
> SSID is returned empty whenever the app is not in the foreground. The SSID is
> compared locally against a list of trusted networks defined by the user, in
> order to enable or disable the Tailscale VPN tunnel.
>
> No position is ever read: no GPS, no triangulation, no geofencing. No
> coordinates are stored or transmitted. The service reads the SSID only, and
> only if the user has granted the location permission. Background location
> ("Allow all the time") is offered as a strictly optional upgrade, for the
> sole purpose of letting the service restart by itself after the device
> reboots (see the background location declaration).

---

## 3. Localisation en arrière-plan — `ACCESS_BACKGROUND_LOCATION`

L'application demande `ACCESS_BACKGROUND_LOCATION`, **facultative et jamais
exigée** : elle n'est proposée que depuis une carte d'explication des
paramètres, quand la localisation de premier plan est déjà accordée et que le
démarrage au boot est activé. Cette déclaration fait l'objet d'un examen
manuel spécifique (« Location permissions » dans Play Console).

**Fonctionnalité principale à déclarer :** redémarrage automatique de
l'automatisation après un redémarrage de l'appareil.

> The app requests background location for a single, optional purpose:
> restarting its automation by itself after the device reboots.
>
> The app never accesses any geographic position — no GPS, no triangulation,
> no geofencing. Android classifies the Wi-Fi network name (SSID) as location
> data: the app reads the SSID, and nothing else, to recognise the networks
> the user has marked as trusted, and enables or disables the Tailscale VPN
> tunnel accordingly. The comparison happens entirely on the device; no
> location data is ever stored or transmitted.
>
> Background access serves one scenario only: the device rebooting. Because
> the SSID counts as location data, the service watching the network must
> declare the "location" foreground service type — and because a "while in
> use" grant is a foreground-only permission, Android refuses to start such a
> service from the BOOT_COMPLETED broadcast. "Allow all the time" is the only
> level that lets the automation resume on its own after a reboot.
>
> The permission is strictly optional. It is offered — never required — from
> the app's settings screen, next to an explanation of this trade-off. If the
> user declines, every feature keeps working: after each reboot, the app
> simply posts a notification inviting the user to open it, which restarts
> the automation.

**Vidéo :** la déclaration de localisation en arrière-plan exige elle aussi une
démonstration. Compléter le scénario du §4 d'une prise montrant la carte
« Toujours autoriser » des paramètres, puis un redémarrage de l'appareil suivi
de la notification d'état qui revient sans ouvrir l'application.

---

## 4. Vidéo de démonstration (obligatoire)

Les deux déclarations de service de premier plan exigent un **lien vidéo**, sans
lequel le formulaire ne peut être soumis. Le lien doit rester accessible sans
authentification (YouTube en « non répertoriée », ou Google Drive partagé à
« tous les utilisateurs disposant du lien ») et pointer directement sur la
vidéo, pas sur un dossier.

**Filmer sur un appareil réel, avec le vrai client Tailscale installé.** La
doublure `com.tailscale.ipn` employée pour les captures d'écran ne bascule aucun
tunnel : une vidéo tournée sur l'émulateur ne montrerait pas la fonctionnalité
déclarée, ce qui est le motif de rejet le plus courant.

Une seule vidéo peut servir aux deux déclarations, à condition qu'elle montre
les deux usages. Scénario tourné, quatre-vingt-quinze secondes :

1. Ouvrir l'application sur le réseau de confiance : tunnel coupé, nom du réseau
   affiché. Montrer l'onglet des réseaux et ses deux règles opposées, l'une
   coupant le tunnel, l'autre l'activant, toutes deux désignées par leur SSID.
2. Passer l'application en arrière-plan. Montrer la notification persistante du
   service : état du tunnel et règle appliquée. *(couvre `specialUse`)*
3. Changer de réseau Wi-Fi sans toucher à l'application. Montrer la notification
   qui bascule à « Tunnel on », avec pour motif la préférence de réseau. *(couvre
   `location` : la décision vient du seul SSID, lu en arrière-plan)*
4. Revenir dans l'application, puis sur le réseau de confiance : le tunnel se
   recoupe à l'écran.

⚠️ **Désactiver la règle des données mobiles avant de filmer.** Un changement de
Wi-Fi passe par un bref instant sans connectivité : la règle des données mobiles
s'applique alors la première, et l'application attribue la bascule à ce motif —
la vidéo ne démontre plus la lecture du SSID, donc plus rien qui justifie
`FOREGROUND_SERVICE_LOCATION`. Constaté à la première prise.

⚠️ **Ne jamais montrer l'écran d'accueil du téléphone** : fond d'écran et
applications personnelles s'y trouvent. Pour figurer l'arrière-plan, ouvrir un
écran de réglages Android sans données personnelles — celui de l'affichage
convient.

Commenter les étapes en anglais, à l'écrit (sous-titres ou cartons) ou à l'oral,
en nommant explicitement ce que la vidéo démontre — un relecteur qui ne fait pas
le lien entre le SSID et la permission de localisation rejette la déclaration.

---

## 5. Tableau de synthèse des permissions

| Permission | Motif | Facultative ? |
|---|---|---|
| `ACCESS_NETWORK_STATE` | Observer le transport réseau courant | Non |
| `ACCESS_WIFI_STATE` | Lire l'état du Wi-Fi | Non |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Lire le SSID courant (classé donnée de localisation par Android) | Oui |
| `ACCESS_BACKGROUND_LOCATION` | Redémarrer l'automatisation seule après un reboot : sans « Toujours autoriser », Android refuse de démarrer le service de type « localisation » depuis `BOOT_COMPLETED` | Oui — à défaut, une notification invite à ouvrir l'application |
| `POST_NOTIFICATIONS` | Notification d'état imposée au service de premier plan | Oui (API 33+) |
| `RECEIVE_BOOT_COMPLETED` | Réarmer l'automatisation après un redémarrage | Oui (réglage) |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Observation continue du réseau | Non |
| `FOREGROUND_SERVICE_LOCATION` | Lire le SSID depuis le service | Employée seulement si la localisation est accordée |
| `<queries> com.tailscale.ipn` | Détecter la présence du client officiel et lui adresser la commande | Non |

Aucune permission n'est demandée tant que la fonctionnalité qui la justifie
n'est pas active, et chaque demande est précédée d'un écran d'explication (le
parcours de premier lancement, puis les cartes d'explication des écrans
concernés).
