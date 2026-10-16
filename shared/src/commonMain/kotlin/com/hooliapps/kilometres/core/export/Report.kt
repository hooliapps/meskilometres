package com.hooliapps.kilometres.core.export

import com.hooliapps.kilometres.core.AppData
import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.Expense
import com.hooliapps.kilometres.core.ExpenseKind
import com.hooliapps.kilometres.core.Purpose
import com.hooliapps.kilometres.core.Trip
import com.hooliapps.kilometres.core.rules.Estimation

/**
 * Libelles dont les exports ont besoin, fournis par l'interface dans la
 * langue de l'utilisateur : le coeur ne connait aucune ressource.
 */
data class ExportLabels(
	val appName: String,
	val reportTitle: String,
	val period: String,
	val date: String,
	val from: String,
	val to: String,
	val purpose: String,
	val vehicle: String,
	val roundTrip: String,
	val distanceKm: String,
	val note: String,
	val totals: String,
	val commuteKm: String,
	val businessKm: String,
	val allowance: String,
	val tollsAndParking: String,
	val total: String,
	val flatDeduction: String,
	val expenses: String,
	val kind: String,
	val amount: String,
	val place: String,
	val purposeNames: Map<Purpose, String>,
	val expenseKindNames: Map<ExpenseKind, String>,
	val disclaimer: String,
	val generatedOn: String,
	val yes: String,
	val no: String,
	val notes: List<String>,
	val vehicleSummary: String? = null
)

/**
 * Ce qu'un export contient : une selection de trajets et de depenses sur une
 * periode, plus l'estimation de l'annee si elle est demandee.
 */
class Report(val data: AppData, val from: Day, val to: Day, val estimation: Estimation?) {
	val trips: List<Trip> = data.trips.filter { it.date in from..to }.sortedBy { it.date }
	val expenses: List<Expense> = data.expenses.filter { it.date in from..to }.sortedBy { it.date }
	val totalKm: Double get() = trips.sumOf { it.totalKm }

	fun vehicleName(t: Trip): String = data.vehicleById(t.vehicleId ?: data.settings.defaultVehicleId)?.name ?: ""
}

/** Nombre a deux decimales, virgule decimale, sans separateur de milliers. */
fun fmt2(v: Double): String {
	val cents = kotlin.math.round(v * 100).toLong()
	val sign = if (cents < 0) "-" else ""
	val abs = kotlin.math.abs(cents)
	return "$sign${abs / 100},${(abs % 100).toString().padStart(2, '0')}"
}
