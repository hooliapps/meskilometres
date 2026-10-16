import GoogleMobileAds
import KilometresCore
import UIKit
import os

/// Blocs d'annonces. En debogage, les blocs de demonstration publics. En
/// production, A REMPLACER par les blocs crees pour cette application.
enum AdUnits {
	static var interstitial: String {
		#if DEBUG
		return "ca-app-pub-3940256099942544/4411468910"
		#else
		return "ca-app-pub-3940256099942544/4411468910"
		#endif
	}
	static var rewarded: String {
		#if DEBUG
		return "ca-app-pub-3940256099942544/1712485313"
		#else
		return "ca-app-pub-3940256099942544/1712485313"
		#endif
	}
}

/// Interstitiel apres une saisie, jamais plus d'un toutes les trois minutes (portier partage).
@MainActor
final class InterstitialAds: NSObject, FullScreenContentDelegate {
	static let shared = InterstitialAds()
	private let log = Logger(subsystem: "com.consultali.kilometres", category: "InterstitialAds")
	private let gate = InterstitialGate(minIntervalMs: AdPolicyKt.MIN_INTERSTITIAL_INTERVAL_MS)
	private let throttle = LoadThrottle(minIntervalMs: AdPolicyKt.MIN_AD_LOAD_INTERVAL_MS)
	private var ad: InterstitialAd?
	private var loading = false
	private var preloadStarted = false
	private var onClosed: (() -> Void)?

	func startPreload() {
		guard !preloadStarted, AdsManager.shared.canRequestAds else { return }
		preloadStarted = true
		load()
	}

	private func load() {
		guard preloadStarted, AdsManager.shared.canRequestAds, ad == nil, !loading, throttle.allow(now: MonotonicClock.nowMs()) else { return }
		loading = true
		InterstitialAd.load(with: AdUnits.interstitial, request: Request()) { [weak self] loaded, error in
			Task { @MainActor in
				guard let self else { return }
				self.loading = false
				if let error { self.log.warning("chargement refuse : \(error.localizedDescription, privacy: .public)"); return }
				guard self.preloadStarted, AdsManager.shared.canRequestAds else { return }
				self.ad = loaded
				self.ad?.fullScreenContentDelegate = self
			}
		}
	}

	func showIfAllowed(isPro: Bool, _ onClosed: @escaping () -> Void) {
		let allowed = gate.allows(now: MonotonicClock.nowMs(), adAvailable: ad != nil, consentGranted: AdsManager.shared.canRequestAds, isPro: isPro)
		guard allowed, let ad, let presenter = UIApplication.shared.topViewController() else {
			if ad == nil { load() }
			onClosed()
			return
		}
		self.ad = nil
		self.onClosed = onClosed
		ad.present(from: presenter)
	}

	func adWillPresentFullScreenContent(_ ad: FullScreenPresentingAd) { gate.markShown(now: MonotonicClock.nowMs()) }
	func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) { finish() }
	func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) { finish() }

	private func finish() {
		gate.markClosed()
		let cb = onClosed
		onClosed = nil
		cb?()
		load()
	}

	func destroy() {
		ad = nil
		preloadStarted = false
		gate.markClosed()
	}
}

/// Annonce recompensee : l'utilisateur la demande pour essayer une fonction Pro.
@MainActor
final class RewardedAds: NSObject, FullScreenContentDelegate {
	static let shared = RewardedAds()
	private let log = Logger(subsystem: "com.consultali.kilometres", category: "RewardedAds")
	private let throttle = LoadThrottle(minIntervalMs: AdPolicyKt.MIN_AD_LOAD_INTERVAL_MS)
	private var ad: RewardedAd?
	private var loading = false
	private var preloadStarted = false
	private var earned = false
	private var onResult: ((Bool) -> Void)?

	var isAvailable: Bool { ad != nil }

	func startPreload() {
		guard !preloadStarted, AdsManager.shared.canRequestAds else { return }
		preloadStarted = true
		load()
	}

	private func load() {
		guard preloadStarted, AdsManager.shared.canRequestAds, ad == nil, !loading, throttle.allow(now: MonotonicClock.nowMs()) else { return }
		loading = true
		RewardedAd.load(with: AdUnits.rewarded, request: Request()) { [weak self] loaded, error in
			Task { @MainActor in
				guard let self else { return }
				self.loading = false
				if let error { self.log.warning("chargement refuse : \(error.localizedDescription, privacy: .public)"); return }
				self.ad = loaded
				self.ad?.fullScreenContentDelegate = self
			}
		}
	}

	func showIfAvailable(_ onResult: @escaping (Bool) -> Void) {
		guard AdsManager.shared.canRequestAds, let ad, let presenter = UIApplication.shared.topViewController() else {
			load()
			onResult(false)
			return
		}
		self.ad = nil
		self.onResult = onResult
		earned = false
		ad.present(from: presenter) { [weak self] in self?.earned = true }
	}

	func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) { finish() }
	func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) { earned = false; finish() }

	private func finish() {
		let cb = onResult
		onResult = nil
		cb?(earned)
		load()
	}

	func destroy() {
		ad = nil
		preloadStarted = false
	}
}
