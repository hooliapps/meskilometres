import KilometresCore
import SwiftUI
import UniformTypeIdentifiers

/// Import d'un historique de positions (Pro). Le fichier est lu, analyse, puis oublie.
struct ImportView: View {
	@EnvironmentObject private var model: AppModel
	@Environment(\.dismiss) private var dismiss
	@State private var picking = false
	@State private var result: ImportResult?
	@State private var defaultPurpose: Purpose = .business
	@State private var vehicleId: String?
	@State private var message: String?

	var body: some View {
		Group {
			if !model.allows(.importHistory) {
				ScrollView { ProLockedCard().padding() }
			} else {
				List {
					Section {
						Text(tr("import_help"))
						Text(tr("import_commute_hint")).font(.caption)
						Button { picking = true } label: { Label(tr("import_pick"), systemImage: "folder") }
					}
					if let r = result {
						if r.format == .unknown {
							Text(tr("import_unreadable")).foregroundStyle(.red)
						} else {
							let already = Set(model.data.trips.compactMap { $0.importKey })
							let newCount = r.trips.filter { !already.contains($0.key) }.count
							Section {
								Text(tr("import_found", Int(r.trips.count))).font(.headline)
								if let first = r.trips.first, let last = r.trips.last {
									Text(tr("import_period", Fmt.day(Platform.shared.localDayOf(epochMillis: first.startEpochMillis)), Fmt.day(Platform.shared.localDayOf(epochMillis: last.startEpochMillis))))
									Text(tr("km_value", Fmt.km(r.trips.reduce(0.0) { $0 + $1.distanceKm })))
								}
								if r.skippedNonVehicle > 0 { Text(tr("import_skipped", Int(r.skippedNonVehicle))).font(.caption) }
							}
							if !r.trips.isEmpty {
								Section {
									Picker(tr("import_default_purpose"), selection: $defaultPurpose) { ForEach(Fmt.purposes, id: \.self) { Text(Fmt.purpose($0)).tag($0) } }
									if !model.data.vehicles.isEmpty {
										Picker(tr("field_vehicle"), selection: $vehicleId) { ForEach(model.data.vehicles, id: \.id) { Text($0.name).tag(Optional($0.id)) } }
									}
									Button(tr("import_confirm", newCount)) {
										let s = model.data.settings
										let trips = LocationHistoryParser.shared.toTrips(imported: r.trips, newId: { model.state.newId() }, defaultPurpose: defaultPurpose, vehicleId: vehicleId, homeLabel: s.homeLabel, workLabel: s.workLabel)
										let added = model.state.addTrips(trips: trips)
										message = added == 0 ? tr("import_nothing_new") : tr("import_done", Int(added))
										result = nil
									}.disabled(newCount == 0).buttonStyle(.borderedProminent)
									if newCount == 0 { Text(tr("import_nothing_new")).font(.caption) }
								}
							}
						}
					}
					if let message { Text(message) }
				}
			}
		}
		.navigationTitle(tr("import_title"))
		.onAppear { vehicleId = model.data.settings.defaultVehicleId }
		.fileImporter(isPresented: $picking, allowedContentTypes: [.json, .plainText, .data]) { res in
			guard case .success(let url) = res else { return }
			let access = url.startAccessingSecurityScopedResource()
			defer { if access { url.stopAccessingSecurityScopedResource() } }
			if let text = try? String(contentsOf: url, encoding: .utf8) { result = LocationHistoryParser.shared.parse(text: text) }
		}
	}
}

/// Export PDF, tableur ou CSV (Pro, ou un export contre une annonce).
struct ExportView: View {
	let year: Int32?
	@EnvironmentObject private var model: AppModel
	@State private var format: Exporter.Format = .pdf
	@State private var selectedYear: Int32 = Day.companion.today().year
	@State private var wholeYear = true
	@State private var from = Date()
	@State private var to = Date()
	@State private var includeEstimation = true
	@State private var file: URL?
	@State private var failed = false

	private var years: [Int32] { Array(Set([Day.companion.today().year] + model.data.trips.map { $0.date.year })).sorted(by: >) }

