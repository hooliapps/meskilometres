import SwiftUI

@main
struct KilometresApp: App {
	@StateObject private var model = AppModel()
	@StateObject private var purchases: Purchases
	@Environment(\.scenePhase) private var scenePhase

	init() {
		let m = AppModel()
		_model = StateObject(wrappedValue: m)
		_purchases = StateObject(wrappedValue: Purchases(state: m.state))
	}

	var body: some Scene {
		WindowGroup {
			RootView()
				.environmentObject(model)
				.environmentObject(purchases)
				.onOpenURL { model.handle(url: $0) }
				.task { await purchases.load() }
		}
		.onChange(of: scenePhase) { phase in
			guard phase == .active else { return }
			Task { @MainActor in await AdsManager.shared.start(isPro: model.isPro) }
		}
	}
}
