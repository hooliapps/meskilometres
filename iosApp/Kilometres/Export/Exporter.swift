import Foundation
import KilometresCore

/// Produit un fichier dans le dossier temporaire ; la vue le partage avec ShareLink.
enum Exporter {
	enum Format { case pdf, xlsx, csv }

	static func labels(data: AppData, estimation: Estimation?) -> ExportLabels {
		let currency = data.settings.currency
		var notes: [String] = []
		if let e = estimation {
			for n in e.notes {
				switch n {
				case .scaleUnconfirmed: notes.append(tr("note_scale_unconfirmed"))
				case .commuteCapApplied: notes.append(tr("note_cap_applied"))
				case .extraCommuteLegsDropped: notes.append(tr("note_extra_legs"))
				case .tripsWithoutVehicle: notes.append(tr("note_no_vehicle"))
				case .fuelIncludedInScale: notes.append(tr("note_fuel", Fmt.money(e.fuelIgnored, currency)))
				default: notes.append(tr("note_no_salary"))
				}
			}
		}
		let vehicles = data.vehicles.map { v -> String in
			v.kind == .moped ? "\(v.name) (\(Fmt.vehicleKind(v.kind)))" : "\(v.name) (\(Fmt.vehicleKind(v.kind)), \(tr("cv", Int(v.fiscalPower))))"
		}.joined(separator: ", ")
		let df = DateFormatter(); df.dateStyle = .medium; df.timeStyle = .short
		return ExportLabels(
			appName: tr("app_name"), reportTitle: tr("report_title"), period: tr("export_period"), date: tr("field_date"), from: tr("field_from"), to: tr("field_to"),
			purpose: tr("field_purpose"), vehicle: tr("field_vehicle"), roundTrip: tr("field_round_trip"), distanceKm: "km", note: tr("field_note"), totals: tr("estimation"),
			commuteKm: tr("commute_km_retained"), businessKm: tr("business_km"), allowance: tr("allowance"), tollsAndParking: tr("tolls_parking"), total: tr("total"),
			flatDeduction: tr("flat_deduction"), expenses: tr("expenses"), kind: tr("expense_kind"), amount: tr("amount"), place: tr("place"),
			purposeNames: [Purpose.commute: Fmt.purpose(.commute), Purpose.business: Fmt.purpose(.business), Purpose.personal: Fmt.purpose(.personal)],
			expenseKindNames: [ExpenseKind.toll: Fmt.kind(.toll), ExpenseKind.parking: Fmt.kind(.parking), ExpenseKind.fuel: Fmt.kind(.fuel), ExpenseKind.other: Fmt.kind(.other)],
			disclaimer: tr("disclaimer_long"), generatedOn: tr("generated_on", df.string(from: Date())), yes: tr("yes"), no: tr("no"), notes: notes,
			vehicleSummary: vehicles.isEmpty ? nil : "\(tr("vehicles")) : \(vehicles)"
		)
	}

	static func file(format: Format, data: AppData, from: Day, to: Day, estimation: Estimation?) -> URL? {
		let l = labels(data: data, estimation: estimation)
		let report = Report(data: data, from: from, to: to, estimation: estimation)
		let base = "kilometres-\(from.toIso())-\(to.toIso())"
		let bytes: Data
		let name: String
		switch format {
		case .pdf:
			bytes = PdfRenderer.render(ReportDocument.companion.from(r: report, l: l))
			name = "\(base).pdf"
		case .xlsx:
			bytes = BridgeKt.bytesToNSData(bytes: XlsxExport.shared.build(r: report, l: l)) as Data
			name = "\(base).xlsx"
		case .csv:
			bytes = CsvExport.shared.build(r: report, l: l).data(using: .utf8) ?? Data()
			name = "\(base).csv"
		}
		return write(bytes, name)
	}

	static func backupFile(_ data: AppData) -> URL? {
		let text = BackupCodec.shared.encode(data: data, atEpochMillis: Platform.shared.nowEpochMillis())
		let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd-HHmm"
		return write(text.data(using: .utf8) ?? Data(), "kilometres-\(df.string(from: Date())).\(BackupCodec.shared.FILE_EXTENSION)")
	}

	private static func write(_ bytes: Data, _ name: String) -> URL? {
		let dir = FileManager.default.temporaryDirectory.appendingPathComponent("exports", isDirectory: true)
		try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
		let url = dir.appendingPathComponent(name)
		do { try bytes.write(to: url, options: .atomic); return url } catch { return nil }
	}
}
