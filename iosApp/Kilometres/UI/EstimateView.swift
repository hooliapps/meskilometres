import KilometresCore
import SwiftUI

struct EstimateView: View {
	@EnvironmentObject private var model: AppModel
	@State private var year: Int32 = Day.companion.today().year

	private var years: [Int32] { Array(Set([Day.companion.today().year] + model.data.trips.map { $0.date.year })).sorted(by: >) }

	var body: some View {
		NavigationStack {
			List {
				Picker(tr("year"), selection: $year) {
					ForEach(years, id: \.self) { Text(tr("income_year", Int($0))).tag($0) }
				}
				if !model.allows(.estimation) {
					ProLockedCard(rewarded: .estimationDay).listRowBackground(Color.clear)
				} else {
					EstimationBody(year: years.contains(year) ? year : years[0])
				}
			}
			.navigationTitle(tr("estimation"))
		}
	}
}

private struct EstimationBody: View {
	@EnvironmentObject private var model: AppModel
	let year: Int32
	@State private var showExpenses = false
	@State private var showExport = false

	var body: some View {
		let data = model.data
		let e = Estimator.shared.estimate(data: data, incomeYear: year)
		let cur = data.settings.currency
		func m(_ v: Double) -> String { Fmt.money(v, cur) }
		func k(_ v: Double) -> String { tr("km_value", Fmt.km(v)) }

		Section { DisclaimerBanner().listRowInsets(EdgeInsets()).listRowBackground(Color.clear) }
		if data.vehicles.isEmpty {
			Section { NavigationLink(destination: VehiclesView()) { Label(tr("no_vehicle"), systemImage: "car") } }
		}
		Section {
			VStack { Text(m(e.total)).font(.system(size: 36, weight: .bold)); Text(tr("total")).font(.caption) }.frame(maxWidth: .infinity)
			KeyValueRow(label: tr("commute_km_raw"), value: k(e.commuteKmRaw))
			KeyValueRow(label: tr("commute_km_retained"), value: k(e.commuteKmRetained))
			KeyValueRow(label: tr("business_km"), value: k(e.businessKm))
			KeyValueRow(label: tr("allowance"), value: m(e.allowanceTotal))
			KeyValueRow(label: tr("tolls_parking"), value: m(e.tollsAndParking))
			KeyValueRow(label: tr("total"), value: m(e.total), bold: true)
		}
		if e.ruleSet == .fr {
			Section(tr("flat_deduction")) {
				if let flat = e.flatDeduction {
					KeyValueRow(label: tr("flat_deduction"), value: m(flat.doubleValue))
					KeyValueRow(label: tr("total"), value: m(e.total))
					let better = e.realCostsBetter?.boolValue == true
					Label(tr(better ? "real_better" : "flat_better"), systemImage: better ? "hand.thumbsup" : "scalemass")
				} else {
					Text(tr("enter_salary"))
					NavigationLink(tr("net_taxable_salary"), destination: SettingsView())
				}
			}
		}
		if !e.byVehicle.isEmpty {
			Section(tr("per_vehicle")) {
				ForEach(e.byVehicle, id: \.vehicle.id) { v in KeyValueRow(label: "\(v.vehicle.name) · \(k(v.totalKm))", value: m(v.allowance)) }
			}
		}
		let notes = e.notes.map { n -> String in
			switch n {
			case .scaleUnconfirmed: return tr("note_scale_unconfirmed")
			case .commuteCapApplied: return tr("note_cap_applied")
			case .extraCommuteLegsDropped: return tr("note_extra_legs")
			case .tripsWithoutVehicle: return tr("note_no_vehicle")
			case .fuelIncludedInScale: return tr("note_fuel", m(e.fuelIgnored))
			default: return tr("note_no_salary")
			}
		}
		if !notes.isEmpty {
			Section { ForEach(notes, id: \.self) { Text("• \($0)").font(.caption) } }
		}
		Section {
			NavigationLink(destination: ExpensesView()) { Label(tr("expenses"), systemImage: "doc.text") }
			NavigationLink(destination: ExportView(year: year)) { Label(tr("export"), systemImage: "square.and.arrow.up") }
		}
		Section(tr("documents_title")) {
			ForEach(tr("documents_list").components(separatedBy: "\n"), id: \.self) { Label($0, systemImage: "checkmark.circle") }
		}
		Section { DisclaimerBanner(long: true).listRowInsets(EdgeInsets()).listRowBackground(Color.clear) }
	}
}
