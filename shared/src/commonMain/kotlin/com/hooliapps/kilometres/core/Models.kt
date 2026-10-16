package com.hooliapps.kilometres.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong

/** Motif d'un trajet. Seuls COMMUTE et BUSINESS entrent dans l'estimation. */
enum class Purpose { COMMUTE, BUSINESS, PERSONAL }

enum class VehicleKind { CAR, MOTORCYCLE, MOPED }

enum class TripSource { MANUAL, GPS, IMPORTED }

enum class ExpenseKind { TOLL, PARKING, FUEL, OTHER }

enum class ExpenseSource { MANUAL, SCAN }

/**
 * Un jour civil, sans heure ni fuseau, serialise « AAAA-MM-JJ ».
 *
 * Aucune bibliotheque de dates : les quelques operations necessaires
 * (comparaison, jour suivant, numero de jour) tiennent en dix lignes et
 * ne dependent d'aucune API qui bouge.
 */
@Serializable(with = DaySerializer::class)
data class Day(val year: Int, val month: Int, val day: Int) : Comparable<Day> {

	init {
		require(month in 1..12) { "mois invalide : $month" }
		require(day in 1..daysInMonth(year, month)) { "jour invalide : $year-$month-$day" }
	}

	/** Nombre de jours depuis le 1er janvier 1970, algorithme « days from civil ». */
	val epochDay: Long
		get() {
			val y = if (month <= 2) year - 1 else year
			val era = (if (y >= 0) y else y - 399) / 400
			val yoe = y - era * 400
			val mp = (month + 9) % 12
			val doy = (153 * mp + 2) / 5 + day - 1
			val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
			return era * 146097L + doe - 719468L
		}

	fun plusDays(n: Int): Day = fromEpochDay(epochDay + n)

	fun toIso(): String = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

	override fun compareTo(other: Day): Int = epochDay.compareTo(other.epochDay)

	override fun toString(): String = toIso()

	companion object {
		fun parse(s: String): Day {
			val p = s.trim().split('-')
			require(p.size == 3) { "jour invalide : $s" }
			return Day(p[0].toInt(), p[1].toInt(), p[2].toInt())
		}

		fun parseOrNull(s: String): Day? = runCatching { parse(s) }.getOrNull()

		fun today(): Day = Platform.localDayOf(Platform.nowEpochMillis())

		fun fromEpochDay(epochDay: Long): Day {
			val z = epochDay + 719468
			val era = (if (z >= 0) z else z - 146096) / 146097
			val doe = z - era * 146097
			val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
			val y = yoe + era * 400
			val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
			val mp = (5 * doy + 2) / 153
			val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
			val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
			return Day((if (m <= 2) y + 1 else y).toInt(), m, d)
		}

		fun isLeap(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

		fun daysInMonth(year: Int, month: Int): Int = when (month) {
			2 -> if (isLeap(year)) 29 else 28
			4, 6, 9, 11 -> 30
			else -> 31
		}
	}
}

object DaySerializer : KSerializer<Day> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Day", PrimitiveKind.STRING)
	override fun serialize(encoder: Encoder, value: Day) = encoder.encodeString(value.toIso())
	override fun deserialize(decoder: Decoder): Day = Day.parse(decoder.decodeString())
}

@Serializable
data class Vehicle(
	val id: String,
	val name: String,
	val kind: VehicleKind = VehicleKind.CAR,
	/**
	 * Puissance administrative. Voiture : 3 a 7, 7 valant « 7 et plus ».
	 * Moto : 1 a 6, 2 valant « 2 et moins », 6 valant « plus de 5 ».
	 * Cyclomoteur : ignoree.
	 */
	val fiscalPower: Int = 5,
	val isElectric: Boolean = false,
	val updatedAt: Long? = null
)

@Serializable
data class Trip(
	val id: String,
	val date: Day,
	/** Distance d'un ALLER, en kilometres. */
	val distanceKm: Double,
	val from: String = "",
	val to: String = "",
	val roundTrip: Boolean = false,
	val purpose: Purpose = Purpose.COMMUTE,
	val vehicleId: String? = null,
	val note: String = "",
	val source: TripSource = TripSource.MANUAL,
	/**
	 * Motif particulier declare par l'utilisateur : distance domicile-travail
	 * au-dela du plafond, ou aller-retour supplementaire dans la journee.
	 */
	val justified: Boolean = false,
	/** Cle de deduplication d'un trajet importe. */
	val importKey: String? = null,
	val createdAt: Long? = null,
	val updatedAt: Long? = null
) {
	val totalKm: Double get() = if (roundTrip) distanceKm * 2 else distanceKm
}

/**
 * Une depense liee aux deplacements. Jamais d'image : uniquement les
 * donnees structurees lues sur le ticket.
 */
@Serializable
data class Expense(
	val id: String,
	val date: Day,
	val kind: ExpenseKind,
	val amount: Double,
	val currency: String = "EUR",
	val place: String = "",
	val odometerKm: Double? = null,
	val litres: Double? = null,
	val tripId: String? = null,
	val source: ExpenseSource = ExpenseSource.MANUAL,
	val createdAt: Long? = null,
	val updatedAt: Long? = null
)

