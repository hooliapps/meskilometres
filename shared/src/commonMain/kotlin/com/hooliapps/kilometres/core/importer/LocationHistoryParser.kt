package com.hooliapps.kilometres.core.importer

import com.hooliapps.kilometres.core.Iso8601
import com.hooliapps.kilometres.core.Platform
import com.hooliapps.kilometres.core.Purpose
import com.hooliapps.kilometres.core.Trip
import com.hooliapps.kilometres.core.TripSource
import com.hooliapps.kilometres.core.km1
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double) {
	/** Distance a vol d'oiseau, en metres. */
	fun distanceTo(o: LatLng): Double {
		val r = 6_371_000.0
		val dLat = rad(o.lat - lat)
		val dLng = rad(o.lng - lng)
		val a = sin(dLat / 2) * sin(dLat / 2) + cos(rad(lat)) * cos(rad(o.lat)) * sin(dLng / 2) * sin(dLng / 2)
		return 2 * r * atan2(sqrt(a), sqrt(1 - a))
	}

	private fun rad(d: Double) = d * kotlin.math.PI / 180
}

/** Un deplacement lu dans un fichier d'historique de positions. */
data class ImportedTrip(
	val key: String,
	val startEpochMillis: Long,
	val endEpochMillis: Long,
	val distanceKm: Double,
	val startPoint: LatLng? = null,
	val endPoint: LatLng? = null,
	/** « home », « work » ou null, quand le fichier le dit. */
	val startPlace: String? = null,
	val endPlace: String? = null
) {
	val isCommute: Boolean
		get() = (startPlace == "home" && endPlace == "work") || (startPlace == "work" && endPlace == "home")
}

enum class HistoryFormat { ON_DEVICE_OBJECT, ON_DEVICE_ARRAY, TAKEOUT_SEMANTIC, UNKNOWN }

data class ImportResult(
	val format: HistoryFormat,
	val trips: List<ImportedTrip>,
	val skippedNonVehicle: Int,
	val skippedUnreadable: Int
) {
	companion object {
		val EMPTY = ImportResult(HistoryFormat.UNKNOWN, emptyList(), 0, 0)
	}
}

/**
 * Lit un export d'historique de positions et en extrait les deplacements en
 * vehicule. Trois formats sont reconnus, sans dependre de l'ordre des cles :
 *
 *  - un objet avec `semanticSegments` (export depuis un telephone) ;
 *  - un tableau de segments, chacun avec `activity` ou `visit` (autre export
 *    depuis un telephone, coordonnees prefixees « geo: ») ;
 *  - un objet avec `timelineObjects` et `activitySegment` / `placeVisit`
 *    (anciens exports par archive, coordonnees en E7).
 *
 * Le fichier n'est jamais conserve ; seules les donnees structurees sortent.
 */
object LocationHistoryParser {

	/** Rayon, en metres, en deca duquel un depart ou une arrivee est « chez soi » ou « au travail ». */
	const val PLACE_RADIUS_METERS = 250.0

	private val json = Json { ignoreUnknownKeys = true; isLenient = true }

	fun parse(text: String): ImportResult {
		val root: JsonElement = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return ImportResult.EMPTY

		val format: HistoryFormat
		val segments: List<JsonObject>
		when {
			root is JsonObject && root["semanticSegments"] is JsonArray -> {
				format = HistoryFormat.ON_DEVICE_OBJECT
				segments = root["semanticSegments"]!!.jsonArray.filterIsInstance<JsonObject>()
			}
			root is JsonArray -> {
				format = HistoryFormat.ON_DEVICE_ARRAY
				segments = root.filterIsInstance<JsonObject>()
			}
			root is JsonObject && root["timelineObjects"] is JsonArray -> {
				format = HistoryFormat.TAKEOUT_SEMANTIC
				segments = root["timelineObjects"]!!.jsonArray.filterIsInstance<JsonObject>().mapNotNull(::flattenTakeout)
			}
			else -> return ImportResult.EMPTY
		}

		data class Place(val point: LatLng, val kind: String)
		val places = mutableListOf<Place>()
		val activities = mutableListOf<JsonObject>()
		var unreadable = 0
		for (s in segments) {
			val visit = s["visit"] as? JsonObject
			val activity = s["activity"] as? JsonObject
			when {
				visit != null -> {
					val top = (visit["topCandidate"] as? JsonObject) ?: visit
					val kind = placeKind(top["semanticType"])
					val point = point(top["placeLocation"])
					if (kind != null && point != null) places += Place(point, kind)
				}
				activity != null -> activities += s
				s["timelinePath"] == null -> unreadable++
			}
		}

		val trips = mutableListOf<ImportedTrip>()
		var nonVehicle = 0
		for (s in activities) {
			val a = s["activity"]!!.jsonObject
			val top = (a["topCandidate"] as? JsonObject) ?: a
			val type = str(top["type"]) ?: str(a["activityType"]) ?: ""
			if (!isVehicle(type)) {
				nonVehicle++
				continue
			}
			val start = time(s["startTime"])
			val end = time(s["endTime"])
			val meters = num(a["distanceMeters"])
			if (start == null || end == null || meters == null || meters <= 0) {
				unreadable++
				continue
			}
			val sp = point(a["start"])
			val ep = point(a["end"])
			trips += ImportedTrip(
				key = "lh:${Iso8601.formatUtc(start)}",
				startEpochMillis = start,
				endEpochMillis = end,
				distanceKm = km1(meters / 1000),
				startPoint = sp,
				endPoint = ep,
				startPlace = nearest(sp, places.map { it.point to it.kind }),
				endPlace = nearest(ep, places.map { it.point to it.kind })
			)
		}
		return ImportResult(format, trips.sortedBy { it.startEpochMillis }, nonVehicle, unreadable)
	}

