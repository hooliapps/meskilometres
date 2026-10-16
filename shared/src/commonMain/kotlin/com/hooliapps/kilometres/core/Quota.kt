package com.hooliapps.kilometres.core

/**
 * Limite de la version gratuite : des trajets saisis a la main, au plus
 * [FREE_TRIPS_PER_MONTH] par mois civil, comptes sur la DATE du trajet.
 */
object Quota {
	const val FREE_TRIPS_PER_MONTH = 5

	fun tripsInMonth(trips: Iterable<Trip>, year: Int, month: Int): Int =
		trips.count { it.date.year == year && it.date.month == month }

	/** Trajets encore autorises ce mois-la pour un compte gratuit. */
	fun remaining(trips: Iterable<Trip>, day: Day, bonus: Int = 0): Int =
		(FREE_TRIPS_PER_MONTH + bonus - tripsInMonth(trips, day.year, day.month)).coerceAtLeast(0)

	fun canAdd(pro: Boolean, trips: Iterable<Trip>, day: Day, bonus: Int = 0): Boolean =
		pro || remaining(trips, day, bonus) > 0
}
