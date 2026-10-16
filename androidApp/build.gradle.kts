import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.compose)
}

android {
	namespace = "com.hooliapps.kilometres"
	compileSdk = 36

	defaultConfig {
		applicationId = "com.consultali.kilometres"
		minSdk = 26
		targetSdk = 36
		versionCode = 10000
		versionName = "1.0.0"
	}

	androidResources {
		// L'application embarque ses propres traductions : sans ce filtre,
		// AAPT tirerait celles de toutes les bibliotheques. Les codes sont
		// ceux des dossiers de ressources : iw, in, nb, zh-rTW.
		localeFilters += listOf(
			"en", "fr", "it", "de", "es", "pt", "ru", "zh", "ja", "hi", "ar", "vi", "hu",
			"ca", "cs", "da", "el", "fi", "iw", "hr", "in", "ko", "ms", "nb", "nl", "pl", "ro", "sk", "sv", "th", "tr", "uk", "zh-rTW",
			"af", "am", "az", "be", "bg", "bn", "et", "eu", "fa", "fil", "gl", "gu", "hy", "is", "ka", "kk", "km", "kn",
			"ky", "lo", "lt", "lv", "mk", "ml", "mn", "mr", "my", "ne", "pa", "rm", "si", "sl", "sr", "sw", "ta", "te", "ur", "zu"
		)
	}

	/**
	 * Une saveur par boutique. Le code est le meme ; seule change la boutique
	 * qui distribue l'application et, pour celle sans services de la
	 * plateforme, le kit d'achats et la publicite (voir docs/STORES.md).
	 *
	 *   ./gradlew :androidApp:bundleGoogleRelease
	 *   ./gradlew :androidApp:assembleXiaomiRelease
	 *   ./gradlew :androidApp:assembleHuaweiRelease
	 */
	flavorDimensions += "store"
	productFlavors {
		create("google") { dimension = "store"; buildConfigField("String", "STORE", "\"google\"") }
		create("xiaomi") { dimension = "store"; buildConfigField("String", "STORE", "\"xiaomi\"") }
		create("huawei") { dimension = "store"; buildConfigField("String", "STORE", "\"huawei\"") }
	}

	/**
	 * Signature de production : keystore.properties hors du depot, comme pour
	 * QuanGame. Le fichier absent n'est pas une erreur.
	 */
	val signature = file("../keystore.properties")
	signingConfigs {
		val props = Properties()
		if (signature.exists()) signature.inputStream().use { props.load(it) }
		val store = props.getProperty("storeFile")
		if (store != null) {
			create("release") {
				storeFile = file(store)
				storePassword = props.getProperty("storePassword")
				keyAlias = props.getProperty("keyAlias")
				keyPassword = props.getProperty("keyPassword")
			}
		}
	}

	buildTypes {
		release {
			signingConfig = signingConfigs.findByName("release")
			// A REMPLACER par l'identifiant d'application AdMob cree pour
			// cette application. Une valeur empruntee repond « No fill » a
			// toutes les requetes sans autre symptome.
			val admobAppId = "ca-app-pub-3940256099942544~3347511713"
			manifestPlaceholders["admobAppId"] = admobAppId
			buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
		debug {
			isMinifyEnabled = false
			applicationIdSuffix = ".debug"
			// Application de demonstration publique de Google : annonces
			// factices, aucune statistique polluee.
			val admobAppId = "ca-app-pub-3940256099942544~3347511713"
			manifestPlaceholders["admobAppId"] = admobAppId
			buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
		}
	}

	buildFeatures {
		compose = true
		buildConfig = true
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}

kotlin {
	compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
	implementation(project(":shared"))

	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.lifecycle.viewmodel.compose)

	implementation(platform(libs.compose.bom))
	implementation(libs.compose.ui)
	implementation(libs.compose.ui.graphics)
	implementation(libs.compose.ui.tooling.preview)
	implementation(libs.compose.material3)
	implementation(libs.compose.material.icons)

	implementation(libs.glance.appwidget)
	implementation(libs.glance.material3)

	implementation(libs.billing.ktx)
	implementation(libs.ads.mobile.sdk)
	implementation(libs.play.ump)
	implementation(libs.play.age.signals)
	implementation(libs.play.services.wearable)

	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.kotlinx.coroutines.play.services)
	implementation(libs.kotlinx.serialization.json)

	testImplementation(libs.junit)
}
