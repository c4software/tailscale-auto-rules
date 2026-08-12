# Déclarations de permissions et de services de premier plan

Textes à coller dans **Play Console → Contenu de l'application**. Deux
déclarations sont obligatoires et font l'objet d'un examen manuel : le type de
service de premier plan `specialUse`, et l'usage de la localisation.

---

## 1. Service de premier plan — `specialUse`

**Fonctionnalité concernée :** observation continue de l'état du réseau.

> L'application active ou désactive le tunnel VPN Tailscale de l'utilisateur
> selon des règles qu'il configure : réseau de confiance, données mobiles, mode
> avion. Réagir à un changement de réseau exige d'être notifié par
> `ConnectivityManager.NetworkCallback` en permanence, ce qu'un processus vivant
> seul permet — donc un service de premier plan.
>
> Aucun type prédéfini ne décrit ce besoin : il ne s'agit ni de synchronisation
> de données, ni d'appareil connecté, ni de lecture multimédia, ni de
> localisation au sens usuel. D'où `specialUse`.
>
> Les solutions sans service ont été mesurées et écartées :
> `registerNetworkCallback(NetworkRequest, PendingIntent)` ne délivre qu'un
> réveil unique avant que le système ne relâche l'inscription, et une
> vérification périodique est plafonnée à quinze minutes par la plateforme —
> une application censée suivre le réseau qui réagirait un quart d'heure plus
> tard ne rendrait pas le service annoncé.
>
> Le service n'effectue aucune communication réseau pour son compte. Sa
> notification affiche l'état du tunnel, la règle appliquée, et permet de
> couper l'automatisation.

**Sous-type déclaré dans le manifeste** (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`) :
observation continue de l'état du réseau afin d'activer ou de désactiver le
tunnel Tailscale selon les règles de l'utilisateur.

---

## 2. Service de premier plan — `location`

> Le service lit le nom (SSID) du réseau Wi-Fi courant pour reconnaître les
> réseaux de confiance de l'utilisateur. Depuis Android 10, un service de
> premier plan n'accède à cette information que s'il se déclare de type
> `location` : sans cela, le nom du réseau revient vide en arrière-plan et la
> règle des réseaux de confiance ne peut jamais s'appliquer.
>
> Aucune position n'est lue : ni GPS, ni triangulation, ni géorepérage. Le
> service ne consulte que le SSID, et uniquement si l'utilisateur a accordé la
> permission de localisation.

---

## 3. Localisation en arrière-plan

L'application ne demande **pas** `ACCESS_BACKGROUND_LOCATION`. Si le formulaire
interroge malgré tout sur l'usage de la localisation :

> La permission de localisation sert exclusivement à lire le nom (SSID) du
> réseau Wi-Fi auquel l'appareil est connecté — Android classe cette
> information parmi les données de localisation et n'y donne accès qu'à ce
> titre. Le SSID sert à reconnaître les réseaux que l'utilisateur a déclarés de
> confiance, afin de couper le tunnel VPN sur ceux-là.
>
> Aucune coordonnée n'est lue, aucune donnée de localisation n'est enregistrée
> ni transmise : rien ne quitte l'appareil. La permission est facultative —
> refusée, l'application continue de fonctionner sans reconnaître les réseaux
> Wi-Fi par leur nom.

---

## 4. Tableau de synthèse des permissions

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
