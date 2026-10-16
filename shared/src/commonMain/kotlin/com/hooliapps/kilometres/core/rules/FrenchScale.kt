package com.hooliapps.kilometres.core.rules

import com.hooliapps.kilometres.core.Vehicle
import com.hooliapps.kilometres.core.VehicleKind
import com.hooliapps.kilometres.core.money

/**
 * Bareme kilometrique francais.
 *
 * Les coefficients sont ceux publies pour les revenus 2022 et reconduits
 * pour les revenus 2023 et 2024. Pour une annee de revenus plus recente que
 * [LAST_CONFIRMED_INCOME_YEAR], l'application applique le dernier bareme
 * connu et le signale « a confirmer » : ce fichier est a mettre a jour a
 * chaque publication officielle, en general en fevrier ou mars.
 *
 * Formule, pour une distance d en km sur l'annee :
 *   d <= bande1           : a * d
 *   bande1 < d <= bande2  : b * d + c
 *   d > bande2            : e * d
 * Les vehicules electriques beneficient d'une majoration de 20 %.
 */
object FrenchScale {
	const val LAST_CONFIRMED_INCOME_YEAR = 2024
	const val ELECTRIC_BONUS = 0.20

	/** Plafond de la distance domicile-travail retenue sans motif particulier, pour un aller. */
	const val COMMUTE_CAP_KM = 40.0

	/** Nombre d'allers simples retenus par jour sans motif particulier. */
	const val COMMUTE_LEGS_PER_DAY = 2

	private val CAR_BANDS = 5000.0 to 20000.0
	private val TWO_WHEEL_BANDS = 3000.0 to 6000.0

	/** Voitures, par puissance administrative : a, b, c, e. */
	private val CARS = mapOf(
		3 to doubleArrayOf(0.529, 0.316, 1065.0, 0.370),
		4 to doubleArrayOf(0.606, 0.340, 1330.0, 0.407),
		5 to doubleArrayOf(0.636, 0.357, 1395.0, 0.427),
		6 to doubleArrayOf(0.665, 0.374, 1457.0, 0.447),
		7 to doubleArrayOf(0.697, 0.394, 1515.0, 0.470)
	)

	/** Motos : 1 = 1 ou 2 CV, 3 = 3 a 5 CV, 5 = plus de 5 CV. */
	private val MOTORCYCLES = mapOf(
		1 to doubleArrayOf(0.395, 0.099, 891.0, 0.248),
		3 to doubleArrayOf(0.468, 0.082, 1158.0, 0.275),
		5 to doubleArrayOf(0.606, 0.079, 1583.0, 0.343)
	)

	private val MOPED = doubleArrayOf(0.315, 0.079, 711.0, 0.198)

	/** Deduction forfaitaire de 10 % : plancher et plafond par annee de revenus. */
	private val FLAT_DEDUCTION = mapOf(
		2022 to (472.0 to 13522.0),
		2023 to (495.0 to 14171.0),
		2024 to (504.0 to 14426.0)
	)

	fun isConfirmed(incomeYear: Int): Boolean = incomeYear <= LAST_CONFIRMED_INCOME_YEAR

	fun carPowerBucket(fiscalPower: Int): Int = fiscalPower.coerceIn(3, 7)

	fun motorcyclePowerBucket(fiscalPower: Int): Int = when {
		fiscalPower <= 2 -> 1
		fiscalPower <= 5 -> 3
		else -> 5
	}

	/** Indemnite annuelle pour [km] parcourus avec [vehicle]. */
	fun allowance(vehicle: Vehicle, km: Double): Double {
		if (km <= 0) return 0.0
		val (coefs, bands) = when (vehicle.kind) {
			VehicleKind.CAR -> CARS.getValue(carPowerBucket(vehicle.fiscalPower)) to CAR_BANDS
			VehicleKind.MOTORCYCLE -> MOTORCYCLES.getValue(motorcyclePowerBucket(vehicle.fiscalPower)) to TWO_WHEEL_BANDS
			VehicleKind.MOPED -> MOPED to TWO_WHEEL_BANDS
		}
		val (band1, band2) = bands
		val raw = when {
			km <= band1 -> coefs[0] * km
			km <= band2 -> coefs[1] * km + coefs[2]
			else -> coefs[3] * km
		}
		return money(if (vehicle.isElectric) raw * (1 + ELECTRIC_BONUS) else raw)
	}

	/** Taux de la premiere bande, a titre indicatif. */
	fun firstBandRate(vehicle: Vehicle): Double = when (vehicle.kind) {
		VehicleKind.CAR -> CARS.getValue(carPowerBucket(vehicle.fiscalPower))[0]
		VehicleKind.MOTORCYCLE -> MOTORCYCLES.getValue(motorcyclePowerBucket(vehicle.fiscalPower))[0]
		VehicleKind.MOPED -> MOPED[0]
	}

	data class FlatDeduction(val amount: Double, val confirmed: Boolean)

	/** Deduction forfaitaire de 10 % sur [netTaxableSalary], bornee. */
	fun flatDeduction(netTaxableSalary: Double, incomeYear: Int): FlatDeduction {
		val year = FLAT_DEDUCTION.keys.filter { it <= incomeYear }.maxOrNull() ?: FLAT_DEDUCTION.keys.min()
		val (floor, ceiling) = FLAT_DEDUCTION.getValue(year)
		val raw = netTaxableSalary * 0.10
		return FlatDeduction(money(raw.coerceIn(floor, ceiling)), FLAT_DEDUCTION.containsKey(incomeYear))
	}
}
