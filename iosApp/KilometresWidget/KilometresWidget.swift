import SwiftUI
import WidgetKit

/// Widget d'ecran d'accueil (Pro) : les kilometres du jour et du mois, et
/// les favoris, qui ouvrent la saisie prete a enregistrer. Il lit le JSON
/// que l'application ecrit dans le groupe partage ; sans Pro, il le dit.
struct Snapshot: Codable {
	var pro: Bool
	var todayKm: Double
	var monthKm: Double
	var favorites: [Fav]
	struct Fav: Codable { var id: String; var label: String; var km: Double }

	static func read() -> Snapshot? {
		guard let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.consultali.kilometres")?.appendingPathComponent("widget.json"),
			  let bytes = try? Data(contentsOf: url) else { return nil }
		return try? JSONDecoder().decode(Snapshot.self, from: bytes)
	}
}

struct Entry: TimelineEntry {
	let date: Date
	let snapshot: Snapshot?
}

struct Provider: TimelineProvider {
	func placeholder(in context: Context) -> Entry { Entry(date: Date(), snapshot: Snapshot(pro: true, todayKm: 24, monthKm: 312, favorites: [.init(id: "a", label: "Maison → Bureau", km: 24)])) }
	func getSnapshot(in context: Context, completion: @escaping (Entry) -> Void) { completion(Entry(date: Date(), snapshot: Snapshot.read() ?? placeholder(in: context).snapshot)) }
	func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) {
		let entry = Entry(date: Date(), snapshot: Snapshot.read())
		completion(Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(1800))))
	}
}

struct WidgetView: View {
	let entry: Entry
	@Environment(\.widgetFamily) private var family

	private func km(_ v: Double) -> String {
		let f = NumberFormatter(); f.numberStyle = .decimal; f.maximumFractionDigits = 1
		return tr("km_value", f.string(from: NSNumber(value: v)) ?? "\(v)")
	}

	var body: some View {
		VStack(alignment: .leading, spacing: 6) {
			if let s = entry.snapshot, s.pro {
				HStack {
					VStack(alignment: .leading) { Text(tr("widget_today")).font(.caption2).foregroundStyle(.secondary); Text(km(s.todayKm)).font(.headline) }
					Spacer()
					VStack(alignment: .leading) { Text(tr("widget_month")).font(.caption2).foregroundStyle(.secondary); Text(km(s.monthKm)).font(.headline) }
				}
				if family != .systemSmall {
					ForEach(s.favorites.prefix(2), id: \.id) { f in
						Link(destination: URL(string: "kilometres://trip?favorite=\(f.id)")!) {
							Label("\(f.label) · \(km(f.km))", systemImage: "bolt.fill").font(.caption).lineLimit(1)
								.padding(.horizontal, 8).padding(.vertical, 4)
								.background(Color.accentColor.opacity(0.15), in: Capsule())
						}
					}
				}
			} else {
				Text(tr("widget_pro_needed")).font(.caption)
			}
		}
		.widgetURL(URL(string: "kilometres://trip"))
		.containerBackground(.fill.tertiary, for: .widget)
	}
}

@main
struct KilometresWidgetBundle: WidgetBundle {
	var body: some Widget { TripsWidget() }
}

struct TripsWidget: Widget {
	var body: some WidgetConfiguration {
		StaticConfiguration(kind: "com.consultali.kilometres.trips", provider: Provider()) { WidgetView(entry: $0) }
			.configurationDisplayName(tr("widget_name"))
			.description(tr("widget_description"))
			.supportedFamilies([.systemSmall, .systemMedium])
	}
}
