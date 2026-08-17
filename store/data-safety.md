# Formulaire « Sécurité des données » — réponses

À reporter dans **Play Console → Contenu de l'application → Sécurité des
données**. Chaque réponse est justifiable par le code : aucune dépendance
réseau, aucun SDK d'analyse, aucun identifiant publicitaire.

## Collecte et partage

| Question | Réponse |
|---|---|
| Votre application collecte-t-elle ou partage-t-elle des données utilisateur ? | **Non** |
| Toutes les données collectées sont-elles chiffrées en transit ? | Sans objet — aucune donnée n'est transmise |
| Proposez-vous un moyen de supprimer les données ? | Sans objet — désinstaller l'application supprime tout |

Si le formulaire refuse « Non » et exige un détail par catégorie, aucune case
n'est à cocher : les données listées ci-dessous ne **quittent jamais**
l'appareil, ce que Play qualifie de traitement local et non de collecte.

## Données conservées localement (non déclarables comme collecte)

| Donnée | Nature | Support |
|---|---|---|
| SSID des réseaux déclarés ou appris | Préférence utilisateur | Room, stockage privé |
| Journal des 500 derniers changements d'état | Historique local | Room, stockage privé |
| Réglages de l'application | Préférences | DataStore, stockage privé |

## Points de vigilance à l'examen

- **Localisation.** L'application déclare `ACCESS_FINE_LOCATION` et
  `ACCESS_COARSE_LOCATION` **sans collecter de position** : Android n'expose le
  SSID courant qu'aux applications titulaires de cette permission. Cette
  justification est reprise mot pour mot dans
  [`permissions-declarations.md`](./permissions-declarations.md).
- **Aucune bibliothèque tierce d'analyse** n'est intégrée : pas de Firebase,
  pas de Crashlytics, pas d'identifiant publicitaire.
- **Aucun compte** n'est créé ni requis par l'application elle-même ; le compte
  Tailscale vit dans le client officiel, hors de notre périmètre.
