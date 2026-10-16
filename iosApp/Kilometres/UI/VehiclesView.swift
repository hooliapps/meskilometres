import KilometresCore
import SwiftUI

struct VehiclesView: View {
	@EnvironmentObject private var model: AppModel
	@State private var editing: Vehicle?
	@State private var adding = false

	var body: some View {
		List {
			if model.data.vehicles.isEmpty { Text(tr("no_vehicle")).foregroundStyle(.secondary) }
			ForEach(model.data.vehicles, id: \.id) { v in
				Button { editing = v } label: {
					HStack {
						Image(systemName: v.kind == .car ? "car" : v.kind == .motorcycle ? "bicycle" : "scooter")
						VStack(alignment: .leading) {
							Text(v.name)
							Text([Fmt.vehicleKind(v.kind), v.kind == .moped ? nil : tr("cv", Int(v.fiscalPower)), v.isElectric ? tr("electric") : nil].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
						}
						Spacer()
						if model.data.settings.defaultVehicleId == v.id { Text(tr("default_vehicle")).font(.caption2).foregroundStyle(.secondary) }
					}
				}.buttonStyle(.plain)
			}
		}
		.navigationTitle(tr("vehicles"))
		.toolbar { ToolbarItem(placement: .primaryAction) { Button { adding = true } label: { Label(tr("add_vehicle"), systemImage: "plus") } } }
		.sheet(isPresented: $adding) { NavigationStack { VehicleFormView(vehicleId: nil) } }
		.sheet(item: $editing) { v in NavigationStack { VehicleFormView(vehicleId: v.id) } }
	}
}

extension Vehicle: Identifiable {}

struct VehicleFormView: View {
	let vehicleId: String?
	@EnvironmentObject private var model: AppModel
	@Environment(\.dismiss) private var dismiss
	@State private var name = ""
	@State private var kind: VehicleKind = .car
	@State private var power: Int32 = 5
	@State private var electric = false
	@State private var isDefault = false
	@State private var loaded = false

	private var existing: Vehicle? { vehicleId.flatMap { id in model.data.vehicles.first { $0.id == id } } }
	private var powers: [Int32] { kind == .car ? [3, 4, 5, 6, 7] : kind == .motorcycle ? [2, 3, 6] : [] }

	private func powerLabel(_ p: Int32) -> String {
		switch kind {
		case .car: return p == 7 ? tr("cv_or_more", 7) : tr("cv", Int(p))
		case .motorcycle: return p == 2 ? tr("cv_or_less", 2) : p == 6 ? tr("cv_or_more", 6) : tr("cv_range_3_5")
		default: return ""
		}
	}

	var body: some View {
		Form {
			TextField(tr("vehicle_name"), text: $name)
			Picker(tr("vehicle_kind"), selection: $kind) { ForEach(Fmt.vehicleKinds, id: \.self) { Text(Fmt.vehicleKind($0)).tag($0) } }.pickerStyle(.segmented)
			if !powers.isEmpty {
				Picker(tr("fiscal_power"), selection: $power) { ForEach(powers, id: \.self) { Text(powerLabel($0)).tag($0) } }
			}
			Toggle(tr("electric"), isOn: $electric)
			Toggle(tr("default_vehicle"), isOn: $isDefault)
			Section {
				Button(tr("save")) {
					let id = existing?.id ?? model.state.newId()
					model.state.upsertVehicle(v: Make.shared.vehicle(id: id, name: name.trimmingCharacters(in: .whitespaces), kind: kind, fiscalPower: powers.contains(power) ? power : (powers.first ?? 5), isElectric: electric))
					if isDefault { let s = model.data.settings; model.state.updateSettings(s: Make.shared.settings(s: s, ruleSet: s.ruleSet, genericRatePerKm: s.genericRatePerKm, currency: s.currency, netTaxableSalary: s.netTaxableSalary, defaultVehicleId: id, homeLabel: s.homeLabel, workLabel: s.workLabel, homeWorkDistanceKm: s.homeWorkDistanceKm)) }
					dismiss()
				}.disabled(name.trimmingCharacters(in: .whitespaces).isEmpty).frame(maxWidth: .infinity).buttonStyle(.borderedProminent)
				if let v = existing { Button(tr("delete"), role: .destructive) { model.state.deleteVehicle(id: v.id); dismiss() }.frame(maxWidth: .infinity) }
			}
		}
		.navigationTitle(tr(existing == nil ? "add_vehicle" : "edit_vehicle"))
		.toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("cancel")) { dismiss() } } }
		.onAppear {
			guard !loaded else { return }
			loaded = true
			if let v = existing { name = v.name; kind = v.kind; power = v.fiscalPower; electric = v.isElectric; isDefault = model.data.settings.defaultVehicleId == v.id }
		}
	}
}

struct FavoritesView: View {
	@EnvironmentObject private var model: AppModel
	@State private var using: Favorite?

	var body: some View {
		List {
			if model.data.favorites.isEmpty { Text(tr("no_favorites")).foregroundStyle(.secondary) }
			ForEach(model.data.favorites, id: \.id) { f in
				Button { using = f } label: {
					HStack {
						Image(systemName: "bolt")
						VStack(alignment: .leading) {
							Text(f.label)
							Text([[f.from, f.to].filter { !$0.isEmpty }.joined(separator: " → "), Fmt.purpose(f.purpose), f.roundTrip ? tr("field_round_trip") : ""].filter { !$0.isEmpty }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
						}
						Spacer()
						Text(tr("km_value", Fmt.km(f.totalKm)))
					}
				}.buttonStyle(.plain)
			}
			.onDelete { offsets in for i in offsets { model.state.deleteFavorite(id: model.data.favorites[i].id) } }
		}
		.navigationTitle(tr("favorites"))
		.sheet(item: $using) { f in NavigationStack { TripFormView(tripId: nil, favoriteId: f.id) } }
	}
}

extension Favorite: Identifiable {}
