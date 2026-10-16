package com.hooliapps.kilometres.core

/**
 * Drapeaux de fonctionnalites par lot.
 *
 * Lot 1 : saisie manuelle, estimation, exports, import d'historique,
 * sauvegarde, widgets, montre. Toujours actif.
 *
 * Lots 2 et 3 : COMPILES mais INACTIFS. Le code existe, il est teste la ou il
 * est pur, et aucune permission sensible n'est declaree dans les manifestes
 * tant que ces drapeaux sont a false. Voir docs/ACTIVATION_LOTS.md.
 */
object Features {
	// ---- Lot 2 ------------------------------------------------------------

	/** Enregistrement GPS d'un trajet demarre a la main, arret automatique. */
	const val GPS_RECORDING = false

	/** Demarrage d'un trajet a la connexion Bluetooth de la voiture (Android). */
	const val CAR_CONNECTION = false

	// ---- Lot 3 ------------------------------------------------------------

	/** Detection automatique de la conduite par reconnaissance d'activite. */
	const val AUTO_DETECTION = false

	/** Scan des tickets : OCR puis extraction. Aucune image conservee. */
	const val RECEIPT_SCAN = false

	/** Synchronisation multi-appareils par dossier cloud choisi par l'utilisateur. */
	const val MULTI_DEVICE_SYNC = false

	/** Publicite dans la version gratuite : interstitiels et recompenses. */
	const val ADS = true

	val anyLot2: Boolean get() = GPS_RECORDING || CAR_CONNECTION
	val anyLot3: Boolean get() = AUTO_DETECTION || RECEIPT_SCAN || MULTI_DEVICE_SYNC
}