	/** Convertit les deplacements lus en trajets de l'application. */
	fun toTrips(
		imported: Iterable<ImportedTrip>,
		newId: () -> String,
		defaultPurpose: Purpose,
		vehicleId: String?,
		homeLabel: String = "",
		workLabel: String = ""
	): List<Trip> = imported.map { t ->
		Trip(
			id = newId(),
			date = Platform.localDayOf(t.startEpochMillis),
			distanceKm = t.distanceKm,
			from = label(t.startPlace, homeLabel, workLabel),
			to = label(t.endPlace, homeLabel, workLabel),
			roundTrip = false,
			purpose = if (t.isCommute) Purpose.COMMUTE else defaultPurpose,
			vehicleId = vehicleId,
			source = TripSource.IMPORTED,
			importKey = t.key
		)
	}

	private fun label(place: String?, home: String, work: String) = when (place) {
		"home" -> home
		"work" -> work
		else -> ""
	}

	private fun flattenTakeout(o: JsonObject): JsonObject? {
		(o["activitySegment"] as? JsonObject)?.let { a ->
			val duration = (a["duration"] as? JsonObject) ?: JsonObject(emptyMap())
			return JsonObject(mapOf(
				"startTime" to (duration["startTimestamp"] ?: duration["startTimestampMs"] ?: JsonNull),
				"endTime" to (duration["endTimestamp"] ?: duration["endTimestampMs"] ?: JsonNull),
				"activity" to JsonObject(mapOf(
					"start" to (a["startLocation"] ?: JsonNull),
					"end" to (a["endLocation"] ?: JsonNull),
					"distanceMeters" to (a["distance"] ?: JsonNull),
					"topCandidate" to JsonObject(mapOf("type" to (a["activityType"] ?: JsonNull)))
				))
			))
		}
		(o["placeVisit"] as? JsonObject)?.let { v ->
			val loc = (v["location"] as? JsonObject) ?: JsonObject(emptyMap())
			return JsonObject(mapOf(
				"visit" to JsonObject(mapOf(
					"topCandidate" to JsonObject(mapOf("semanticType" to (loc["semanticType"] ?: JsonNull), "placeLocation" to loc))
				))
			))
		}
		return null
	}

	private fun isVehicle(type: String): Boolean {
		val t = type.uppercase().replace(' ', '_')
		return t.contains("VEHICLE") || t.contains("DRIV") || t.contains("MOTORCYCL") || t == "IN_CAR"
	}

	private fun placeKind(v: JsonElement?): String? {
		val s = str(v)?.uppercase()?.replace("TYPE_", "")?.trim() ?: return null
		return when {
			s.contains("HOME") -> "home"
			s.contains("WORK") -> "work"
			else -> null
		}
	}

	private fun nearest(p: LatLng?, places: List<Pair<LatLng, String>>): String? {
		if (p == null) return null
		var best: String? = null
		var bestD = PLACE_RADIUS_METERS
		for ((point, kind) in places) {
			val d = p.distanceTo(point)
			if (d <= bestD) {
				bestD = d
				best = kind
			}
		}
		return best
	}

	/**
	 * Lit un point dans n'importe laquelle des formes rencontrees :
	 * « 48.85°, 2.35° », « geo:48.85,2.35 », {latLng: ...},
	 * {latitudeE7: 488500000, longitudeE7: 23500000}.
	 */
	private fun point(v: JsonElement?): LatLng? {
		when (v) {
			null, JsonNull -> return null
			is JsonObject -> {
				v["latLng"]?.let { return point(it) }
				val lat = num(v["latitudeE7"])
				val lng = num(v["longitudeE7"])
				if (lat != null && lng != null) return LatLng(lat / 1e7, lng / 1e7)
				val la = num(v["lat"] ?: v["latitude"])
				val ln = num(v["lng"] ?: v["longitude"])
				if (la != null && ln != null) return LatLng(la, ln)
				return null
			}
			else -> {
				val s = str(v)?.replaceFirst(Regex("^geo:", RegexOption.IGNORE_CASE), "")?.replace("°", "") ?: return null
				val parts = s.split(',')
				if (parts.size < 2) return null
				val la = parts[0].trim().toDoubleOrNull() ?: return null
				val ln = parts[1].trim().toDoubleOrNull() ?: return null
				return LatLng(la, ln)
			}
		}
	}

	private fun time(v: JsonElement?): Long? {
		val p = v as? JsonPrimitive ?: return null
		if (p.isString) {
			val s = p.content
			val ms = s.toLongOrNull()
			if (ms != null && s.length >= 12) return ms
			return Iso8601.parseEpochMillisOrNull(s)
		}
		return p.doubleOrNull?.toLong()
	}

	private fun num(v: JsonElement?): Double? {
		val p = v as? JsonPrimitive ?: return null
		return p.doubleOrNull ?: p.content.toDoubleOrNull()
	}

	private fun str(v: JsonElement?): String? = (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
}
