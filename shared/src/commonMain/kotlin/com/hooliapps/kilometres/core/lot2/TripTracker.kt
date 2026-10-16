package com.hooliapps.kilometres.core.lot2

import com.hooliapps.kilometres.core.importer.LatLng

/** Un point recu du capteur de position. Independant de tout SDK. */
data class TrackPoint(val point: LatLng, val epochMillis: Long, val accuracyMeters: Double, val speedMetersPerSecond: Double? = null)

data class TrackSummary(val startEpochMillis: Long, val endEpochMillis: Long, val distanceKm: Double, val points: Int)

/**
 * LOT 2, INACTIF. Accumule la distance d'un trajet en cours et decide de
 * l'arret automatique apres un arret prolonge. Pur et teste.
 *
 *  - un point dont la precision est pire que [maxAccuracyMeters] est ignore ;
 *  - un saut implausible (> [maxSpeedMetersPerSecond]) est ignore ;
 *  - en dessous de [minStepMeters] on ne compte rien ;
 *  - apres [stopAfterMs] sans deplacement, [shouldStop] devient vrai.
 */
class TripTracker(
	private val maxAccuracyMeters: Double = 50.0,
	private val maxSpeedMetersPerSecond: Double = 70.0,
	private val minStepMeters: Double = 15.0,
	private val stopAfterMs: Long = 5 * 60_000L
) {
	private var last: TrackPoint? = null
	private var start: Long? = null
	private var lastMove: Long? = null
	private var meters = 0.0
	private var points = 0

	val distanceKm: Double get() = kotlin.math.round(meters / 100) / 10

	fun shouldStop(now: Long): Boolean = lastMove?.let { now - it >= stopAfterMs } ?: false

	fun add(p: TrackPoint) {
		if (p.accuracyMeters > maxAccuracyMeters) return
		if (start == null) start = p.epochMillis
		if (lastMove == null) lastMove = p.epochMillis
		points++
		val prev = last
		if (prev == null) {
			last = p
			return
		}
		val d = prev.point.distanceTo(p.point)
		val dt = (p.epochMillis - prev.epochMillis) / 1000.0
		if (dt <= 0) return
		if (d / dt > maxSpeedMetersPerSecond) return
		if (d < minStepMeters) return
		meters += d
		lastMove = p.epochMillis
		last = p
	}

	fun finish(end: Long) = TrackSummary(start ?: end, end, distanceKm, points)
}
