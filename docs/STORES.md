# Publier sur les magasins

Cinq magasins, une seule source de textes (`stores/listings/<langue>.js`,
71 langues) et une seule source d'icônes (`tools/branding/render.js`).

```sh
node stores/generate.js        # fastlane/metadata/android, fastlane/metadata/ios, stores/out/*
node tools/branding/render.js  # tools/branding/out/*, icônes iOS/watchOS/Windows
```

Le nom provisoire « Kilomètres » est injecté par `APP_NAME`
(`stores/listing.js`) ; les prix par `stores/prices.js`, par locale. Une
locale absente de `prices.js` affiche le prix en euros : compléter la table
au moment de fixer les paliers dans chaque console.

## Produits d'abonnement (identiques partout)

| Identifiant | Prix | Note |
|---|---|---|
| `kilometres_pro_monthly` | 4,99 €/mois | renouvellement automatique |
| `kilometres_pro_yearly` | 49,99 €/an | « deux mois offerts » dans l'application |

Un seul groupe d'abonnement (« Pro ») : l'utilisateur passe de l'un à
l'autre sans doublon. Gratuit pour tous : 5 trajets par mois, favoris,
véhicules, sauvegarde fichier et cloud, export JSON. Pro : trajets illimités,
estimation, frais, exports PDF/tableur/CSV, import d'historique,
restauration d'une sauvegarde, widget, montre, sans publicité. Pas de serveur : l'application fait confiance à la
boutique (Play Billing 9 / StoreKit 2) et ne retire Pro que quand la
boutique dit explicitement qu'aucun abonnement n'est actif.

## Google Play (saveur `google`)
- AAB : `./gradlew :androidApp:bundleGoogleRelease` avec `keystore.properties`.
- Fiche : `fastlane supply` lit `fastlane/metadata/android/` (87 locales ;
  changelogs nommés par `versionCode`). Icône `tools/branding/out/icon-512.png`,
  image de présentation `feature-1024x500.png`. Captures : à faire sur
  appareil (voir « Captures »).
- Data Safety : données **non** collectées par l'éditeur ; le SDK publicitaire
  collecte l'identifiant publicitaire et des données d'appareil (version
  gratuite) ; aucune localisation tant que le lot 2 est inactif.
- Questionnaire de contenu : outil / finance, publicité oui, achats intégrés
  oui, public 18+ (audience déclarée adulte, classification des annonces « T »).
- Abonnements : créer les deux produits, offre de base mensuelle et annuelle,
  disponibilité dans tous les pays ; le paywall lit les prix localisés.
- Signaux d'âge : la saveur `google` les lit (API Play Age Signals) ; un
  compte signalé jeune n'a ni annonces ni consentement.

## App Store (iOS, widget, watchOS)
- Sur macOS : `./gradlew :shared:assembleKilometresCoreReleaseXCFramework`,
  `cd iosApp && xcodegen generate`, archiver dans Xcode.
- Fiche : `fastlane deliver` lit `fastlane/metadata/ios/` (39 locales).
  Icône `icon-1024.png` déjà dans les catalogues (RGB, sans alpha, sans coins).
- App Privacy : identifiants (identifiant publicitaire) et données d'usage
  liées à la publicité (version gratuite) ; aucune donnée reliée à l'identité.
  ATT : demandé APRÈS le consentement UMP, uniquement si l'utilisateur a
  accepté la publicité personnalisée.
- Abonnements : groupe « Pro », mêmes identifiants ; `StoreKit` configuration
  locale pour tester (`Kilometres/Resources/Products.storekit` à créer dans Xcode).
- Capacités : iCloud Documents (conteneur `iCloud.com.consultali.kilometres`),
  App Groups (`group.com.consultali.kilometres`) pour le widget.
- Classification : 4+ (l'outil n'a pas de contenu), audience déclarée 18+
  via `DeclaredAgeRange` pour la publicité.

## Microsoft Store (Windows)
- `./gradlew :desktopApp:packageMsi` sur Windows, puis Partner Center.
- Texte à coller : `stores/out/microsoft/<langue>.md` ; tuile
  `tools/branding/out/store-300x300.png` ; captures 1366×768 minimum.
- Pas de publicité ni d'abonnement sur Windows pour l'instant : l'application
  Windows est **Pro par construction** (`Entitlement` fixé à Pro dans
  `desktopApp`, voir `Main.kt`), pensée comme complément d'un abonnement
  mobile. Décision à revoir si Windows devient un canal de vente (Microsoft
  Store propose ses abonnements).

## AppGallery (Huawei, saveur `huawei`)
- APK : `./gradlew :androidApp:assembleHuaweiRelease`.
- Sans services Google : pas de Play Billing, pas de SDK publicitaire Google.
  La saveur `huawei` masque la publicité et affiche « achats non disponibles »
  tant que Huawei IAP n'est pas branché. À brancher : HMS IAP Kit (licence
  Huawei, usage commercial autorisé) derrière la même interface `Purchases`.
- Texte à coller : `stores/out/appgallery/<langue>.md` ; icône `icon-512.png`.

## GetApps (Xiaomi, saveur `xiaomi`)
- APK : `./gradlew :androidApp:assembleXiaomiRelease`. Les appareils Xiaomi
  hors Chine ont les services Google : Play Billing et AdMob fonctionnent ;
  en Chine, même situation que Huawei (pas de services).
- Texte à coller : `stores/out/getapps/<langue>.md`.

## Captures d'écran
Non générées ici : elles se prennent sur appareil ou simulateur avec des
données de démonstration (`Graph.state` accepte un jeu de démonstration via
`AppData` sérialisé). Ordre conseillé : trajets du mois, saisie en un geste
(favoris), estimation avec comparaison, export PDF, widget, montre.

## Vérifications avant envoi
- `node tools/i18n/generate.js --check` et `node stores/generate.js --check` : rien d'obsolète.
- Aucun nom de plateforme ou concurrent : `stores/validate.js` échoue sinon.
- Identifiants AdMob réels, keystore, URLs de confidentialité en ligne.
- `./gradlew :shared:desktopTest :androidApp:lintGoogleRelease`.
