# Dossier de soumission — Google Play

Tout ce qu'attend la Play Console pour la première publication, prêt à coller
ou à téléverser. Les textes sont livrés en **anglais** (langue par défaut de
l'application) et en **français**.

Identifiant de l'application : `fr.vbrosseau.tailscaleautorules`
Version des captures : **0.4.1** (`app-debug`, construite depuis l'étiquette `v0.4.1`)

---

## Inventaire

| Élément | Fichier | Contrainte Play | État |
|---|---|---|---|
| Titre | [`listing/*/title.txt`](./listing) | 30 caractères max | 19 caractères |
| Description courte | [`listing/*/short-description.txt`](./listing) | 80 caractères max | 72 (en) / 73 (fr) |
| Description complète | [`listing/*/full-description.txt`](./listing) | 4 000 caractères max | ≈ 2 500 |
| Notes de version | [`release-notes/`](./release-notes) | 500 caractères max | ≈ 400 |
| Icône | [`graphics/icon-512.png`](./graphics) | 512 × 512, PNG 32 bits | conforme |
| Image mise en avant | [`graphics/feature-graphic-*.png`](./graphics) | 1 024 × 500, PNG 32 bits | conforme |
| Captures téléphone | [`screenshots/en-US/`](./screenshots/en-US), [`screenshots/fr-FR/`](./screenshots/fr-FR) | 2 à 8, ratio 9:16, 1 080 × 2 400 | 6 par langue |
| Sécurité des données | [`data-safety.md`](./data-safety.md) | formulaire | rédigé |
| Déclarations de permissions | [`permissions-declarations.md`](./permissions-declarations.md) | examen manuel | rédigé |
| Contenu de l'application | [`app-content.md`](./app-content.md) | questionnaire | rédigé |
| Politique de confidentialité | [`privacy-policy-en.md`](./privacy-policy-en.md), [`privacy-policy-fr.md`](./privacy-policy-fr.md) | URL publique | publiée par la CI sur GitHub Pages |

### Les captures, une par une

| Ordre | en-US | fr-FR | Ce qu'elle montre |
|---|---|---|---|
| 1 | `01-welcome.png` | `01-bienvenue.png` | Première page du parcours d'accueil : ce que fait l'application, et ce qu'elle ne remplace pas |
| 2 | `02-home.png` | `02-accueil.png` | Accueil en données mobiles : tunnel actif, règle « Réseau mobile » |
| 3 | `03-networks.png` | `03-reseaux.png` | Préférences de réseau : deux réseaux de confiance, un réseau à tunnel forcé |
| 4 | `04-history.png` | `04-journal.png` | Journal : chaque changement nommé par sa règle |
| 5 | `05-settings.png` | `05-parametres.png` | Paramètres, et l'explication de la notification permanente |
| 6 | `06-privacy.png` | `06-confidentialite.png` | Page de localisation : pourquoi le SSID, et ce qui n'est pas lu |

---

## Avant l'envoi

1. **La politique de confidentialité se publie toute seule sur GitHub Pages.**
   Le workflow [`pages.yml`](../.github/workflows/pages.yml) la régénère depuis
   les fichiers du dépôt à chaque modification poussée sur `main` : le texte
   déclaré à Google ne peut donc pas diverger de celui qui est versionné. URL à
   renseigner dans la fiche :

   ```
   https://c4software.github.io/tailscale-auto-rules/privacy-en.html
   ```

   Version française, pour la traduction fr-FR de la fiche :

   ```
   https://c4software.github.io/tailscale-auto-rules/privacy-fr.html
   ```

   Pages est déjà activé sur le dépôt, en mode « construction par workflow » :
   rien à cocher dans l'interface, rien à refaire. Vérifier tout de même que
   les deux URL répondent avant de les déclarer — une politique injoignable
   fait rejeter la fiche.
2. **Créer le keystore de production** hors du dépôt, et déposer les quatre
   secrets `RELEASE_KEYSTORE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
   `RELEASE_KEY_PASSWORD` dans le dépôt GitHub — voir le README racine,
   § *Signature*.
3. **Produire l'App Bundle signé** : poser une étiquette `v*`, le workflow
   `release.yml` construit, signe et vérifie. Récupérer l'artefact dans la
   publication GitHub.
4. **Vérifier la version** : le `versionName` de l'artefact doit être celui de
   l'étiquette, sans suffixe. Un nom comme `1.0.0-3-gabc1234` signale une
   construction qui ne descend pas directement d'une étiquette : elle n'est pas
   publiable.

## Ordre de saisie dans la Play Console

1. **Créer l'application** — nom `Tailscale Auto Rules`, langue par défaut
   *anglais (États-Unis)*, catégorie *Outils*, application gratuite.
2. **Fiche Play Store principale** (en-US) — titre, descriptions, icône, image
   mise en avant, captures. Puis ajouter la traduction **fr-FR** et y coller
   les fichiers correspondants.
3. **Contenu de l'application** — dans l'ordre : politique de confidentialité
   (URL), accès à l'application, publicités, classification du contenu, public
   cible, sécurité des données. Réponses dans
   [`app-content.md`](./app-content.md) et [`data-safety.md`](./data-safety.md).
4. **Déclarations d'examen manuel** — types de service de premier plan
   `specialUse` et `location`. Textes dans
   [`permissions-declarations.md`](./permissions-declarations.md). **C'est
   l'étape qui allonge le délai d'examen** : la remplir avec soin, et attendre
   sa validation avant de viser la production.
5. **Version en test interne** d'abord : y téléverser l'App Bundle et les notes
   de version. Vérifier l'installation depuis le Play Store sur un appareil
   réel, client Tailscale installé.
6. **Production** seulement une fois le test interne concluant.

---

## Comment les captures ont été produites

Sur émulateur Pixel 6 (API 36, 1 080 × 2 400), application `app-debug`
construite depuis l'étiquette `v0.4.1`, barre d'état figée par le mode démo de
SystemUI (`10:30`, batterie pleine) pour que les captures ne portent ni heure
réelle ni notification étrangère.

Les états visibles sont **réels** : les préférences de réseau ont été saisies
dans l'application, et les entrées du journal proviennent de bascules Wi-Fi ↔
données mobiles effectuées sur l'émulateur, chacune ayant réellement déclenché
une commande vers le client. Aucune image n'est retouchée ni composée.

Un émulateur n'exécute pas le client Tailscale officiel (il exige un compte et
un tunnel réel). Pour que l'application se trouve dans les conditions d'un
téléphone équipé, une **doublure locale** du client — même nom de paquet, même
receveur `IPNReceiver`, un `VpnService` inerte qui monte un tunnel sans trafic
— a été installée sur l'émulateur le temps des captures. Elle vit hors du
dépôt, n'est liée à aucune construction et n'entre pas dans l'artefact publié :
elle ne sert qu'à faire exister un tunnel VPN que l'application puisse
constater. Ce qui est montré à l'écran reste, à chaque pixel, ce que
l'application rend.

**À refaire après toute évolution de l'interface** : ces captures datent de la
version 0.4.1. Une fiche Play qui montre un écran disparu vaut moins qu'une
fiche sans capture.

---

## Ce qui manque encore, et pourquoi

- **Une capture de la notification permanente** — la seule façon de la montrer
  serait de dérouler le volet système, qui expose alors les notifications du
  reste de l'appareil. Elle est décrite dans la description longue plutôt que
  montrée de travers.
- **Les captures tablette** — facultatives tant que l'application n'est pas
  distribuée en avant sur grand écran ; l'interface s'y adapte, mais aucune
  vérification n'a été faite sur ce format.
