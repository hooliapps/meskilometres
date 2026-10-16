package com.hooliapps.kilometres.core

import com.hooliapps.kilometres.core.ads.Audience
import com.hooliapps.kilometres.core.ads.InterstitialGate
import com.hooliapps.kilometres.core.ads.MIN_INTERSTITIAL_INTERVAL_MS
import com.hooliapps.kilometres.core.ads.plan
import com.hooliapps.kilometres.core.export.CsvExport
import com.hooliapps.kilometres.core.export.ExportLabels
import com.hooliapps.kilometres.core.export.Report
import com.hooliapps.kilometres.core.export.ReportDocument
import com.hooliapps.kilometres.core.export.XlsxExport
import com.hooliapps.kilometres.core.importer.LatLng
import com.hooliapps.kilometres.core.lot2.TrackPoint
import com.hooliapps.kilometres.core.lot2.TripTracker
import com.hooliapps.kilometres.core.lot3.DriveEvent
import com.hooliapps.kilometres.core.lot3.DriveEventKind
import com.hooliapps.kilometres.core.lot3.DriveEventSmoother
import com.hooliapps.kilometres.core.lot3.ReceiptParser
import com.hooliapps.kilometres.core.rules.Estimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportAndLotsTest {

	private val labels = ExportLabels(
		appName = "Kilometres", reportTitle = "Rapport kilometrique", period = "Periode", date = "Date", from = "Depart", to = "Arrivee",
		purpose = "Motif", vehicle = "Vehicule", roundTrip = "A/R", distanceKm = "Km", note = "Note", totals = "Synthese",
		commuteKm = "Km domicile-travail retenus", businessKm = "Km professionnels", allowance = "Indemnite", tollsAndParking = "Peages et parkings",
		total = "Total", flatDeduction = "Deduction forfaitaire", expenses = "Depenses", kind = "Type", amount = "Montant", place = "Lieu",
		purposeNames = mapOf(Purpose.COMMUTE to "Domicile-travail", Purpose.BUSINESS to "Professionnel", Purpose.PERSONAL to "Personnel"),
		expenseKindNames = mapOf(ExpenseKind.TOLL to "Peage", ExpenseKind.PARKING to "Parking", ExpenseKind.FUEL to "Carburant", ExpenseKind.OTHER to "Autre"),
		disclaimer = "Estimation indicative, non garantie.", generatedOn = "2026-01-01", yes = "oui", no = "non", notes = listOf("Note 1"), vehicleSummary = "Clio"
	)

	private val data = AppData(
		vehicles = listOf(Vehicle("v", "Clio; 5CV", fiscalPower = 5)),
		trips = listOf(
			Trip("1", Day(2025, 3, 3), 12.5, from = "Maison", to = "Bureau \"A\"", roundTrip = true, vehicleId = "v"),
			Trip("2", Day(2025, 3, 4), 80.0, from = "Bureau", to = "Client", purpose = Purpose.BUSINESS, vehicleId = "v"),
			Trip("3", Day(2024, 12, 31), 1.0, vehicleId = "v")
		),
		expenses = listOf(Expense("e", Day(2025, 3, 4), ExpenseKind.TOLL, 7.65, place = "Peage"))
	)
	private val report = Report(data, Day(2025, 1, 1), Day(2025, 12, 31), Estimator.estimate(data, 2025))

	@Test fun reportFiltersPeriod() {
		assertEquals(2, report.trips.size)
		assertEquals(105.0, report.totalKm)
	}

	@Test fun csv() {
		val csv = CsvExport.build(report, labels)
		assertTrue(csv.startsWith("﻿"))
		assertTrue(csv.contains("Date;Depart;Arrivee"))
		assertTrue(csv.contains("\"Clio; 5CV\""))
		assertTrue(csv.contains("\"Bureau \"\"A\"\"\""))
		assertTrue(csv.contains("25,00"))
		assertTrue(csv.contains("Estimation indicative"))
	}

	@Test fun xlsxIsAZipWithThreeSheets() {
		val bytes = XlsxExport.build(report, labels)
		assertTrue(bytes.size > 1500)
		assertEquals(0x50, bytes[0].toInt())
		assertEquals(0x4B, bytes[1].toInt())
		val text = bytes.decodeToString()
		assertTrue(text.contains("xl/worksheets/sheet3.xml"))
		assertTrue(text.contains("Bureau &quot;A&quot;"))
		assertTrue(text.contains("<v>25.0</v>"))
	}

	@Test fun reportDocument() {
		val doc = ReportDocument.from(report, labels)
		assertEquals(3, doc.tripsTable.rows.size)
		assertEquals("25,00", doc.tripsTable.rows[0][6])
		assertNotNull(doc.expensesTable)
		assertTrue(doc.summary.any { it.first == "Total" })
		assertEquals("Estimation indicative, non garantie.", doc.footer)
	}

	@Test fun interstitialGate() {
		val gate = InterstitialGate()
		assertFalse(gate.allows(now = 0, adAvailable = true, consentGranted = true, isPro = true))
		assertFalse(gate.allows(now = 0, adAvailable = true, consentGranted = false, isPro = false))
		assertTrue(gate.allows(now = 0, adAvailable = true, consentGranted = true, isPro = false))
		gate.markShown(0)
		assertFalse(gate.allows(now = 1000, adAvailable = true, consentGranted = true, isPro = false))
		gate.markClosed()
		assertFalse(gate.allows(now = MIN_INTERSTITIAL_INTERVAL_MS - 1, adAvailable = true, consentGranted = true, isPro = false))
		assertTrue(gate.allows(now = MIN_INTERSTITIAL_INTERVAL_MS, adAvailable = true, consentGranted = true, isPro = false))
	}

	@Test fun adPlan() {
		assertFalse(plan(Audience.GENERAL, consentGranted = true, isPro = true).adsAllowed)
		assertFalse(plan(Audience.GENERAL, consentGranted = true, isPro = true).askConsent)
		assertTrue(plan(Audience.GENERAL, consentGranted = true).adsAllowed)
		assertTrue(plan(Audience.GENERAL, consentGranted = false).askConsent)
		assertFalse(plan(Audience.GENERAL, consentGranted = false).initialiseSdk)
		assertFalse(plan(Audience.PENDING, consentGranted = true).askConsent)
		assertFalse(Audience.classify(13, 15, "test").adsAllowed)
		assertFalse(Audience.classify(16, 17, "test").adsAllowed)
		assertTrue(Audience.classify(18, null, "test").adsAllowed)
		assertTrue(Audience.classify(null, null, "test").adsAllowed)
	}

	@Test fun tripTracker() {
		fun p(lat: Double, lng: Double, s: Int, acc: Double = 10.0) = TrackPoint(LatLng(lat, lng), 1_000_000L + s * 1000L, acc)
		val tr = TripTracker()
		tr.add(p(48.8566, 2.3522, 0))
		tr.add(p(48.8575, 2.3522, 10))
		tr.add(p(48.8584, 2.3522, 20))
		tr.add(p(48.9000, 2.3522, 21))
		tr.add(p(48.8593, 2.3522, 30, acc = 500.0))
		tr.add(p(48.8584, 2.3522, 40))
		assertEquals(0.2, tr.distanceKm)
		assertFalse(tr.shouldStop(1_000_000L + 4 * 60_000L))
		assertTrue(tr.shouldStop(1_000_000L + 20_000L + 5 * 60_000L))
		assertEquals(5, tr.finish(1_000_000L + 6 * 60_000L).points)
	}

	@Test fun driveSmoother() {
		val s = DriveEventSmoother()
		val t = 1_000_000L
		fun min(m: Int) = t + m * 60_000L
		assertNull(s.feed(DriveEvent(DriveEventKind.STARTED, t)))
		assertNull(s.feed(DriveEvent(DriveEventKind.ENDED, min(1))))
		assertNull(s.feed(DriveEvent(DriveEventKind.STARTED, min(10))))
		assertNotNull(s.feed(DriveEvent(DriveEventKind.ENDED, min(20))))
		assertNull(s.feed(DriveEvent(DriveEventKind.STARTED, min(22))))
		assertEquals(min(10), s.feed(DriveEvent(DriveEventKind.ENDED, min(30)))!!.first)
	}

	@Test fun receiptParser() {
		val toll = ReceiptParser.parse("GARE DE PEAGE SUD\n24/04/2025 09:13\nClasse 1\nTOTAL TTC 7,65 EUR\nTVA 20%")
		assertEquals(ExpenseKind.TOLL, toll.kind)
		assertEquals(7.65, toll.amount)
		assertEquals(Day(2025, 4, 24), toll.date)
		assertEquals("GARE DE PEAGE SUD", toll.place)
		assertNull(toll.litres)

		val fuel = ReceiptParser.parse("STATION DU CENTRE\n2025-05-02\nGAZOLE  38,12 L  x 1,689\nKM 123456\nMONTANT 64,38 €")
		assertEquals(ExpenseKind.FUEL, fuel.kind)
		assertEquals(64.38, fuel.amount)
		assertEquals(38.12, fuel.litres)
		assertEquals(123456.0, fuel.odometerKm)

		val parking = ReceiptParser.parse("PARKING MAIRIE\n03.06.25\n1,20\n2,40 €\nMerci")
		assertEquals(ExpenseKind.PARKING, parking.kind)
		assertEquals(2.40, parking.amount)
		assertEquals(Day(2025, 6, 3), parking.date)

		assertFalse(ReceiptParser.parse("").isUsable)
		val e = ReceiptParser.parse("PEAGE\nTOTAL 3,00 €").toExpense("x", Day(2026, 1, 1))
		assertEquals(ExpenseSource.SCAN, e.source)
		assertFalse(AppJson.encodeToString(Expense.serializer(), e).contains("image"))
	}
}
