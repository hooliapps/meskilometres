import Foundation
import KilometresCore
import WatchConnectivity

/// Montre (Pro) : l'iPhone publie ses favoris et les totaux dans le contexte
/// d'application ; la montre renvoie l'identifiant d'un favori a saisir,
/// livre meme si l'application iPhone n'est pas ouverte.
final class WatchBridge: NSObject, WCSessionDelegate {
	static let shared = WatchBridge()
	var onAdd: ((String) -> Void)?

	private override init() {
		super.init()
		guard WCSession.isSupported() else { return }
		WCSession.default.delegate = self
		WCSession.default.activate()
	}

	func publish(data: AppData, entitlement: Entitlement) {
		guard WCSession.isSupported(), WCSession.default.activationState == .activated else { return }
		let today = Day.companion.today()
		let context: [String: Any] = [
			"pro": entitlement.allowsNow(feature: .watch),
			"todayKm": data.trips.filter { $0.date == today }.reduce(0.0) { $0 + $1.totalKm },
			"monthKm": data.trips.filter { $0.date.year == today.year && $0.date.month == today.month }.reduce(0.0) { $0 + $1.totalKm },
			"favorites": data.favorites.map { ["id": $0.id, "label": $0.label, "km": $0.totalKm] },
			"at": Date().timeIntervalSince1970
		]
		try? WCSession.default.updateApplicationContext(context)
	}

	func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {}
	func sessionDidBecomeInactive(_ session: WCSession) {}
	func sessionDidDeactivate(_ session: WCSession) { session.activate() }

	func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
		guard let id = userInfo["add"] as? String else { return }
		DispatchQueue.main.async { self.onAdd?(id) }
	}

	func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
		guard let id = message["add"] as? String else { return }
		DispatchQueue.main.async { self.onAdd?(id) }
	}
}
