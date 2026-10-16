import Foundation
import KilometresCore
import StoreKit

/// Abonnements Pro par StoreKit 2. Sans serveur : la boutique est la seule
/// source de verite. Une reponse de la boutique sans abonnement actif retire
/// le droit ; une absence de reponse (hors ligne) ne change rien.
@MainActor
final class Purchases: ObservableObject {
	enum Status { case idle, loading, ready, unavailable, pending, failed }

	@Published private(set) var status: Status = .idle
	@Published private(set) var products: [String: Product] = [:]

	private let state: AppState
	private var updates: Task<Void, Never>?
	private let ids: [String]

	init(state: AppState) {
		self.state = state
		ids = [Products.shared.MONTHLY, Products.shared.YEARLY]
		updates = Task { [weak self] in
			for await result in Transaction.updates {
				guard let self else { return }
				if case .verified(let t) = result {
					self.apply(t)
					await t.finish()
				}
			}
		}
	}

	func load() async {
		status = .loading
		do {
			let list = try await Product.products(for: ids)
			products = Dictionary(uniqueKeysWithValues: list.map { ($0.id, $0) })
			status = list.isEmpty ? .unavailable : .ready
			await refreshEntitlement(revokeIfNone: true)
		} catch {
			status = .unavailable
		}
	}

	func buy(_ id: String) async {
		guard let product = products[id] else { return }
		status = .pending
		do {
			let result = try await product.purchase()
			switch result {
			case .success(let verification):
				if case .verified(let t) = verification {
					apply(t)
					await t.finish()
				}
				status = .ready
			case .userCancelled, .pending:
				status = .ready
			@unknown default:
				status = .ready
			}
		} catch {
			status = .failed
		}
	}

	/// Restauration demandee par l'utilisateur. Rend vrai si un abonnement actif a ete retrouve.
	func restore() async -> Bool {
		try? await AppStore.sync()
		return await refreshEntitlement(revokeIfNone: true)
	}

	@discardableResult
	private func refreshEntitlement(revokeIfNone: Bool) async -> Bool {
		var active: Transaction?
		for await result in Transaction.currentEntitlements {
			if case .verified(let t) = result, ids.contains(t.productID), t.revocationDate == nil { active = t }
		}
		if let t = active {
			apply(t)
			return true
		}
		if revokeIfNone, state.currentEntitlement.pro {
			state.setEntitlement(e: Make.shared.entitlementRevoked(e: state.currentEntitlement))
		}
		return false
	}

	private func apply(_ t: Transaction) {
		guard ids.contains(t.productID), t.revocationDate == nil else { return }
		state.setEntitlement(e: Make.shared.entitlementPro(productId: t.productID, confirmedAt: Int64(Date().timeIntervalSince1970 * 1000)))
	}
}
