import Foundation
import KilometresCore
import WidgetKit

/// Le widget lit un petit JSON dans le groupe d'applications : les
/// kilometres du jour et du mois, les favoris, et si le Pro est actif.
enum WidgetBridge {
	static let group = "group.com.consultali.kilometres"
	static let fileName = "widget.json"

	struct Snapshot: Codable {
		var pro: Bool
		var todayKm: Double
		var monthKm: Double
		var favorites: [Fav]
		struct Fav: Codable { var id: String; var label: String; var km: Double }
	}

	static var url: URL? { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)?.appendingPathComponent(fileName) }

	static func publish(data: AppData, entitlement: Entitlement) {
		guard let url else { return }
		let today = Day.companion.today()
		let trips = data.trips
		let snapshot = Snapshot(
			pro: entitlement.allowsNow(feature: .widgets),
			todayKm: trips.filter { $0.date == today }.reduce(0.0) { $0 + $1.totalKm },
			monthKm: trips.filter { $0.date.year == today.year && $0.date.month == today.month }.reduce(0.0) { $0 + $1.totalKm },
			favorites: data.favorites.prefix(3).map { .init(id: $0.id, label: $0.label, km: $0.totalKm) }
		)
		if let bytes = try? JSONEncoder().encode(snapshot) {
			try? bytes.write(to: url, options: .atomic)
			WidgetCenter.shared.reloadAllTimelines()
		}
	}

	static func read() -> Snapshot? {
		guard let url, let bytes = try? Data(contentsOf: url) else { return nil }
		return try? JSONDecoder().decode(Snapshot.self, from: bytes)
	}
}
