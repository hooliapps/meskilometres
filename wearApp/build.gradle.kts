plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.compose)
}

/**
 * Application Wear OS (Pro) : les kilometres du jour et du mois, et les
 * favoris a saisir d'un geste. Elle ne garde aucune donnee propre : le
 * telephone publie l'etat, la montre renvoie une saisie. Meme identifiant
 * d'application que le telephone, c'est ce que Wear OS exige.
 */
android {
	namespace = "com.hooliapps.kilometres.wear"
	compileSdk = 36

	defaultConfig {
		applicationId = "com.consultali.kilometres"
		minSdk = 30
		targetSdk = 36
		versionCode = 10000
		versionName = "1.0.0"
	}

	androidResources {
		localeFilters += listOf(
			"en", "fr", "it", "de", "es", "pt", "ru", "zh", "ja", "hi", "ar", "vi", "hu",
			"ca", "cs", "da", "el", "fi", "iw", "hr", "in", "ko", "ms", "nb", "nl", "pl", "ro", "sk", "sv", "th", "tr", "uk", "zh-rTW",
			"af", "am", "az", "be", "bg", "bn", "et", "eu", "fa", "fil", "gl", "gu", "hy", "is", "ka", "kk", "km", "kn",
			"ky", "lo", "lt", "lv", "mk", "ml", "mn", "mr", "my", "ne", "pa", "rm", "si", "sl", "sr", "sw", "ta", "te", "ur", "zu"
		)
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
		}
		debug { applicationIdSuffix = ".debug" }
	}

	buildFeatures { compose = true }

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}

kotlin {
	compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
	implementation(libs.androidx.activity.compose)
	implementation(platform(libs.compose.bom))
	implementation(libs.compose.ui)
	implementation(libs.wear.compose.material3)
	implementation(libs.wear.compose.foundation)
	implementation(libs.play.services.wearable)
	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.kotlinx.coroutines.play.services)
}