	var body: some View {
		Group {
			if !model.allows(.export) {
				ScrollView { ProLockedCard(rewarded: .exportHour).padding() }
			} else {
				Form {
					Picker(tr("export_title"), selection: $format) {
						Text(tr("export_pdf")).tag(Exporter.Format.pdf)
						Text(tr("export_xlsx")).tag(Exporter.Format.xlsx)
						Text(tr("export_csv")).tag(Exporter.Format.csv)
					}.pickerStyle(.segmented)
					Section(tr("export_period")) {
						Toggle(tr("year"), isOn: $wholeYear)
						if wholeYear {
							Picker(tr("year"), selection: $selectedYear) { ForEach(years, id: \.self) { Text(String($0)).tag($0) } }
						} else {
							DatePicker(tr("export_from"), selection: $from, displayedComponents: .date)
							DatePicker(tr("export_to"), selection: $to, displayedComponents: .date)
						}
						Toggle(tr("export_include_estimation"), isOn: $includeEstimation)
					}
					Section {
						Text(tr("export_share")).font(.caption)
						Button(tr("export")) {
							let f = wholeYear ? Day(year: selectedYear, month: 1, day: 1) : Fmt.day(from: from)
							let t = wholeYear ? Day(year: selectedYear, month: 12, day: 31) : Fmt.day(from: to)
							let est = includeEstimation ? Estimator.shared.estimate(data: model.data, incomeYear: f.year) : nil
							file = Exporter.file(format: format, data: model.data, from: f, to: t, estimation: est)
							failed = file == nil
						}.buttonStyle(.borderedProminent)
						if let file { ShareLink(item: file) { Label(tr("export_share"), systemImage: "square.and.arrow.up") } }
						if failed { Text(tr("export_failed")).foregroundStyle(.red) }
					}
				}
			}
		}
		.navigationTitle(tr("export_title"))
		.onAppear { if let year, years.contains(year) { selectedYear = year } }
	}
}

/// Sauvegarde, gratuite : fichier a deposer ou l'on veut, et conteneur cloud de l'app. Seule la restauration d'un fichier est Pro.
struct BackupView: View {
	@EnvironmentObject private var model: AppModel
	@State private var picking = false
	@State private var file: URL?
	@State private var message: String?

	var body: some View {
		List {
					Section {
						Text(tr("backup_explain"))
						Button { file = Exporter.backupFile(model.data) } label: { Label(tr("backup_export"), systemImage: "square.and.arrow.up") }
						if let file { ShareLink(item: file) { Label(tr("export_share"), systemImage: "square.and.arrow.up") } }
						if model.allows(.backupRestore) {
							Button { picking = true } label: { Label(tr("backup_restore"), systemImage: "arrow.counterclockwise") }
							Text(tr("backup_restore_help")).font(.caption)
						} else {
							ProLockedCard()
						}
					}
					Section(tr("cloud_title")) {
						if !CloudBackup.available {
							Text(tr("cloud_unavailable"))
						} else {
							Text(tr("cloud_explain"))
							if let last = CloudBackup.lastBackup {
								let df = DateFormatter(); let _ = { df.dateStyle = .medium; df.timeStyle = .short }()
								Text(tr("cloud_last", df.string(from: last))).font(.caption)
							} else {
								Text(tr("cloud_never")).font(.caption)
							}
							Button { message = CloudBackup.write(model.data) ? tr("cloud_done") : tr("error_generic") } label: { Label(tr("cloud_now"), systemImage: "icloud.and.arrow.up") }
							Button {
								if let incoming = CloudBackup.read() { restore(incoming) } else { message = tr("cloud_nothing") }
							} label: { Label(tr("cloud_restore"), systemImage: "icloud.and.arrow.down") }
						}
					}
					if let message { Text(message) }
		}
		.navigationTitle(tr("backup_title"))
		.fileImporter(isPresented: $picking, allowedContentTypes: [.json, .data]) { res in
			guard case .success(let url) = res else { return }
			let access = url.startAccessingSecurityScopedResource()
			defer { if access { url.stopAccessingSecurityScopedResource() } }
			if let text = try? String(contentsOf: url, encoding: .utf8), let incoming = BackupCodec.shared.decode(text: text) { restore(incoming) } else { message = tr("backup_invalid") }
		}
	}

