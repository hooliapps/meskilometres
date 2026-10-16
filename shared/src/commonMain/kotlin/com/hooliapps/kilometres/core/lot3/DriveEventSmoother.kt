package com.hooliapps.kilometres.core.lot3

enum class DriveEventKind { STARTED, ENDED }

data class DriveEvent(val kind: DriveEventKind, val epochMillis: Long, val confidence: Double? = null)

/**
 * LOT 3, INACTIF. Filtre pur : ignore les detections trop courtes et
 * fusionne deux conduites separees par un arret bref. La detection
 * elle-meme (reconnaissance d'activite du systeme) est a brancher par
 * plateforme a l'activation.
 */
class DriveEventSmoother(private val minDurationMs: Long = 2 * 60_000L, private val mergeGapMs: Long = 3 * 60_000L) {
	private var start: Long? = null
	private var lastEnd: Long? = null

	/** Rend (debut, fin) quand une conduite est confirmee. */
	fun feed(e: DriveEvent): Pair<Long, Long>? = when (e.kind) {
		DriveEventKind.STARTED -> {
			val le = lastEnd
			if (le != null && start != null && e.epochMillis - le <= mergeGapMs) {
				lastEnd = null
			} else {
				start = e.epochMillis
				lastEnd = null
			}
			null
		}
		DriveEventKind.ENDED -> {
			val s = start
			if (s == null) {
				null
			} else {
				lastEnd = e.epochMillis
				if (e.epochMillis - s < minDurationMs) {
					start = null
					lastEnd = null
					null
				} else {
					s to e.epochMillis
				}
			}
		}
	}
}
