package com.hooliapps.kilometres.core

import com.hooliapps.kilometres.core.backup.BackupCodec
import com.hooliapps.kilometres.core.importer.HistoryFormat
import com.hooliapps.kilometres.core.importer.LocationHistoryParser
import com.hooliapps.kilometres.core.lot3.MemorySyncFolder
import com.hooliapps.kilometres.core.lot3.SyncEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportAndBackupTest {

	@Test fun onDeviceObject() {
		val json = """{"semanticSegments":[
			{"startTime":"2025-03-03T07:00:00.000+01:00","endTime":"2025-03-03T08:00:00.000+01:00","visit":{"topCandidate":{"semanticType":"HOME","placeLocation":{"latLng":"48.8566°, 2.3522°"}}}},
			{"startTime":"2025-03-03T08:05:00.000+01:00","endTime":"2025-03-03T08:40:00.000+01:00","activity":{"start":{"latLng":"48.8570°, 2.3520°"},"end":{"latLng":"48.9000°, 2.4000°"},"distanceMeters":12345.0,"topCandidate":{"type":"IN_PASSENGER_VEHICLE","probability":0.9}}},
			{"startTime":"2025-03-03T08:45:00.000+01:00","endTime":"2025-03-03T17:00:00.000+01:00","visit":{"topCandidate":{"semanticType":"WORK","placeLocation":{"latLng":"48.9001°, 2.4001°"}}}},
			{"startTime":"2025-03-03T12:00:00.000+01:00","endTime":"2025-03-03T12:20:00.000+01:00","activity":{"distanceMeters":1200.0,"topCandidate":{"type":"WALKING"}}},
			{"startTime":"2025-03-03T18:00:00.000+01:00","endTime":"2025-03-03T18:30:00.000+01:00","timelinePath":[{"point":"48.9°, 2.4°","time":"2025-03-03T18:00:00.000+01:00"}]}
		],"rawSignals":[]}"""
		val r = LocationHistoryParser.parse(json)
		assertEquals(HistoryFormat.ON_DEVICE_OBJECT, r.format)
		assertEquals(1, r.trips.size)
		assertEquals(1, r.skippedNonVehicle)
		val t = r.trips.single()
		assertEquals(12.3, t.distanceKm)
		assertEquals("home", t.startPlace)
		assertEquals("work", t.endPlace)
		assertTrue(t.isCommute)
		val trips = LocationHistoryParser.toTrips(r.trips, { "id" }, Purpose.BUSINESS, null, "Maison", "Bureau")
		assertEquals(Purpose.COMMUTE, trips.single().purpose)
		assertEquals("Maison", trips.single().from)
		assertEquals(TripSource.IMPORTED, trips.single().source)
		assertNotNull(trips.single().importKey)
	}

	@Test fun onDeviceArrayGeo() {
		val json = """[
			{"startTime":"2025-02-01T09:00:00.000Z","endTime":"2025-02-01T09:30:00.000Z","activity":{"start":"geo:45.7640,4.8357","end":"geo:45.8000,4.9000","distanceMeters":"8000.5","topCandidate":{"type":"in passenger vehicle","probability":"0.8"}}},
			{"startTime":"2025-02-01T10:00:00.000Z","endTime":"2025-02-01T10:30:00.000Z","activity":{"distanceMeters":"500","topCandidate":{"type":"cycling"}}}
		]"""
		val r = LocationHistoryParser.parse(json)
		assertEquals(HistoryFormat.ON_DEVICE_ARRAY, r.format)
		assertEquals(8.0, r.trips.single().distanceKm)
		assertNull(r.trips.single().startPlace)
		assertEquals(1, r.skippedNonVehicle)
	}

	@Test fun takeoutE7() {
		val json = """{"timelineObjects":[
			{"placeVisit":{"location":{"latitudeE7":488566000,"longitudeE7":23522000,"semanticType":"TYPE_HOME"}}},
			{"activitySegment":{"startLocation":{"latitudeE7":488566500,"longitudeE7":23522500},"endLocation":{"latitudeE7":490000000,"longitudeE7":24000000},"duration":{"startTimestamp":"2023-06-01T07:30:00Z","endTimestamp":"2023-06-01T08:10:00Z"},"distance":15000,"activityType":"IN_VEHICLE"}}
		]}"""
		val r = LocationHistoryParser.parse(json)
		assertEquals(HistoryFormat.TAKEOUT_SEMANTIC, r.format)
		assertEquals(15.0, r.trips.single().distanceKm)
		assertEquals("home", r.trips.single().startPlace)
	}

	@Test fun invalidInputs() {
		assertTrue(LocationHistoryParser.parse("pas du json").trips.isEmpty())
		assertEquals(HistoryFormat.UNKNOWN, LocationHistoryParser.parse("""{"autre":1}""").format)
		val r = LocationHistoryParser.parse("""{"semanticSegments":[{"startTime":"2025-01-01T00:00:00Z","endTime":"2025-01-01T01:00:00Z","activity":{"topCandidate":{"type":"IN_VEHICLE"}}}]}""")
		assertTrue(r.trips.isEmpty())
		assertEquals(1, r.skippedUnreadable)
	}

	private fun trip(id: String, km: Double, updated: Long, key: String? = null) =
		Trip(id, Day(2026, 1, 1), km, updatedAt = updated, createdAt = updated, importKey = key)

	@Test fun codecRoundTrip() {
		val data = AppData(vehicles = listOf(Vehicle("v", "A")), trips = listOf(trip("t", 10.0, 1)))
		val decoded = BackupCodec.decode(BackupCodec.encode(data))
		assertNotNull(decoded)
		assertEquals(10.0, decoded.trips.single().distanceKm)
		assertEquals("A", decoded.vehicles.single().name)
		assertNull(BackupCodec.decode("""{"app":"autre","data":{}}"""))
		assertNull(BackupCodec.decode("n importe quoi"))
	}

	@Test fun mergeRules() {
		val local = AppData(trips = listOf(trip("a", 10.0, 2), trip("b", 5.0, 1)), tombstones = listOf(Tombstone("c", 3)))
		val incoming = AppData(trips = listOf(trip("a", 12.0, 1), trip("c", 7.0, 2), trip("d", 1.0, 1)))
		val m = BackupCodec.merge(local, incoming)
		val byId = m.trips.associateBy { it.id }
		assertEquals(10.0, byId.getValue("a").distanceKm)
		assertTrue("b" in byId)
		assertFalse("c" in byId)
		assertTrue("d" in byId)
		assertEquals("c", m.tombstones.single().id)
		// Modifie apres sa suppression ailleurs : revient.
		assertEquals("a", BackupCodec.merge(AppData(tombstones = listOf(Tombstone("a", 1))), AppData(trips = listOf(trip("a", 3.0, 2)))).trips.single().id)
		// Deux trajets importes du meme deplacement ne font qu'un.
		val dedup = BackupCodec.merge(AppData(trips = listOf(trip("x", 4.0, 1, "lh:1"))), AppData(trips = listOf(trip("y", 4.0, 2, "lh:1"))))
		assertEquals("x", dedup.trips.single().id)
		// Reglages : les plus recents gagnent, dans les deux sens.
		val l = AppData(settings = AppSettings(homeLabel = "L", updatedAt = 1))
		val r = AppData(settings = AppSettings(homeLabel = "R", updatedAt = 2))
		assertEquals("R", BackupCodec.merge(l, r).settings.homeLabel)
		assertEquals("R", BackupCodec.merge(r, l).settings.homeLabel)
	}

	@Test fun syncInactive() {
		val folder = MemorySyncFolder(BackupCodec.encode(AppData(trips = listOf(trip("r", 1.0, 1)))))
		val out = SyncEngine(folder).sync(AppData(trips = listOf(trip("l", 2.0, 1))))
		assertEquals(listOf("l"), out.trips.map { it.id })
	}
}
