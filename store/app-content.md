# Fiche « Contenu de l'application » — réponses

## Classification du contenu (questionnaire IARC)

| Question | Réponse |
|---|---|
| Catégorie | Utilitaire / Outils (application de productivité, pas un jeu) |
| Violence, sexualité, langage grossier, substances, jeux d'argent | Non à tout |
| Contenu généré par les utilisateurs | Non |
| Communication entre utilisateurs (chat, partage) | Non |
| Partage de la position de l'utilisateur | **Non** — la permission de localisation ne sert qu'à lire le SSID (voir [`permissions-declarations.md`](./permissions-declarations.md)) |
| Achats numériques | Non |
| Publicités | Non |

Classification attendue : **Tout public / 3+**.

## Public cible et enfants

| Question | Réponse |
|---|---|
| Tranches d'âge visées | 18 ans et plus |
| L'application attire-t-elle les enfants ? | Non — outil réseau destiné aux utilisateurs de Tailscale |
| Conforme à la Families Policy | Sans objet (non destinée aux familles) |

## Publicités

Aucune publicité. Répondre **« Non, mon application ne contient pas de
publicités »**.

## Accès à l'application

Aucune restriction : toutes les fonctionnalités sont accessibles sans compte ni
identifiant. Préciser dans le champ d'instructions destiné à l'examinateur :

> L'application pilote le client Tailscale officiel (`com.tailscale.ipn`), qui
> doit être installé et connecté sur l'appareil de test. Sans lui, l'écran
> d'accueil affiche « Tailscale client not found » et aucune règle ne
> s'applique — c'est le comportement attendu, non une erreur.
>
> Pour observer l'automatisation : installer Tailscale, s'y connecter, puis
> basculer l'appareil du Wi-Fi aux données mobiles. Le tunnel s'active, et
> l'écran Journal en consigne la raison. Déclarer le Wi-Fi courant comme réseau
> de confiance depuis l'écran Réseaux fait couper le tunnel au retour sur ce
> réseau.

## Application gouvernementale, finance, santé

Non à tout.

## Sécurité des données

Voir [`data-safety.md`](./data-safety.md).

## Politique de confidentialité

Publiée sur GitHub Pages par le workflow
[`pages.yml`](../.github/workflows/pages.yml) :

```
https://c4software.github.io/tailscale-auto-rules/privacy-en.html
```

Version française pour la fiche fr-FR :

```
https://c4software.github.io/tailscale-auto-rules/privacy-fr.html
```