	private func restore(_ incoming: AppData) {
		let merged = BackupCodec.shared.merge(local: model.data, incoming: incoming)
		model.state.replaceData(next: merged)
		message = tr("backup_restored", Int(merged.trips.count))
	}
}

struct SettingsView: View {
	@EnvironmentObject private var model: AppModel
	@State private var rate = ""
	@State private var currency = ""
	@State private var salary = ""
	@State private var home = ""
	@State private var work = ""
	@State private var homeWork = ""
	@State private var loaded = false

	var body: some View {
		let s = model.data.settings
		Form {
			Section(tr("rules_title")) {
				Picker(tr("rules_title"), selection: Binding(get: { s.ruleSet }, set: { r in model.state.updateSettings(s: Make.shared.settings(s: s, ruleSet: r, genericRatePerKm: s.genericRatePerKm, currency: s.currency, netTaxableSalary: s.netTaxableSalary, defaultVehicleId: s.defaultVehicleId, homeLabel: s.homeLabel, workLabel: s.workLabel, homeWorkDistanceKm: s.homeWorkDistanceKm)) })) {
					Text(tr("rule_fr")).tag(RuleSet.fr)
					Text(tr("rule_generic")).tag(RuleSet.generic)
				}.pickerStyle(.segmented)
				if s.ruleSet == .generic { TextField(tr("generic_rate"), text: $rate).keyboardType(.decimalPad) }
				TextField(tr("currency"), text: $currency)
				if s.ruleSet == .fr {
					TextField(tr("net_taxable_salary"), text: $salary).keyboardType(.numberPad)
					Text(tr("enter_salary")).font(.caption)
				}
			}
			Section(footer: Text(tr("places_help"))) {
				TextField(tr("home_label"), text: $home)
				TextField(tr("work_label"), text: $work)
				HStack { TextField(tr("home_work_distance"), text: $homeWork).keyboardType(.decimalPad); Text("km").foregroundStyle(.secondary) }
			}
			if !model.data.vehicles.isEmpty {
				Picker(tr("default_vehicle"), selection: Binding(get: { s.defaultVehicleId }, set: { id in model.state.updateSettings(s: Make.shared.settings(s: s, ruleSet: s.ruleSet, genericRatePerKm: s.genericRatePerKm, currency: s.currency, netTaxableSalary: s.netTaxableSalary, defaultVehicleId: id, homeLabel: s.homeLabel, workLabel: s.workLabel, homeWorkDistanceKm: s.homeWorkDistanceKm)) })) {
					ForEach(model.data.vehicles, id: \.id) { Text($0.name).tag(Optional($0.id)) }
				}
			}
		}
		.navigationTitle(tr("settings"))
		.onAppear {
			guard !loaded else { return }
			loaded = true
			rate = Fmt.number(s.genericRatePerKm); currency = s.currency; salary = s.netTaxableSalary.map { Fmt.number($0.doubleValue) } ?? ""
			home = s.homeLabel; work = s.workLabel; homeWork = s.homeWorkDistanceKm.map { Fmt.number($0.doubleValue) } ?? ""
		}
		.onDisappear {
			let s = model.data.settings
			model.state.updateSettings(s: Make.shared.settings(
				s: s, ruleSet: s.ruleSet, genericRatePerKm: Fmt.parse(rate) ?? s.genericRatePerKm,
				currency: currency.trimmingCharacters(in: .whitespaces).uppercased().isEmpty ? s.currency : String(currency.trimmingCharacters(in: .whitespaces).uppercased().prefix(3)),
				netTaxableSalary: Fmt.parse(salary).map { KotlinDouble(double: $0) }, defaultVehicleId: s.defaultVehicleId,
				homeLabel: home.trimmingCharacters(in: .whitespaces), workLabel: work.trimmingCharacters(in: .whitespaces),
				homeWorkDistanceKm: Fmt.parse(homeWork).map { KotlinDouble(double: $0) }
			))
		}
	}
}

