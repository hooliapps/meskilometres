import SwiftUI
import WatchConnectivity

/// Application Apple Watch (Pro) : les kilometres du jour et du mois, et les
/// favoris a saisir d'un geste. Aucune donnee propre : l'iPhone publie le
/// contexte, la montre renvoie une saisie, livree meme si l'iPhone dort.
@main
struct KilometresWatchApp: App {
	@StateObject private var model = WatchModel()
	var body: some Scene { WindowGroup { WatchContentView().environmentObject(model) } }
}

@MainActor
final class WatchModel: NSObject, ObservableObject, WCSessionDelegate {
	struct Fav: Identifiable { let id: String; let label: String; let km: Double }
	@Published var pro = false
	@Published var todayKm = 0.0
	@Published var monthKm = 0.0
	@Published var favorites: [Fav] = []
	@Published var feedback: String?

	override init() {
		super.init()
		apply(UserDefaults.standard.dictionary(forKey: "context") ?? [:])
		guard WCSession.isSupported() else { return }
		WCSession.default.delegate = self
		WCSession.default.activate()
	}

	func add(_ f: Fav) {
		let info = ["add": f.id]
		if WCSession.default.isReachable {
			WCSession.default.sendMessage(info, replyHandler: nil) { _ in WCSession.default.transferUserInfo(info) }
		} else {
			WCSession.default.transferUserInfo(info)
		}
		feedback = "\(tr("watch_added")) · \(f.label)"
	}

	private func apply(_ c: [String: Any]) {
		pro = c["pro"] as? Bool ?? false
		todayKm = c["todayKm"] as? Double ?? 0
		monthKm = c["monthKm"] as? Double ?? 0
		favorites = (c["favorites"] as? [[String: Any]] ?? []).compactMap { d in
			guard let id = d["id"] as? String, let label = d["label"] as? String else { return nil }
			return Fav(id: id, label: label, km: d["km"] as? Double ?? 0)
		}
	}

	nonisolated func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
		let c = session.receivedApplicationContext
		Task { @MainActor in if !c.isEmpty { self.apply(c); UserDefaults.standard.set(c, forKey: "context") } }
	}

	nonisolated func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
		Task { @MainActor in self.apply(applicationContext); UserDefaults.standard.set(applicationContext, forKey: "context") }
	}
}

struct WatchContentView: View {
	@EnvironmentObject private var model: WatchModel

	private func km(_ v: Double) -> String {
		let f = NumberFormatter(); f.numberStyle = .decimal; f.maximumFractionDigits = 1
		return tr("km_value", f.string(from: NSNumber(value: v)) ?? "\(v)")
	}

	var body: some View {
		NavigationStack {
			List {
				if !model.pro {
					Text(tr("widget_pro_needed"))
				} else {
					Section {
						HStack { Text(tr("today")); Spacer(); Text(km(model.todayKm)).bold() }
						HStack { Text(tr("this_month")); Spacer(); Text(km(model.monthKm)).bold() }
					}
					if let feedback = model.feedback { Text(feedback).font(.caption) }
					Section(tr("watch_quick_add")) {
						if model.favorites.isEmpty {
							Text(tr("watch_no_favorites")).font(.caption)
						} else {
							ForEach(model.favorites) { f in
								Button { model.add(f) } label: { VStack(alignment: .leading) { Text(f.label); Text(km(f.km)).font(.caption).foregroundStyle(.secondary) } }
							}
						}
					}
				}
			}
			.navigationTitle(tr("app_name"))
		}
	}
}
