import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.compose.multiplatform)
	alias(libs.plugins.kotlin.compose)
}

/**
 * Application Windows (Compose Multiplatform, JVM). Meme coeur, meme
 * logique ; l'interface suit Material 3 avec la densite d'un ordinateur.
 *
 *   ./gradlew :desktopApp:run            lancer
 *   ./gradlew :desktopApp:packageMsi     installeur Windows (sur Windows)
 */
kotlin {
	compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
	implementation(project(":shared"))
	implementation(compose.desktop.currentOs)
	implementation(compose.material3)
	implementation(compose.materialIconsExtended)
	implementation(libs.kotlinx.coroutines.swing)
	implementation(libs.kotlinx.serialization.json)
}

compose.desktop {
	application {
		mainClass = "com.hooliapps.kilometres.desktop.MainKt"
		nativeDistributions {
			targetFormats(TargetFormat.Msi, TargetFormat.Exe)
			packageName = "Kilometres"
			packageVersion = "1.0.0"
			vendor = "HooliApps"
			description = "Carnet de kilometrage et estimation des frais reels"
			windows {
				// Icone rendue par tools/branding/render.js (PNG dans l'ICO, 16 a 256 px).
				iconFile.set(project.file("src/main/resources/icon.ico"))
				menuGroup = "HooliApps"
				perUserInstall = true
				// Identifiant stable de l'installeur : ne jamais le changer, sinon
				// Windows installe une seconde copie au lieu de mettre a jour.
				upgradeUuid = "7C1C5B2E-6D3B-4E5C-9A52-2C4B9E3A1F10"
			}
		}
	}
}
