package com.hooliapps.kilometres.core.rules

import com.hooliapps.kilometres.core.AppData
import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.ExpenseKind
import com.hooliapps.kilometres.core.Purpose
import com.hooliapps.kilometres.core.RuleSet
import com.hooliapps.kilometres.core.Trip
import com.hooliapps.kilometres.core.Vehicle
import com.hooliapps.kilometres.core.km1
import com.hooliapps.kilometres.core.money

/** Avertissements produits par l'estimation, a afficher a l'utilisateur. */
enum class EstimationNote {
	/** Le bareme de l'annee demandee n'est pas encore publie : dernier connu. */
	SCALE_UNCONFIRMED,

	/** Au moins un aller domicile-travail a ete ramene au plafond. */
	COMMUTE_CAP_APPLIED,

	/** Des allers domicile-travail au-dela de deux par jour ont ete ecartes. */
	EXTRA_COMMUTE_LEGS_DROPPED,

	/** Des trajets sans vehicule ont ete ignores faute de vehicule par defaut. */
	TRIPS_WITHOUT_VEHICLE,

	/** Le carburant n'est pas ajoute : il est deja compris dans le bareme. */
	FUEL_INCLUDED_IN_SCALE,

	/** Salaire non renseigne : pas de comparaison avec la deduction forfaitaire. */
	NO_SALARY_FOR_COMPARISON
}

data class VehicleEstimate(val vehicle: Vehicle, val commuteKm: Double, val businessKm: Double, val allowance: Double) {
	val totalKm: Double get() = commuteKm + businessKm
}

data class Estimation(
	val incomeYear: Int,
	val ruleSet: RuleSet,
	val byVehicle: List<VehicleEstimate>,
	val commuteKmRaw: Double,
	val commuteKmRetained: Double,
	val businessKm: Double,
	val allowanceTotal: Double,
	val tollsAndParking: Double,
	val fuelIgnored: Double,
	/** Deduction forfaitaire de 10 %, si le salaire est connu. */
	val flatDeduction: Double?,
	val notes: Set<EstimationNote>
) {
	val total: Double get() = money(allowanceTotal + tollsAndParking)

	/** Vrai si les frais reels depassent la deduction forfaitaire ; null sans salaire. */
	val realCostsBetter: Boolean? get() = flatDeduction?.let { total > it }

	val retainedKm: Double get() = commuteKmRetained + businessKm
}

/** Calcule l'estimation d'une annee de revenus. Pur, sans effet de bord. */
object Estimator {

