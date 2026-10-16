import KilometresCore
import SwiftUI

struct RootView: View {
	@EnvironmentObject private var model: AppModel
	@State private var showPendingForm = false

	var body: some View {
		TabView {
			TripsView().tabItem { Label(tr("tab_trips"), systemImage: "road.lanes") }
			EstimateView().tabItem { Label(tr("tab_estimate"), systemImage: "function") }
			MoreView().tabItem { Label(tr("tab_more"), systemImage: "ellipsis.circle") }
		}
		.onChange(of: model.pendingFavoriteId) { id in showPendingForm = id != nil }
		.sheet(isPresented: $showPendingForm, onDismiss: { model.pendingFavoriteId = nil }) {
			NavigationStack { TripFormView(tripId: nil, favoriteId: model.pendingFavoriteId?.isEmpty == false ? model.pendingFavoriteId : nil) }
		}
		.onAppear {
			WatchBridge.shared.onAdd = { id in
				if let f = model.data.favorites.first(where: { $0.id == id }) { _ = model.quickAdd(f) }
			}
		}
	}
}
