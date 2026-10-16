# Kilomètres — carnet de kilométrage et frais réels

Application native **Kotlin Multiplatform** : un seul cœur métier Kotlin
(`shared/`), et une interface native par plateforme. Gratuite avec 5 trajets
par mois saisis à la main, la sauvegarde (fichier, cloud du système, export
JSON) étant gratuite pour tous ; **Pro** (4,99 €/mois ou 49,99 €/an) pour tout
le reste, y compris la restauration d'un fichier de sauvegarde. Pas de
compte, pas de serveur : les données restent sur l'appareil.

Le nom « Kilomètres » est provisoire : voir `docs/NOMS.md`. Il est écrit à
UN endroit par plateforme (voir « Renommer » plus bas).

## Arborescence

| Dossier | Contenu |
|---|---|
| `shared/` | Cœur Kotlin partagé (Android, Windows, iOS/watchOS via XCFramework) : modèles, stockage JSON, quota, droits, barème kilométrique, estimation, import d'historique, exports PDF/XLSX/CSV, sauvegarde et fusion, politique publicitaire, lots 2 et 3 inactifs. 41 tests. |
| `androidApp/` | Application Android (Jetpack Compose, Material 3, couleur dynamique) : facturation Play, publicité (SDK nouvelle génération, UMP, signaux d'âge), widget Glance, sauvegarde système, liaison montre. Trois saveurs : `google`, `xiaomi`, `huawei`. |
| `wearApp/` | Application Wear OS (Compose for Wear) : saisie d'un trajet favori en un geste, compteurs du mois. Réservée à Pro. |
| `desktopApp/` | Application Windows (Compose Multiplatform, JVM) : installeur MSI/EXE. |
| `iosApp/` | Application iPhone/iPad (SwiftUI), widget (WidgetKit), application Apple Watch (watchOS + WatchConnectivity). Projet Xcode généré par XcodeGen depuis `project.yml`. |
| `tools/i18n/` | Source UNIQUE des chaînes, 71 langues, et le générateur qui écrit les ressources des quatre cibles. |
| `tools/branding/` | Rendu des icônes et visuels de magasin par Chromium. |
| `stores/` | Fiches de magasin, 71 langues, et le générateur qui écrit `fastlane/metadata/` (Play, App Store) et les textes à coller (Microsoft, AppGallery, GetApps). |
| `docs/` | `ACTIVATION_LOTS.md`, `STORES.md`, `LICENSES.md`, `NOMS.md`, `PUBLICITE.md`. |

## Construire

Prérequis : JDK 17, Android SDK (plateforme 36, build-tools 36.0.0), Node 20+
pour les outils. `local.properties` doit pointer `sdk.dir`.

```sh
./gradlew :shared:desktopTest                        # tests du cœur (JVM)
./gradlew :androidApp:assembleGoogleDebug            # Android, saveur Play
./gradlew :androidApp:bundleGoogleRelease            # AAB signé (keystore.properties requis)
./gradlew :androidApp:assembleXiaomiRelease :androidApp:assembleHuaweiRelease
./gradlew :wearApp:assembleDebug
./gradlew :desktopApp:run                            # Windows/JVM, lancer
./gradlew :desktopApp:packageMsi                     # installeur (sur Windows)
./gradlew :shared:assembleKilometresCoreReleaseXCFramework   # sur macOS, pour Xcode
cd iosApp && xcodegen generate && open Kilometres.xcodeproj  # sur macOS
```

iOS, watchOS et l'installeur Windows ne se compilent que sur macOS et
Windows respectivement : le code est écrit et relu, pas encore compilé ici.

## Chaînes et fiches : une source, tout est généré

```sh
node tools/i18n/generate.js            # écrit strings.xml (Android, Wear), Localizable.strings (iOS), .properties (Windows)
node tools/i18n/generate.js --check    # vérifie que tout est à jour
node tools/i18n/validate.js fr it      # vérifie une langue (clés, paramètres)
node stores/generate.js                # écrit fastlane/metadata/{android,ios} et stores/out/
node stores/validate.js fr it          # vérifie une fiche (limites, jetons, noms interdits)
npm install --prefix tools/branding && node tools/branding/render.js   # icônes et visuels
```

Ne jamais modifier un fichier généré à la main : il porte un en-tête qui le
dit, et le prochain passage du générateur l'écraserait.

## Avant la première publication

1. **Identifiants AdMob** : remplacer les identifiants de démonstration dans
   `androidApp/build.gradle.kts` (release), `androidApp/.../ads/AdUnits.kt`,
   `iosApp/project.yml` (`ADMOB_APP_ID`) et `iosApp/Kilometres/Ads/AdUnits.swift`.
2. **Signature Android** : `keystore.properties` à la racine (voir
   `androidApp/build.gradle.kts`), jamais commité.
3. **Abonnements** : créer `kilometres_pro_monthly` et `kilometres_pro_yearly`
   dans la Play Console et App Store Connect (voir `docs/STORES.md`).
4. **iCloud** : activer la capacité iCloud Documents sur l'identifiant
   d'application dans le portail développeur (conteneur
   `iCloud.com.consultali.kilometres`).
5. **Pages web** : politique de confidentialité et support aux adresses
   écrites dans `stores/generate.js`.
6. **Renommer** si un nom définitif est choisi : `stores/listing.js`
   (`APP_NAME`), `tools/i18n/strings/*.js` (`app_name`),
   `tools/branding/render.js` (image de présentation), `desktopApp/build.gradle.kts`
   (`packageName`), `iosApp/project.yml` (`PRODUCT_NAME`). Les identifiants
   techniques (`com.consultali.kilometres`, `upgradeUuid`) ne changent PAS.

## Principes

- **Aucun concurrent, aucune plateforme nommée** dans l'application ni les
  fiches : vérifié par `stores/validate.js`.
- **Aucune image de ticket** : seules les références (date, montant, devise,
  lieu, litres, kilométrage) sont stockées, même quand le scan (lot 3) sera actif.
- **Estimation indicative, non garantie** : rappelé sur l'écran d'estimation,
  dans les exports et dans les fiches.
- **Personne ne perd ses données** : sauvegarde fichier, sauvegarde cloud du
  système et export JSON gratuits. Seule la restauration d'un fichier est Pro
  (le RGPD impose la portabilité, c'est-à-dire l'export, pas le réimport ; la
  restauration par le système à la réinstallation reste automatique et gratuite).
- **Licences** : uniquement des bibliothèques permises dans un projet
  commercial fermé (`docs/LICENSES.md`).
