import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

/**
 * Le coeur de l'application, partage par Android, Wear OS, iOS, watchOS et
 * Windows : modeles, regles fiscales, quota, droits, import, exports,
 * sauvegarde, politique publicitaire. Pas une ligne d'interface.
 *
 * Tout ce qui est pur se teste ici sur la JVM : ./gradlew :shared:desktopTest
 *
 * Pour iOS, le module se compile en XCFramework « KilometresCore » que
 * l'application Swift importe :
 *     ./gradlew :shared:assembleKilometresCoreXCFramework
 * Les cibles iOS ne se declarent que sur macOS : ailleurs elles n'existent
 * pas, et le reste du projet se construit sans la chaine Apple.
 */
plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlin.serialization)
}

val onMac = org.gradle.internal.os.OperatingSystem.current().isMacOsX

kotlin {
	androidTarget {
		compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
	}

	jvm("desktop") {
		compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
	}

	if (onMac) {
		val xcf = XCFramework("KilometresCore")
		listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
			target.binaries.framework {
				baseName = "KilometresCore"
				isStatic = true
				xcf.add(this)
			}
		}
	}

	sourceSets {
		// Android et le bureau partagent la meme implementation java.io.
		val jvmShared by creating { dependsOn(commonMain.get()) }
		androidMain.get().dependsOn(jvmShared)
		getByName("desktopMain").dependsOn(jvmShared)

		commonMain.dependencies {
			implementation(libs.kotlinx.coroutines.core)
			implementation(libs.kotlinx.serialization.json)
		}
		commonTest.dependencies {
			implementation(kotlin("test"))
			implementation(libs.kotlinx.coroutines.core)
		}
	}
}

android {
	namespace = "com.hooliapps.kilometres.core"
	compileSdk = 36
	defaultConfig { minSdk = 26 }
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}
