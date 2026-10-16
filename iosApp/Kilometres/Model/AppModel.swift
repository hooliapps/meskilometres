import Foundation
import KilometresCore
import SwiftUI
import WidgetKit

/// Le modele de l'application : enveloppe l'etat Kotlin partage dans un
/// ObservableObject, et branche ce qui doit suivre chaque modification
/// (widget, montre, sauvegarde cloud).
@MainActor
final class AppModel: ObservableObject {
	let state: AppState
	@Published private(set) var data: AppData
	@Published private(set) var entitlement: Entitlement
	@Published var pendingFavoriteId: String?

	private var subscription: Subscription?
	private var backupTask: Task<Void, Never>?

	init() {
		let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
		let dir = support.appendingPathComponent("Kilometres", isDirectory: true)
		try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
		state = AppState(store: FileDataStore(files: PlatformFiles(directory: dir.path)))
		state.load()
		data = state.current
		entitlement = state.currentEntitlement
		subscription = state.watch(onData: { [weak self] d in self?.onData(d) }, onEntitlement: { [weak self] e in self?.onEntitlement(e) })
	}

	var isPro: Bool { entitlement.isActive }

	func allows(_ f: Feature) -> Bool { entitlement.allowsNow(feature: f) }

	private func onData(_ d: AppData) {
		data = d
		WidgetBridge.publish(data: d, entitlement: entitlement)
		WatchBridge.shared.publish(data: d, entitlement: entitlement)
		scheduleCloudBackup()
	}

	private func onEntitlement(_ e: Entitlement) {
		entitlement = e
		WidgetBridge.publish(data: data, entitlement: e)
		WatchBridge.shared.publish(data: data, entitlement: e)
		if e.isActive { AdsManager.shared.shutdownForPro() }
	}

	/// Sauvegarde cloud automatique, pour tous, quelques secondes apres la derniere modification.
	private func scheduleCloudBackup() {
		backupTask?.cancel()
		let snapshot = data
		backupTask = Task {
			try? await Task.sleep(nanoseconds: 5_000_000_000)
			if Task.isCancelled { return }
			_ = CloudBackup.write(snapshot)
		}
	}

	/// kilometres://trip?favorite=ID : le widget ou la montre demandent une saisie.
	func handle(url: URL) {
		guard url.scheme == "kilometres", url.host == "trip" else { return }
		let id = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "favorite" }?.value
		pendingFavoriteId = id ?? ""
	}

	func quickAdd(_ f: Favorite) -> Bool {
		state.addTrip(trip: Make.shared.trip(
			id: state.newId(), date: Day.companion.today(), distanceKm: f.distanceKm, from: f.from, to: f.to,
			roundTrip: f.roundTrip, purpose: f.purpose, vehicleId: f.vehicleId ?? data.settings.defaultVehicleId, note: "", justified: false
		))
	}
}