struct ProView: View {
	@EnvironmentObject private var model: AppModel
	@EnvironmentObject private var purchases: Purchases
	@ObservedObject private var ads = AdsManager.shared
	@State private var message: String?

	private var features: [String] {
		var list = ["pro_feature_unlimited", "pro_feature_no_ads", "pro_feature_estimation", "pro_feature_export", "pro_feature_import", "pro_feature_expenses", "pro_feature_cloud", "pro_feature_widgets", "pro_feature_watch"]
		if Features.shared.GPS_RECORDING || Features.shared.AUTO_DETECTION || Features.shared.CAR_CONNECTION { list.append("pro_feature_gps") }
		if Features.shared.RECEIPT_SCAN { list.append("pro_feature_scan") }
		return list
	}

	var body: some View {
		List {
			Section {
				VStack(spacing: 8) {
					Image(systemName: "crown.fill").font(.system(size: 48)).foregroundStyle(.tint)
					Text(tr("pro_subtitle")).multilineTextAlignment(.center).font(.headline)
				}.frame(maxWidth: .infinity)
			}
			Section(tr("pro_features_title")) {
				ForEach(features, id: \.self) { Label(tr($0), systemImage: "checkmark.circle.fill") }
			}
			if model.isPro {
				Section { Label(tr("pro_active"), systemImage: "checkmark.seal.fill"); Text(tr("pro_active_since", model.entitlement.productId ?? "")).font(.caption) }
			} else {
				Section {
					let yearly = purchases.products[Products.shared.YEARLY]
					let monthly = purchases.products[Products.shared.MONTHLY]
					plan(title: tr("pro_yearly", yearly?.displayPrice ?? Products.shared.YEARLY_FALLBACK_PRICE), subtitle: tr("pro_yearly_hint"), enabled: purchases.status == .ready && yearly != nil) { Task { await purchases.buy(Products.shared.YEARLY) } }
					plan(title: tr("pro_monthly", monthly?.displayPrice ?? Products.shared.MONTHLY_FALLBACK_PRICE), subtitle: nil, enabled: purchases.status == .ready && monthly != nil) { Task { await purchases.buy(Products.shared.MONTHLY) } }
					switch purchases.status {
					case .loading: ProgressView()
					case .pending: Text(tr("purchase_pending"))
					case .unavailable: Text(tr("pro_unavailable")).font(.caption)
					case .failed: Text(tr("purchase_failed")).font(.caption)
					default: EmptyView()
					}
				}
			}
			Section {
				Button(tr("restore_purchases")) { Task { message = await purchases.restore() ? tr("purchase_restored") : tr("purchase_nothing") } }
				if !model.isPro, ads.privacyOptionsRequired { Button(tr("ads_privacy_options")) { ads.showPrivacyOptions() } }
				if let message { Text(message).font(.caption) }
				Text(tr("pro_legal")).font(.caption2).foregroundStyle(.secondary)
			}
		}
		.navigationTitle(tr("pro_title"))
	}

	private func plan(title: String, subtitle: String?, enabled: Bool, action: @escaping () -> Void) -> some View {
		HStack {
			VStack(alignment: .leading) {
				Text(title).font(.headline)
				if let subtitle { Text(subtitle).font(.caption) }
			}
			Spacer()
			Button(tr("subscribe"), action: action).buttonStyle(.borderedProminent).disabled(!enabled)
		}
	}
}

struct LegalView: View {
	@ObservedObject private var ads = AdsManager.shared
	var body: some View {
		List {
			Section { DisclaimerBanner(long: true).listRowInsets(EdgeInsets()).listRowBackground(Color.clear) }
			Section { Text(tr("privacy_text")) }
			if ads.protectedAudienceKnown {
				Section(tr("ads_disabled_title")) { Text(tr("ads_disabled_text")) }
			}
			Section { Text(tr("pro_legal")).font(.caption) }
			Section { Text(tr("version", Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")).font(.caption) }
		}
		.navigationTitle(tr("legal_title"))
	}
}
