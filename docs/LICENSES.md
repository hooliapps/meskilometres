# Licences des composants embarqués

Règle du projet : **aucune licence copyleft** (GPL, LGPL, AGPL, MPL avec
modification, CC BY-SA...) ni licence « non commerciale ». Tout ce qui est
embarqué est sous licence permissive ou sous les conditions du SDK de la
plateforme, qui autorisent explicitement la distribution d'applications
commerciales fermées.

## Code embarqué dans les applications

| Composant | Licence | Usage |
|---|---|---|
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache 2.0 | cœur, toutes cibles |
| AndroidX (core, activity, lifecycle, Compose, Material 3, Glance, Wear Compose) | Apache 2.0 | Android, Wear OS |
| Compose Multiplatform (JetBrains) | Apache 2.0 | Windows |
| Material Icons | Apache 2.0 | icônes des interfaces |
| Google Mobile Ads SDK (nouvelle génération) | Conditions du SDK Google (Android SDK License / Google APIs ToS) | publicité, version gratuite |
| User Messaging Platform (UMP) | Conditions du SDK Google | consentement RGPD |
| Play Age Signals | Conditions du SDK Google | signaux d'âge |
| Play Billing | Conditions du SDK Google | abonnements Android |
| Play Services Wearable | Conditions du SDK Google | liaison téléphone-montre |
| Google Mobile Ads SDK iOS, UMP iOS (Swift Package Manager) | Conditions du SDK Google | iOS |
| StoreKit 2, WidgetKit, WatchConnectivity, SwiftUI, UIKit, PDFKit | SDK Apple | iOS, watchOS |
| Java 17 (JetBrains Runtime embarqué par l'installeur) | GPL 2 **avec Classpath Exception** | Windows : l'exception autorise explicitement l'embarquement dans une application propriétaire ; aucun code du JDK n'est modifié |

Les conditions des SDK Google et Apple autorisent la distribution
commerciale ; elles interdisent la redistribution du SDK lui-même hors de
l'application, ce que nous ne faisons pas.

## Ce que le projet a écrit lui-même, pour ne rien devoir
- Lecture d'historique de positions (formats JSON), sauvegarde et fusion.
- Écriture ZIP et XLSX (format Office Open XML, norme ouverte ECMA-376).
- Rendu PDF par les API de chaque plateforme (`android.graphics.pdf`,
  `UIGraphicsPDFRenderer`, Java2D) : aucune bibliothèque PDF tierce.
- Calendrier (`Day`), analyse ISO 8601, barème kilométrique.

## Outils de développement (non embarqués)
| Outil | Licence |
|---|---|
| Gradle, Android Gradle Plugin | Apache 2.0 |
| playwright-core (rendu des icônes) | Apache 2.0 |
| Chromium (rendu des icônes) | BSD |
| XcodeGen | MIT |
| fastlane | MIT |

## À vérifier à chaque montée de version
`./gradlew :androidApp:dependencies --configuration googleReleaseRuntimeClasspath`
puis contrôler toute nouvelle dépendance transitive. Aucun greffon de
licences n'est embarqué : l'écran « Informations » de l'application cite
les bibliothèques ci-dessus et renvoie à cette page.
