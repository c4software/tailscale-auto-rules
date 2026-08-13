# Déclarations de permissions et de services de premier plan

Textes à coller dans **Play Console → Contenu de l'application**. Deux
déclarations sont obligatoires et font l'objet d'un examen manuel : le type de
service de premier plan `specialUse`, et l'usage de la localisation.

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
> coordinates are stored or transmitted, and the app does not request
> ACCESS_BACKGROUND_LOCATION. The service reads the SSID only, and only if the
> user has granted the location permission.

---

## 3. Localisation en arrière-plan

L'application ne demande **pas** `ACCESS_BACKGROUND_LOCATION`. Si le formulaire
interroge malgré tout sur l'usage de la localisation :

> The location permission is used exclusively to read the name (SSID) of the
> Wi-Fi network the device is connected to — Android classifies this piece of
> information as location data and grants access to it on that basis only. The
> SSID is used to recognise the networks the user has marked as trusted, in
> order to turn the VPN tunnel off on those.
>
> No coordinates are ever read, no location data is stored or transmitted:
> nothing leaves the device. The permission is optional — if denied, the app
> keeps working, without recognising Wi-Fi networks by name.

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
les deux usages. Scénario, environ une minute :

1. Ouvrir l'application, montrer une règle « réseau de confiance » configurée
   avec le SSID du Wi-Fi domestique, et le mode « réactivité immédiate » activé.
2. Passer l'application en arrière-plan. Montrer la notification persistante du
   service : état du tunnel, règle appliquée, action de coupure. *(couvre
   `specialUse`)*
3. Se déconnecter du Wi-Fi de confiance, ou passer sur un autre réseau. Montrer
   la notification qui change d'état et le client Tailscale qui active le
   tunnel, sans que l'utilisateur ait rouvert l'application. *(couvre
   `location` : le SSID est lu par le service en arrière-plan)*
4. Revenir sur le réseau de confiance, montrer le tunnel qui se coupe.

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
| `POST_NOTIFICATIONS` | Notification d'état imposée au service de premier plan | Oui (API 33+) |
| `RECEIVE_BOOT_COMPLETED` | Réarmer l'automatisation après un redémarrage | Oui (réglage) |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Observation continue du réseau | Non |
| `FOREGROUND_SERVICE_LOCATION` | Lire le SSID depuis le service | Employée seulement si la localisation est accordée |
| `<queries> com.tailscale.ipn` | Détecter la présence du client officiel et lui adresser la commande | Non |

Aucune permission n'est demandée tant que la fonctionnalité qui la justifie
n'est pas active, et chaque demande est précédée d'un écran d'explication (le
parcours de premier lancement, puis les cartes d'explication des écrans
concernés).