/** Un trajet favori : une saisie en un geste. */
@Serializable
data class Favorite(
	val id: String,
	val label: String,
	val distanceKm: Double,
	val from: String = "",
	val to: String = "",
	val roundTrip: Boolean = true,
	val purpose: Purpose = Purpose.COMMUTE,
	val vehicleId: String? = null,
	val updatedAt: Long? = null
) {
	val totalKm: Double get() = if (roundTrip) distanceKm * 2 else distanceKm
}

/** Jeu de regles fiscales applique a l'estimation. */
enum class RuleSet {
	/** Bareme kilometrique francais, plafonds domicile-travail, deduction forfaitaire. */
	FR,

	/** Un taux par kilometre saisi par l'utilisateur, sans plafond. */
	GENERIC
}

@Serializable
data class AppSettings(
	val ruleSet: RuleSet = RuleSet.FR,
	val genericRatePerKm: Double = 0.30,
	val currency: String = "EUR",
	/** Salaire net imposable annuel, pour comparer avec la deduction forfaitaire. */
	val netTaxableSalary: Double? = null,
	val defaultVehicleId: String? = null,
	val homeLabel: String = "",
	val workLabel: String = "",
	val homeWorkDistanceKm: Double? = null,
	val updatedAt: Long? = null
)

/**
 * Pierre tombale : un objet supprime, garde pour que la fusion de deux
 * sauvegardes ne le ressuscite pas.
 */
@Serializable
data class Tombstone(val id: String, val deletedAt: Long)

/**
 * Toutes les donnees de l'application. Immuable : chaque modification rend
 * une nouvelle instance, ce qui rend la sauvegarde et la fusion triviales.
 */
@Serializable
data class AppData(
	val schemaVersion: Int = SCHEMA_VERSION,
	val vehicles: List<Vehicle> = emptyList(),
	val trips: List<Trip> = emptyList(),
	val expenses: List<Expense> = emptyList(),
	val favorites: List<Favorite> = emptyList(),
	val settings: AppSettings = AppSettings(),
	val tombstones: List<Tombstone> = emptyList()
) {
	fun vehicleById(id: String?): Vehicle? = if (id == null) null else vehicles.firstOrNull { it.id == id }

	companion object {
		const val SCHEMA_VERSION = 1
		val EMPTY = AppData()
	}
}

/** Le codec JSON de l'application : tolerant aux cles inconnues, defauts ecrits. */
val AppJson: Json = Json {
	ignoreUnknownKeys = true
	encodeDefaults = true
	prettyPrint = false
	isLenient = true
}

/** Arrondi monetaire a deux decimales. */
fun money(v: Double): Double = (v * 100).roundToLong() / 100.0

/** Arrondi d'une distance au dixieme. */
fun km1(v: Double): Double = (v * 10).roundToLong() / 10.0

/**
 * Fabriques pour Swift, qui ne connait pas les valeurs par defaut de Kotlin :
 * un trajet ou une depense se cree ici avec les seuls champs utiles.
 */
object Make {
	fun trip(id: String, date: Day, distanceKm: Double, from: String, to: String, roundTrip: Boolean, purpose: Purpose, vehicleId: String?, note: String, justified: Boolean): Trip =
		Trip(id = id, date = date, distanceKm = distanceKm, from = from, to = to, roundTrip = roundTrip, purpose = purpose, vehicleId = vehicleId, note = note, justified = justified)

	fun tripEdited(t: Trip, date: Day, distanceKm: Double, from: String, to: String, roundTrip: Boolean, purpose: Purpose, vehicleId: String?, note: String, justified: Boolean): Trip =
		t.copy(date = date, distanceKm = distanceKm, from = from, to = to, roundTrip = roundTrip, purpose = purpose, vehicleId = vehicleId, note = note, justified = justified)

	fun favorite(id: String, label: String, distanceKm: Double, from: String, to: String, roundTrip: Boolean, purpose: Purpose, vehicleId: String?): Favorite =
		Favorite(id = id, label = label, distanceKm = distanceKm, from = from, to = to, roundTrip = roundTrip, purpose = purpose, vehicleId = vehicleId)

	fun vehicle(id: String, name: String, kind: VehicleKind, fiscalPower: Int, isElectric: Boolean): Vehicle =
		Vehicle(id = id, name = name, kind = kind, fiscalPower = fiscalPower, isElectric = isElectric)

	fun expense(id: String, date: Day, kind: ExpenseKind, amount: Double, currency: String, place: String, litres: Double?, odometerKm: Double?): Expense =
		Expense(id = id, date = date, kind = kind, amount = amount, currency = currency, place = place, litres = litres, odometerKm = odometerKm)

	fun settings(s: AppSettings, ruleSet: RuleSet, genericRatePerKm: Double, currency: String, netTaxableSalary: Double?, defaultVehicleId: String?, homeLabel: String, workLabel: String, homeWorkDistanceKm: Double?): AppSettings =
		s.copy(ruleSet = ruleSet, genericRatePerKm = genericRatePerKm, currency = currency, netTaxableSalary = netTaxableSalary, defaultVehicleId = defaultVehicleId, homeLabel = homeLabel, workLabel = workLabel, homeWorkDistanceKm = homeWorkDistanceKm)

	fun entitlementPro(productId: String, confirmedAt: Long): Entitlement = Entitlement(pro = true, productId = productId, confirmedAt = confirmedAt)
	fun entitlementRevoked(e: Entitlement): Entitlement = e.copy(pro = false, productId = null)
}
