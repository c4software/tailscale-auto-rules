# Politique de confidentialité — Tailscale Auto Rules

Dernière mise à jour : 12 août 2026

## En résumé

Tailscale Auto Rules ne collecte rien, n'envoie rien et ne conserve rien hors
de votre appareil. Aucun serveur, aucun compte, aucune télémétrie, aucune
publicité.

## Données manipulées par l'application

Tout ce qui suit reste sur votre téléphone, dans le stockage privé de
l'application, et disparaît à sa désinstallation :

| Donnée | Pourquoi | Destination |
|---|---|---|
| Noms (SSID) des réseaux Wi-Fi que vous ajoutez ou que l'application apprend | Reconnaître un réseau et lui appliquer le comportement choisi | Base locale, sur l'appareil uniquement |
| Les 500 derniers changements d'état du tunnel (date, ancien état, nouvel état, règle) | Vous permettre de vérifier pourquoi le tunnel a changé | Base locale, sur l'appareil uniquement |
| Vos réglages (automatisation, apprentissage des gestes, démarrage au boot, journalisation détaillée) | Mémoriser vos choix | Préférences locales, sur l'appareil uniquement |

L'application ne collecte ni votre nom, ni votre adresse électronique, ni vos
contacts, ni vos fichiers, ni votre position, ni votre navigation, ni aucun
identifiant d'appareil.

## Permission de localisation

Android classe le nom du réseau Wi-Fi connecté (SSID) parmi les données de
localisation, et n'y donne accès qu'aux applications disposant d'une permission
de localisation. C'est l'unique raison pour laquelle l'application demande
`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`.

L'application lit le nom du réseau, et rien d'autre. Elle ne lit aucune
coordonnée GPS, ne suit aucun déplacement et ne transmet rien nulle part. La
permission peut être refusée : l'application continue de fonctionner, mais ne
peut plus reconnaître vos réseaux Wi-Fi par leur nom.

## Service de premier plan

L'application exécute un service de premier plan afin d'observer les
changements de réseau en continu. Android impose à un tel service d'afficher
une notification permanente, qui montre l'état du tunnel et la règle qui l'a
décidé. Ce service n'effectue aucune communication réseau pour son compte.

## Interaction avec le client Tailscale

L'application adresse une diffusion Android locale au client Tailscale officiel
(`com.tailscale.ipn`) pour lui demander de connecter ou de déconnecter le
tunnel. Aucune autre donnée n'est échangée, et l'application n'a accès ni à
votre compte Tailscale, ni à vos appareils, ni à votre trafic.

## Enfants

L'application ne s'adresse pas aux enfants et ne collecte de données sur
personne.

## Modifications

Toute modification de cette politique sera publiée dans le dépôt public de
l'application, avec la version à laquelle elle s'applique.

## Contact

Questions ou demandes : ouvrez un ticket sur
https://github.com/c4software/tailscale-auto-rules/issues