	fun estimate(data: AppData, incomeYear: Int): Estimation {
		val notes = mutableSetOf<EstimationNote>()
		val settings = data.settings
		val trips = data.trips
			.filter { it.date.year == incomeYear && it.purpose != Purpose.PERSONAL }
			.sortedBy { it.date }

		val retained = retainLegs(trips, settings.ruleSet, notes)

		val commuteByVehicle = mutableMapOf<String, Double>()
		val businessByVehicle = mutableMapOf<String, Double>()
		var commuteRaw = 0.0
		var commuteRetained = 0.0
		var business = 0.0
		var droppedForVehicle = false

		val implicitVehicle = settings.defaultVehicleId ?: data.vehicles.singleOrNull()?.id
		for (r in retained) {
			val vid = r.trip.vehicleId ?: implicitVehicle
			if (vid == null || data.vehicleById(vid) == null) {
				droppedForVehicle = true
				continue
			}
			if (r.trip.purpose == Purpose.COMMUTE) {
				commuteRaw += r.rawKm
				commuteRetained += r.retainedKm
				commuteByVehicle[vid] = (commuteByVehicle[vid] ?: 0.0) + r.retainedKm
			} else {
				business += r.retainedKm
				businessByVehicle[vid] = (businessByVehicle[vid] ?: 0.0) + r.retainedKm
			}
		}
		if (droppedForVehicle) notes += EstimationNote.TRIPS_WITHOUT_VEHICLE

		val byVehicle = mutableListOf<VehicleEstimate>()
		var allowanceTotal = 0.0
		for (v in data.vehicles) {
			val c = commuteByVehicle[v.id] ?: 0.0
			val b = businessByVehicle[v.id] ?: 0.0
			if (c == 0.0 && b == 0.0) continue
			val allowance = when (settings.ruleSet) {
				RuleSet.FR -> FrenchScale.allowance(v, c + b)
				RuleSet.GENERIC -> money((c + b) * settings.genericRatePerKm)
			}
			allowanceTotal += allowance
			byVehicle += VehicleEstimate(v, km1(c), km1(b), allowance)
		}

		if (settings.ruleSet == RuleSet.FR && !FrenchScale.isConfirmed(incomeYear)) notes += EstimationNote.SCALE_UNCONFIRMED

		var tollsParking = 0.0
		var fuel = 0.0
		for (e in data.expenses.filter { it.date.year == incomeYear }) {
			when (e.kind) {
				ExpenseKind.TOLL, ExpenseKind.PARKING, ExpenseKind.OTHER -> tollsParking += e.amount
				ExpenseKind.FUEL -> fuel += e.amount
			}
		}
		if (fuel > 0) notes += EstimationNote.FUEL_INCLUDED_IN_SCALE

		var flat: Double? = null
		if (settings.ruleSet == RuleSet.FR) {
			val salary = settings.netTaxableSalary
			if (salary == null || salary <= 0) {
				notes += EstimationNote.NO_SALARY_FOR_COMPARISON
			} else {
				val f = FrenchScale.flatDeduction(salary, incomeYear)
				flat = f.amount
				if (!f.confirmed) notes += EstimationNote.SCALE_UNCONFIRMED
			}
		}

		return Estimation(
			incomeYear = incomeYear,
			ruleSet = settings.ruleSet,
			byVehicle = byVehicle,
			commuteKmRaw = km1(commuteRaw),
			commuteKmRetained = km1(commuteRetained),
			businessKm = km1(business),
			allowanceTotal = money(allowanceTotal),
			tollsAndParking = money(tollsParking),
			fuelIgnored = money(fuel),
			flatDeduction = flat,
			notes = notes
		)
	}

	private class Retained(val trip: Trip, val rawKm: Double, val retainedKm: Double)

	/**
	 * Applique les plafonds domicile-travail jour par jour. Les trajets
	 * justifies d'abord, puis les plus longs : on garde le plus favorable a
	 * l'utilisateur dans la limite des regles.
	 */
	private fun retainLegs(trips: List<Trip>, ruleSet: RuleSet, notes: MutableSet<EstimationNote>): List<Retained> {
		if (ruleSet == RuleSet.GENERIC) return trips.map { Retained(it, it.totalKm, it.totalKm) }

		val out = mutableListOf<Retained>()
		for ((_, dayTrips) in trips.groupBy { it.date }) {
			var freeLegsLeft = FrenchScale.COMMUTE_LEGS_PER_DAY
			for (t in dayTrips.filter { it.purpose == Purpose.BUSINESS }) out += Retained(t, t.totalKm, t.totalKm)
			val commutes = dayTrips.filter { it.purpose == Purpose.COMMUTE }
				.sortedWith(compareByDescending<Trip> { it.justified }.thenByDescending { it.distanceKm })
			for (t in commutes) {
				val legs = if (t.roundTrip) 2 else 1
				var legKm = t.distanceKm
				if (!t.justified && legKm > FrenchScale.COMMUTE_CAP_KM) {
					legKm = FrenchScale.COMMUTE_CAP_KM
					notes += EstimationNote.COMMUTE_CAP_APPLIED
				}
				val keptLegs = if (t.justified) {
					legs
				} else {
					val kept = minOf(legs, freeLegsLeft)
					freeLegsLeft -= kept
					if (kept < legs) notes += EstimationNote.EXTRA_COMMUTE_LEGS_DROPPED
					kept
				}
				out += Retained(t, t.totalKm, legKm * keptLegs)
			}
		}
		return out
	}
}
