pluginManagement {
	repositories {
		google()
		// Miroir de Maven Central tenu par Google : meme contenu, et il ne
		// limite pas le debit comme le depot d'origine le fait parfois.
		maven("https://maven-central.storage-download.googleapis.com/maven2/")
		mavenCentral()
		gradlePluginPortal()
	}
}

dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		google()
		maven("https://maven-central.storage-download.googleapis.com/maven2/")
		mavenCentral()
	}
}

rootProject.name = "Kilometres"

// Le coeur, partage par tout le monde ; puis une application par cible.
include(":shared")
include(":androidApp")
include(":wearApp")
include(":desktopApp")
