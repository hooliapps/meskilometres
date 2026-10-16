package com.hooliapps.kilometres.core

import com.hooliapps.kilometres.core.rules.FrenchScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrenchScaleTest {
	private val car5 = Vehicle("a", "Clio", fiscalPower = 5)
	private val car7 = Vehicle("b", "SUV", fiscalPower = 9)
	private val car3 = Vehicle("c", "Petite", fiscalPower = 2)
	private val elec = Vehicle("d", "Zoe", fiscalPower = 4, isElectric = true)
	private val moto = Vehicle("e", "Moto", kind = VehicleKind.MOTORCYCLE, fiscalPower = 4)
	private val moped = Vehicle("f", "Cyclo", kind = VehicleKind.MOPED)

	@Test fun carBands() {
		assertEquals(2544.00, FrenchScale.allowance(car5, 4000.0))
		assertEquals(4965.00, FrenchScale.allowance(car5, 10000.0))
		assertEquals(10675.00, FrenchScale.allowance(car5, 25000.0))
	}

	@Test fun powerClamped() {
		assertEquals(697.00, FrenchScale.allowance(car7, 1000.0))
		assertEquals(529.00, FrenchScale.allowance(car3, 1000.0))
	}

	@Test fun electricBonus() = assertEquals(727.20, FrenchScale.allowance(elec, 1000.0))

	@Test fun twoWheels() {
		assertEquals(936.00, FrenchScale.allowance(moto, 2000.0))
		assertEquals(1568.00, FrenchScale.allowance(moto, 5000.0))
		assertEquals(1386.00, FrenchScale.allowance(moped, 7000.0))
	}

	@Test fun zero() = assertEquals(0.0, FrenchScale.allowance(car5, 0.0))

	@Test fun flatDeduction() {
		assertEquals(FrenchScale.FlatDeduction(3000.0, true), FrenchScale.flatDeduction(30000.0, 2024))
		assertEquals(504.0, FrenchScale.flatDeduction(2000.0, 2024).amount)
		assertEquals(14426.0, FrenchScale.flatDeduction(500000.0, 2024).amount)
		assertFalse(FrenchScale.flatDeduction(30000.0, 2030).confirmed)
	}

	@Test fun confirmedYears() {
		assertTrue(FrenchScale.isConfirmed(2024))
		assertFalse(FrenchScale.isConfirmed(2026))
	}
}
