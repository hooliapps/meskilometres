package com.hooliapps.kilometres.core

/**
 * Ce que chaque plateforme fournit au coeur, et rien de plus : l'heure,
 * le jour local, et des fichiers. Tout le reste est du Kotlin commun.
 */
expect object Platform {
	/** Heure murale, en millisecondes depuis 1970 (UTC). */
	fun nowEpochMillis(): Long

	/** Jour civil local pour un instant donne. */
	fun localDayOf(epochMillis: Long): Day

	/** Nom de la plateforme, pour le journal et les rapports. */
	val name: String
}

/**
 * Acces aux fichiers de donnees. Un dossier prive a l'application, des
 * fichiers nommes, une ecriture atomique (fichier temporaire puis
 * renommage) pour qu'une coupure ne laisse jamais un fichier a moitie ecrit.
 */
expect class PlatformFiles(directory: String) {
	val directory: String
	fun exists(name: String): Boolean
	fun readText(name: String): String?
	fun writeTextAtomic(name: String, content: String)
	fun copy(name: String, toName: String)
	fun lastModifiedEpochMillis(name: String): Long?
}
