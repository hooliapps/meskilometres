# Activer les lots 2 et 3

Le code des lots 2 et 3 est écrit, compilé et testé là où il est pur, mais
**inactif** : chaque fonction est derrière un drapeau de
`shared/src/commonMain/kotlin/com/hooliapps/kilometres/core/Features.kt`, tous
à `false`. Tant qu'ils le sont, aucune permission sensible n'est déclarée,
aucun service ne tourne, et les lignes Pro correspondantes (« Tracking GPS
automatique », « Scan intelligent ») ne s'affichent pas.

| Drapeau | Lot | Ce qu'il active | Code partagé déjà écrit |
|---|---|---|---|
| `GPS_RECORDING` | 2 | Enregistrement d'un trajet démarré à la main, arrêt automatique après 5 min sans mouvement | `lot2/TripTracker.kt` (filtrage précision, sauts, distance, arrêt) |
| `CAR_CONNECTION` | 2 | Démarrage d'un trajet à la connexion Bluetooth de la voiture | utilise `TripTracker` |
| `AUTO_DETECTION` | 3 | Détection de la conduite par reconnaissance d'activité | `lot3/DriveEventSmoother.kt` (durée minimale, fusion des arrêts brefs) |
| `RECEIPT_SCAN` | 3 | Scan des tickets péage/parking/carburant, texte seulement | `lot3/ReceiptParser.kt` (type, date, montant, devise, lieu, litres, km) |
| `MULTI_DEVICE_SYNC` | 3 | Synchronisation par un dossier cloud choisi par l'utilisateur | `lot3/SyncEngine.kt` (fusion `BackupCodec.merge`) |

## Marche à suivre, par fonction

### GPS_RECORDING (lot 2)
1. `Features.GPS_RECORDING = true`.
2. Android : ajouter dans `androidApp/src/main/AndroidManifest.xml`
   `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`,
   et un service de premier plan de type `location` qui alimente
   `TripTracker.add(TrackPoint)` depuis `FusedLocationProviderClient`
   (`play-services-location`, licence Android SDK). À l'arrêt
   (`shouldStop`), créer le trajet via `AppState.addTrip` avec
   `TripSource.GPS`.
3. iOS : `NSLocationWhenInUseUsageDescription` dans `Info.plist`, mode
   d'arrière-plan `location`, `CLLocationManager` → `TripTracker`.
4. Fiches : déclarer la localisation dans Data Safety (Play) et App Privacy
   (App Store) ; justifier le service de premier plan dans la Play Console.
5. Écrans : le bouton « Démarrer un trajet » existe dans `TripsScreen` /
   `TripsView` derrière `Features.GPS_RECORDING`.

### CAR_CONNECTION (lot 2, Android)
1. `Features.CAR_CONNECTION = true` (implique GPS_RECORDING).
2. Permission `BLUETOOTH_CONNECT`, récepteur `ACL_CONNECTED` /
   `ACL_DISCONNECTED` filtré sur l'adresse de l'appareil choisi dans les
   réglages (`AppSettings.carBluetoothAddress`, déjà dans le modèle).
3. iOS ne permet pas de réagir à une connexion Bluetooth en arrière-plan sans
   accessoire dédié : la ligne Pro ne mentionne le Bluetooth que sur Android.

### AUTO_DETECTION (lot 3)
1. `Features.AUTO_DETECTION = true`.
2. Android : `ACTIVITY_RECOGNITION`, `ActivityTransitionRequest`
   (`IN_VEHICLE` entrée/sortie) → `DriveEventSmoother.feed`, qui ne rend une
   conduite qu'après 2 min et fusionne les arrêts < 3 min ; puis GPS comme
   ci-dessus.
3. iOS : `CMMotionActivityManager` (`automotive`) avec
   `NSMotionUsageDescription`.

### RECEIPT_SCAN (lot 3)
1. `Features.RECEIPT_SCAN = true`.
2. Reconnaissance SUR L'APPAREIL, texte seulement : Android ML Kit Text
   Recognition v2 (licence Android SDK, pas de GPL), iOS `VisionKit`
   (`DataScannerViewController`) ou `Vision` `VNRecognizeTextRequest`.
3. Le texte va dans `ReceiptParser.parse(text)` → `ReceiptReading` →
   `AppState.addExpense`. **L'image n'est jamais écrite** : ne pas ajouter de
   `CameraX ImageCapture` vers un fichier, utiliser l'analyse en mémoire.
4. Permission caméra (`CAMERA`, `NSCameraUsageDescription`).

### MULTI_DEVICE_SYNC (lot 3)
1. `Features.MULTI_DEVICE_SYNC = true`.
2. Android : `ACTION_OPEN_DOCUMENT_TREE` → `DocumentFile` qui implémente
   `SyncFolder` ; iOS : `UIDocumentPickerViewController` sur un dossier
   iCloud Drive avec accès sécurisé (bookmark).
3. Appeler `SyncEngine.sync(state.data)` à l'ouverture, à chaque
   modification (avec le même délai que `SystemBackup`) et au retour au
   premier plan. La fusion est idempotente et testée.

## Après activation
- Mettre à jour `pro_feature_gps` / `pro_feature_scan` si le texte change
  (les lignes existent déjà en 71 langues).
- Ajouter la fonction dans `stores/listings/*.js` (section « Ce qu'ajoute
  Pro ») et régénérer.
- Relire `docs/PUBLICITE.md` : une récompense « 24 h de GPS » est possible.
