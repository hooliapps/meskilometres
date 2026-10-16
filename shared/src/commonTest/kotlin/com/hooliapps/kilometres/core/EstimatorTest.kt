package com.hooliapps.kilometres.core

import com.hooliapps.kilometres.core.rules.EstimationNote
import com.hooliapps.kilometres.core.rules.Estimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EstimatorTest {
	private val v = Vehicle("v", "Clio", fiscalPower = 5)

	private fun trip(id: String, d: Day, km: Double, rt: Boolean = true, p: Purpose = Purpose.COMMUTE, justified: Boolean = false) =
		Trip(id, d, km, roundTrip = rt, purpose = p, vehicleId = "v", justified = justified)

	@Test fun commuteWithoutCap() {
		val e = Estimator.estimate(AppData(vehicles = listOf(v), trips = listOf(trip("1", Day(2024, 3, 4), 30.0))), 2024)
		assertEquals(60.0, e.commuteKmRaw)
		assertEquals(60.0, e.commuteKmRetained)
		assertEquals(38.16, e.allowanceTotal)
		assertTrue(EstimationNote.NO_SALARY_FOR_COMPARISON in e.notes)
		assertFalse(EstimationNote.COMMUTE_CAP_APPLIED in e.notes)
	}

	@Test fun capAt40UnlessJustified() {
		val e = Estimator.estimate(AppData(vehicles = listOf(v), trips = listOf(trip("1", Day(2024, 3, 4), 55.0))), 2024)
		assertEquals(80.0, e.commuteKmRetained)
		assertTrue(EstimationNote.COMMUTE_CAP_APPLIED in e.notes)
		val j = Estimator.estimate(AppData(vehicles = listOf(v), trips = listOf(trip("1", Day(2024, 3, 4), 55.0, justified = true))), 2024)
		assertEquals(110.0, j.commuteKmRetained)
	}

	@Test fun oneRoundTripPerDay() {
		val d = Day(2024, 3, 4)
		val e = Estimator.estimate(AppData(vehicles = listOf(v), trips = listOf(trip("1", d, 20.0), trip("2", d, 20.0))), 2024)
		assertEquals(40.0, e.commuteKmRetained)
		assertTrue(EstimationNote.EXTRA_COMMUTE_LEGS_DROPPED in e.notes)
	}

	@Test fun businessFullPersonalIgnored() {
		val d = Day(2024, 5, 6)
		val e = Estimator.estimate(AppData(vehicles = listOf(v), trips = listOf(
			trip("1", d, 120.0, rt = false, p = Purpose.BUSINESS), trip("2", d, 300.0, p = Purpose.PERSONAL)
		)), 2024)
		assertEquals(120.0, e.businessKm)
		assertEquals(0.0, e.commuteKmRetained)
	}

	@Test fun expenses() {
		val d = Day(2024, 5, 6)
		val e = Estimator.estimate(AppData(vehicles = listOf(v), trips = listOf(trip("1", d, 10.0)), expenses = listOf(
			Expense("e1", d, ExpenseKind.TOLL, 12.5), Expense("e2", d, ExpenseKind.PARKING, 3.0),
			Expense("e3", d, ExpenseKind.FUEL, 60.0), Expense("e4", Day(2023, 1, 1), ExpenseKind.TOLL, 99.0)
		)), 2024)
		assertEquals(15.5, e.tollsAndParking)
		assertEquals(60.0, e.fuelIgnored)
		assertEquals(money(12.72 + 15.5), e.total)
		assertTrue(EstimationNote.FUEL_INCLUDED_IN_SCALE in e.notes)
	}

	@Test fun flatDeductionComparison() {
		val trips = (0 until 200).map { i -> trip("$i", Day(2024, 1, 1).plusDays(i), 35.0) }
		val e = Estimator.estimate(AppData(vehicles = listOf(v), trips = trips, settings = AppSettings(netTaxableSalary = 25000.0)), 2024)
		assertEquals(2500.0, e.flatDeduction)
		assertEquals(14000.0, e.commuteKmRetained)
		assertEquals(true, e.realCostsBetter)
	}

	@Test fun noVehicle() {
		val e = Estimator.estimate(AppData(trips = listOf(Trip("t", Day(2024, 2, 2), 10.0))), 2024)
		assertTrue(EstimationNote.TRIPS_WITHOUT_VEHICLE in e.notes)
		assertEquals(0.0, e.allowanceTotal)
	}

	@Test fun singleVehicleImplicit() {
		val e = Estimator.estimate(AppData(vehicles = listOf(v), trips = listOf(Trip("t", Day(2024, 2, 2), 10.0))), 2024)
		assertEquals(6.36, e.allowanceTotal)
	}

	@Test fun genericRules() {
		val d = Day(2024, 3, 4)
		val e = Estimator.estimate(AppData(vehicles = listOf(v), trips = listOf(trip("1", d, 100.0), trip("2", d, 100.0)),
			settings = AppSettings(ruleSet = RuleSet.GENERIC, genericRatePerKm = 0.5)), 2024)
		assertEquals(400.0, e.commuteKmRetained)
		assertEquals(200.0, e.allowanceTotal)
		assertNull(e.flatDeduction)
	}

	@Test fun futureYearUnconfirmed() {
		val e = Estimator.estimate(AppData(vehicles = listOf(v), trips = listOf(trip("1", Day(2027, 3, 4), 10.0))), 2027)
		assertTrue(EstimationNote.SCALE_UNCONFIRMED in e.notes)
	}
}
