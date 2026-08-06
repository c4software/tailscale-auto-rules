<div align="center">

# Tailscale Auto Rules

**Active ou désactive votre tunnel Tailscale automatiquement, selon vos règles.**

[![CI](https://github.com/c4software/tailscale-auto-rules/actions/workflows/ci.yml/badge.svg)](https://github.com/c4software/tailscale-auto-rules/actions/workflows/ci.yml)
[![Licence: MIT](https://img.shields.io/badge/licence-MIT-blue.svg)](./LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-26-brightgreen.svg)](https://developer.android.com/tools/releases/platforms)

</div>

> ⚠️ **Projet en construction.** Le pilotage du client Tailscale est établi et
> testé ; le moteur de règles et l'interface sont en cours d'implémentation.
> Voir [TASKS.md](./TASKS.md) pour l'avancement réel.

---

## Présentation

Sur mobile, on veut souvent que le VPN suive le contexte : **actif** sur le
réseau de l'aéroport, **inactif** à la maison où le tunnel n'apporte rien et
coûte de la batterie. Le faire à la main, c'est l'oublier une fois sur deux.

**Tailscale Auto Rules** s'en charge. L'application observe le réseau et
demande au client Tailscale officiel de s'activer ou de se désactiver selon des
règles que vous définissez.

Elle **ne remplace pas Tailscale** et n'implémente aucune pile VPN : le client
officiel reste indispensable.

### Règles disponibles

| Priorité | Situation | Action |
|---:|---|---|
| 1 | Mode avion actif | Désactive le tunnel |
| 2 | Wi-Fi dans votre liste de réseaux de confiance | Désactive le tunnel |
| 3 | Tout autre Wi-Fi | Active le tunnel |
| 4 | Réseau mobile (4G / 5G) | Active le tunnel |
| — | Aucun réseau | Ne fait rien |

Chaque décision est justifiée par une règle nommée et consignée dans un journal
des 500 derniers événements.

Le moteur suit un pattern *Strategy* : **ajouter une règle n'implique aucune
modification du moteur existant.** Horaires, BSSID, niveau de batterie,
Bluetooth, Android Auto… sont des ajouts, pas des refontes.

---

## Captures d'écran

<!-- À remplacer par de vraies captures à l'étape 15 de TASKS.md -->

| Accueil | Blacklist Wi-Fi | Journal | Paramètres |
|:---:|:---:|:---:|:---:|
| _(à venir)_ | _(à venir)_ | _(à venir)_ | _(à venir)_ |

---

## Architecture en bref

Trois couches, une seule direction de dépendance : tout pointe vers le domaine,
le domaine ne pointe vers rien.

```
presentation ──▶ domain ◀── data
```

Le domaine — modèle, règles, moteur — est un module **Kotlin/JVM pur**, sans
SDK Android sur son classpath. Il s'exécute intégralement en test JVM, sans
émulateur.

**Kotlin · Jetpack Compose · Material 3 · MVVM · Hilt · Room · DataStore ·
StateFlow · Coroutines**

Le détail — couches, moteur de règles, cycle de synchronisation, décisions
d'architecture — est dans **[ARCHITECTURE.md](./ARCHITECTURE.md)**.

---

## Compilation

**Prérequis :** JDK 21 et le SDK Android (plateforme 37). Le wrapper Gradle et
la toolchain sont fournis, rien d'autre à installer.

```bash
git clone https://github.com/c4software/tailscale-auto-rules.git
cd tailscale-auto-rules

# Si vous utilisez le JDK embarqué d'Android Studio :
export JAVA_HOME=$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew assembleDebug
```

L'APK est produit dans `app/build/outputs/apk/debug/`.

### Vérification complète

```bash
./gradlew ktlintCheck detekt lint test assembleDebug
```

C'est exactement ce que vérifie la CI sur chaque Pull Request.

---

## Publication sur le Play Store

> Procédure détaillée à l'étape 15 de [TASKS.md](./TASKS.md).

Trame :

1. Créer un keystore de release, **hors du dépôt**.
2. Renseigner `keystore.properties` (ignoré par Git) et le référencer dans la
   configuration de signature.
3. `./gradlew bundleRelease` → App Bundle dans `app/build/outputs/bundle/`.
4. Compléter la fiche Play Console : formulaire de sécurité des données,
   justification de chaque permission, politique de confidentialité.
5. Publier en test interne avant toute diffusion en production.

Les permissions restent minimales par conception : chacune n'est demandée que
si la fonctionnalité qui la justifie est activée par l'utilisateur (voir
[SPECS.md](./SPECS.md) §8).

---

## Contribution

Les contributions sont bienvenues. Lisez **[CONTRIBUTING.md](./CONTRIBUTING.md)**
avant d'ouvrir une Pull Request, et **[AGENTS.md](./AGENTS.md)** pour les
conventions de code et la définition de « terminé ».

---

## Documentation du projet

| Fichier | Contenu |
|---|---|
| [SPECS.md](./SPECS.md) | Spécification fonctionnelle — le **quoi** |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Architecture technique — le **comment** |
| [TASKS.md](./TASKS.md) | Feuille de route — l'**ordre** et l'avancement |
| [AGENTS.md](./AGENTS.md) | Règles de développement et conventions |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | Comment contribuer |
| [PROMPT.md](./PROMPT.md) | Intention initiale du projet (figée) |

---

## Licence

[MIT](./LICENSE).

Ce projet n'est pas affilié à Tailscale Inc. « Tailscale » est une marque de
Tailscale Inc., citée ici à des fins d'interopérabilité.
