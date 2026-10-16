import KilometresCore
import SwiftUI

struct TripsView: View {
	@EnvironmentObject private var model: AppModel
	@State private var editing: Trip?
	@State private var adding = false
	@State private var showPro = false
	@State private var toast: String?

	var body: some View {
		let today = Day.companion.today()
		let monthTrips = model.data.trips.filter { $0.date.year == today.year && $0.date.month == today.month }
		let yearKm = model.data.trips.filter { $0.date.year == today.year }.reduce(0.0) { $0 + $1.totalKm }
		let grouped = Dictionary(grouping: model.data.trips, by: { $0.date })
		let days = grouped.keys.sorted { $0.compareTo(other: $1) > 0 }

		NavigationStack {
			List {
				Section {
					HStack {
						VStack(alignment: .leading) {
							Text(tr("this_month")).font(.caption)
							Text(tr("km_value", Fmt.km(monthTrips.reduce(0.0) { $0 + $1.totalKm }))).font(.title2.bold())
							Text(tr("trips_count", monthTrips.count)).font(.caption2)
						}
						Spacer()
						VStack(alignment: .leading) {
							Text(tr("this_year")).font(.caption)
							Text(tr("km_value", Fmt.km(yearKm))).font(.title2.bold())
						}
					}
					if !model.isPro {
						let remaining = Int(model.state.remainingFreeTrips(day: today))
						VStack(alignment: .leading) {
							Button {
								showPro = true
							} label: {
								Label(remaining == 0 ? tr("free_limit_reached") : tr("free_remaining", remaining), systemImage: "lock")
							}
							if remaining == 0 { RewardedButton(unlock: .fiveTrips) }
						}
					}
					if !model.data.favorites.isEmpty {
						ScrollView(.horizontal, showsIndicators: false) {
							HStack {
								ForEach(model.data.favorites, id: \.id) { f in
									Button {
										if model.quickAdd(f) { toast = tr("added") } else { showPro = true }
									} label: { Label("\(f.label) · \(Fmt.km(f.totalKm)) km", systemImage: "bolt") }
									.buttonStyle(.bordered)
								}
							}
						}
						Text(tr("quick_add_hint")).font(.caption)
					}
					if let toast { Text(toast).font(.caption).foregroundStyle(.secondary) }
				}
				if model.data.trips.isEmpty {
					Text(tr("no_trips_yet")).foregroundStyle(.secondary)
				}
				ForEach(days, id: \.self) { day in
					Section(Fmt.dayLong(day)) {
						ForEach(grouped[day] ?? [], id: \.id) { t in
							Button { editing = t } label: { TripRow(trip: t) }.buttonStyle(.plain)
						}
					}
				}
			}
			.navigationTitle(tr("app_name"))
			.toolbar {
				ToolbarItem(placement: .primaryAction) {
					Button { adding = true } label: { Label(tr("add_trip"), systemImage: "plus") }
				}
			}
			.sheet(isPresented: $adding) { NavigationStack { TripFormView(tripId: nil, favoriteId: nil) } }
			.sheet(item: $editing) { t in NavigationStack { TripFormView(tripId: t.id, favoriteId: nil) } }
			.sheet(isPresented: $showPro) { NavigationStack { ProView() } }
		}
	}
}

extension Trip: Identifiable {}

struct TripRow: View {
	@EnvironmentObject private var model: AppModel
	let trip: Trip
	var body: some View {
		let route = [trip.from, trip.to].filter { !$0.isEmpty }.joined(separator: " → ")
		let vehicle = model.data.vehicleById(id: trip.vehicleId ?? model.data.settings.defaultVehicleId)
		let subtitle = [Fmt.purpose(trip.purpose), trip.roundTrip ? tr("field_round_trip") : nil, vehicle?.name].compactMap { $0 }.joined(separator: " · ")
		HStack {
			Image(systemName: trip.purpose == .commute ? "house.and.flag" : trip.purpose == .business ? "briefcase" : "person")
				.foregroundStyle(.tint)
			VStack(alignment: .leading) {
				Text(route.isEmpty ? Fmt.purpose(trip.purpose) : route)
				Text(subtitle).font(.caption).foregroundStyle(.secondary)
			}
			Spacer()
			Text(tr("km_value", Fmt.km(trip.totalKm))).font(.headline)
		}
	}
}
