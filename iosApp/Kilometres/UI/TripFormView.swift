import KilometresCore
import SwiftUI

/// Saisie d'un trajet : la page la plus utilisee, donc la plus simple.
struct TripFormView: View {
	let tripId: String?
	let favoriteId: String?
	@EnvironmentObject private var model: AppModel
	@Environment(\.dismiss) private var dismiss

	@State private var date = Date()
	@State private var from = ""
	@State private var to = ""
	@State private var distance = ""
	@State private var roundTrip = true
	@State private var purpose: Purpose = .commute
	@State private var justified = false
	@State private var vehicleId: String?
	@State private var note = ""
	@State private var saveFavorite = false
	@State private var favoriteLabel = ""
	@State private var error: String?
	@State private var confirmDelete = false
	@State private var showPro = false
	@State private var loaded = false

	private var existing: Trip? { tripId.flatMap { id in model.data.trips.first { $0.id == id } } }

	var body: some View {
		Form {
			Section {
				DatePicker(tr("field_date"), selection: $date, displayedComponents: .date)
				Picker(tr("field_purpose"), selection: $purpose) {
					ForEach(Fmt.purposes, id: \.self) { Text(Fmt.purpose($0)).tag($0) }
				}.pickerStyle(.segmented)
			}
			Section {
				HStack {
					TextField(tr("field_from"), text: $from)
					Button { let f = from; from = to; to = f } label: { Image(systemName: "arrow.left.arrow.right") }.buttonStyle(.borderless)
					TextField(tr("field_to"), text: $to)
				}
				HStack {
					TextField(tr("field_distance"), text: $distance).keyboardType(.decimalPad)
					Text("km").foregroundStyle(.secondary)
				}
				if let error { Text(error).font(.caption).foregroundStyle(.red) }
				Toggle(tr("field_round_trip"), isOn: $roundTrip)
				if model.data.vehicles.isEmpty {
					Text(tr("vehicle_needed")).font(.caption)
				} else {
					Picker(tr("field_vehicle"), selection: $vehicleId) {
						ForEach(model.data.vehicles, id: \.id) { Text($0.name).tag(Optional($0.id)) }
					}
				}
				if purpose == .commute {
					Toggle(isOn: $justified) {
						VStack(alignment: .leading) {
							Text(tr("field_justified"))
							Text(tr("field_justified_help")).font(.caption).foregroundStyle(.secondary)
						}
					}
				}
				TextField(tr("field_note"), text: $note)
			}
			if existing == nil {
				Section {
					Toggle(tr("save_as_favorite"), isOn: $saveFavorite)
					if saveFavorite { TextField(tr("favorite_label"), text: $favoriteLabel) }
				}
			}
			Section {
				Button(tr("save")) { save() }.frame(maxWidth: .infinity).buttonStyle(.borderedProminent)
				if existing == nil, !model.isPro, !model.state.canAddTrip(day: Fmt.day(from: date)) { RewardedButton(unlock: .fiveTrips) }
				if existing != nil { Button(tr("delete"), role: .destructive) { confirmDelete = true }.frame(maxWidth: .infinity) }
			}
		}
		.navigationTitle(tr(existing == nil ? "trip_new" : "trip_edit"))
		.navigationBarTitleDisplayMode(.inline)
		.toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("cancel")) { dismiss() } } }
		.confirmationDialog(tr("confirm_delete"), isPresented: $confirmDelete) {
			Button(tr("delete"), role: .destructive) { if let t = existing { model.state.deleteTrip(id: t.id) }; dismiss() }
		}
		.sheet(isPresented: $showPro) { NavigationStack { ProView() } }
		.onAppear { if !loaded { load(); loaded = true } }
	}

	private func load() {
		let s = model.data.settings
		let favorite = favoriteId.flatMap { id in model.data.favorites.first { $0.id == id } }
		if let t = existing {
			date = Fmt.date(t.date); from = t.from; to = t.to; distance = Fmt.number(t.distanceKm); roundTrip = t.roundTrip
			purpose = t.purpose; justified = t.justified; vehicleId = t.vehicleId; note = t.note
		} else if let f = favorite {
			from = f.from; to = f.to; distance = Fmt.number(f.distanceKm); roundTrip = f.roundTrip; purpose = f.purpose; vehicleId = f.vehicleId ?? s.defaultVehicleId
		} else {
			vehicleId = s.defaultVehicleId
			if let d = s.homeWorkDistanceKm { from = s.homeLabel; to = s.workLabel; distance = Fmt.number(d.doubleValue) }
		}
	}

	private func save() {
		guard let km = Fmt.parse(distance), km > 0 else { error = tr("invalid_distance"); return }
		let day = Fmt.day(from: date)
		let isJustified = purpose == .commute && justified
		if let t = existing {
			model.state.updateTrip(trip: Make.shared.tripEdited(t: t, date: day, distanceKm: km, from: from.trimmingCharacters(in: .whitespaces), to: to.trimmingCharacters(in: .whitespaces), roundTrip: roundTrip, purpose: purpose, vehicleId: vehicleId, note: note, justified: isJustified))
		} else {
			let trip = Make.shared.trip(id: model.state.newId(), date: day, distanceKm: km, from: from.trimmingCharacters(in: .whitespaces), to: to.trimmingCharacters(in: .whitespaces), roundTrip: roundTrip, purpose: purpose, vehicleId: vehicleId, note: note, justified: isJustified)
			guard model.state.addTrip(trip: trip) else { showPro = true; return }
			if saveFavorite {
				var label = favoriteLabel.trimmingCharacters(in: .whitespaces)
				if label.isEmpty { label = [trip.from, trip.to].filter { !$0.isEmpty }.joined(separator: " → ") }
				if label.isEmpty { label = Fmt.purpose(purpose) }
				model.state.upsertFavorite(f: Make.shared.favorite(id: model.state.newId(), label: label, distanceKm: km, from: trip.from, to: trip.to, roundTrip: roundTrip, purpose: purpose, vehicleId: vehicleId))
			}
			InterstitialAds.shared.showIfAllowed(isPro: model.isPro) {}
		}
		dismiss()
	}
}
