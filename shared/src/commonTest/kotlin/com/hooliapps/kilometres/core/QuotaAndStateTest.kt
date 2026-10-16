package com.hooliapps.kilometres.core

import com.hooliapps.kilometres.core.store.AppState
import com.hooliapps.kilometres.core.store.MemoryDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuotaAndStateTest {
	private fun t(id: String, d: Day) = Trip(id, d, 1.0)

	@Test fun fivePerMonth() {
		val trips = (1..5).map { t("$it", Day(2026, 4, it)) }
		assertEquals(0, Quota.remaining(trips, Day(2026, 4, 20)))
		assertEquals(5, Quota.remaining(trips, Day(2026, 5, 1)))
		assertFalse(Quota.canAdd(false, trips, Day(2026, 4, 20)))
		assertTrue(Quota.canAdd(true, trips, Day(2026, 4, 20)))
		assertEquals(5, Quota.remaining(trips, Day(2026, 4, 20), bonus = 5))
	}

	@Test fun stateRefusesSixthThenAcceptsPro() {
		val state = AppState(MemoryDataStore()).apply { load() }
		for (i in 1..5) assertTrue(state.addTrip(t("$i", Day(2026, 4, i))))
		assertFalse(state.addTrip(t("6", Day(2026, 4, 6))))
		assertEquals(5, state.current.trips.size)
		state.setEntitlement(Entitlement(pro = true, productId = Products.YEARLY))
		assertTrue(state.addTrip(t("6", Day(2026, 4, 6))))
	}

	@Test fun rewardedFiveTrips() {
		val today = Day.today()
		val state = AppState(MemoryDataStore()).apply { load() }
		for (i in 1..5) assertTrue(state.addTrip(t("$i", today)))
		assertFalse(state.canAddTrip(today))
		state.grant(RewardedUnlock.FIVE_TRIPS)
		assertEquals(5, state.remainingFreeTrips(today))
		assertTrue(state.addTrip(t("6", today)))
	}

	@Test fun backupIsFreeRestoreIsPro() {
		assertTrue(Entitlement.FREE.allows(Feature.CLOUD_BACKUP), "sauvegarde fichier, cloud et export JSON gratuits")
		assertFalse(Entitlement.FREE.allows(Feature.BACKUP_RESTORE), "la restauration d'un fichier reste Pro")
		assertTrue(Entitlement(pro = true).allows(Feature.BACKUP_RESTORE))
	}

	@Test fun rewardedFiveTripsOncePerMonth() {
		val jan = Day(2026, 1, 10)
		val once = Entitlement.FREE.withGrant(RewardedUnlock.FIVE_TRIPS, day = jan)
		assertEquals(5, once.bonusTrips(jan))
		assertFalse(once.canGrant(RewardedUnlock.FIVE_TRIPS, jan))
		val twice = once.withGrant(RewardedUnlock.FIVE_TRIPS, day = jan)
		assertEquals(5, twice.bonusTrips(jan), "une seule recompense de trajets par mois")
		val feb = Day(2026, 2, 1)
		assertTrue(once.canGrant(RewardedUnlock.FIVE_TRIPS, feb))
		assertTrue(once.canGrant(RewardedUnlock.ESTIMATION_DAY, jan), "les recompenses a duree restent possibles")
		assertFalse(Entitlement(pro = true).canGrant(RewardedUnlock.ESTIMATION_DAY, jan))
	}

	@Test fun rewardedEstimationExpires() {
		var now = 1_000_000L
		val e = Entitlement.FREE.withGrant(RewardedUnlock.ESTIMATION_DAY, now = now, day = Day(2026, 1, 1))
		assertTrue(e.allows(Feature.ESTIMATION, now))
		assertFalse(e.allows(Feature.EXPORT, now))
		now += RewardedUnlock.ESTIMATION_DAY.durationMs + 1
		assertFalse(e.allows(Feature.ESTIMATION, now))
		assertTrue(e.pruned(now, Day(2026, 1, 1)).grants.isEmpty())
	}

	@Test fun deleteTripDetachesExpense() {
		val state = AppState(MemoryDataStore()).apply { load() }
		state.addTrip(t("1", Day(2026, 4, 1)))
		state.upsertExpense(Expense("e", Day(2026, 4, 1), ExpenseKind.TOLL, 2.0, tripId = "1"))
		state.deleteTrip("1")
		assertTrue(state.current.trips.isEmpty())
		assertEquals("1", state.current.tombstones.single().id)
		assertNull(state.current.expenses.single().tripId)
	}

	@Test fun firstVehicleIsDefault() {
		val state = AppState(MemoryDataStore()).apply { load() }
		state.upsertVehicle(Vehicle("v1", "A"))
		state.upsertVehicle(Vehicle("v2", "B"))
		assertEquals("v1", state.current.settings.defaultVehicleId)
		state.deleteVehicle("v1")
		assertNull(state.current.settings.defaultVehicleId)
	}

	@Test fun inactiveLotsStayLocked() {
		val pro = Entitlement(pro = true)
		assertTrue(pro.allows(Feature.ESTIMATION))
		assertTrue(pro.allows(Feature.WIDGETS))
		assertFalse(pro.allows(Feature.GPS_RECORDING))
		assertFalse(pro.allows(Feature.RECEIPT_SCAN))
		assertFalse(Entitlement.FREE.allows(Feature.ESTIMATION))
	}

	@Test fun dayArithmetic() {
		assertEquals(Day(2024, 3, 1), Day(2024, 2, 29).plusDays(1))
		assertEquals(Day(2025, 1, 1), Day(2024, 12, 31).plusDays(1))
		assertEquals(0L, Day(1970, 1, 1).epochDay)
		assertEquals(Day(2026, 10, 15), Day.parse("2026-10-15"))
		assertEquals("2026-10-15T20:00:00Z", Iso8601.formatUtc(Iso8601.parseEpochMillisOrNull("2026-10-15T22:00:00.000+02:00")!!))
		assertEquals(Iso8601.parseEpochMillisOrNull("2025-01-01T00:00:00Z"), Iso8601.parseEpochMillisOrNull("2025-01-01T01:00:00+01:00"))
	}
}
