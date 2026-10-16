import AppTrackingTransparency
import GoogleMobileAds
import KilometresCore
import UIKit
import UserMessagingPlatform
import os
#if canImport(DeclaredAgeRange)
import DeclaredAgeRange
#endif

/// Mise en route de la publicite, repris de QuanGame : signaux d'age, puis
/// Pro (jamais de publicite), puis consentement, puis SDK, puis
/// prechargement de l'interstitiel et de l'annonce recompensee.
@MainActor
final class AdsManager: ObservableObject {
	static let shared = AdsManager()
	private let log = Logger(subsystem: "com.consultali.kilometres", category: "AdsManager")
	private let recheckAfterMs: Int64 = 12 * 60 * 60 * 1_000
	private var starting = false
	private var initialisedFor: Audience?
	private var settledAt: Int64 = .min

	@Published private(set) var audience: Audience = Audience.companion.PENDING
	@Published private(set) var canRequestAds = false
	@Published private(set) var privacyOptionsRequired = false
	private(set) var current: AdPlan = AdPolicyKt.plan(audience: Audience.companion.PENDING, consentGranted: false, isPro: false)

	var protectedAudienceKnown: Bool { audience.settled && !audience.adsAllowed }

	func start(isPro: Bool) async {
		if !Features.shared.ADS || starting { return }
		if isPro { shutdownForPro(); return }
		if audience.settled && !isStale() && chaineTerminee() { return }
		guard let presenter = UIApplication.shared.topViewController() else { return }
		starting = true
		defer { starting = false }
		await run(presenter)
	}

	func shutdownForPro() {
		current = AdPolicyKt.plan(audience: audience, consentGranted: false, isPro: true)
		shutdownAds()
	}

	private func isStale() -> Bool { MonotonicClock.nowMs() - settledAt > recheckAfterMs }
	private func chaineTerminee() -> Bool { initialisedFor != nil || !AdPolicyKt.shouldAskConsent(audience: audience) }

	private func run(_ presenter: UIViewController) async {
		let verdict = await AgeRange.resolve(from: presenter)
		audience = verdict
		if verdict.settled { settledAt = MonotonicClock.nowMs() }
		log.info("public : \(verdict.source, privacy: .public) (publicite=\(verdict.adsAllowed), definitif=\(verdict.settled))")
		if !AdPolicyKt.shouldAskConsent(audience: verdict) {
			current = AdPolicyKt.plan(audience: verdict, consentGranted: false, isPro: false)
			shutdownAds()
			return
		}
		if let previous = initialisedFor {
			if previous.treatment != verdict.treatment || previous.maxAdContentRating != verdict.maxAdContentRating { shutdownAds() }
			return
		}
		let granted = await requestConsent(presenter, verdict)
		let decision = AdPolicyKt.plan(audience: verdict, consentGranted: granted, isPro: false)
		current = decision
		canRequestAds = decision.adsAllowed
		privacyOptionsRequired = ConsentInformation.shared.privacyOptionsRequirementStatus == .required
		if !decision.initialiseSdk { return }
		await requestTracking()
		applyRequestConfiguration(decision)
		_ = await MobileAds.shared.start()
		log.info("SDK publicitaire pret")
		if decision.preload {
			InterstitialAds.shared.startPreload()
			RewardedAds.shared.startPreload()
		}
		initialisedFor = verdict
	}

	private func shutdownAds() {
		canRequestAds = false
		InterstitialAds.shared.destroy()
		RewardedAds.shared.destroy()
	}

	private func requestTracking() async {
		guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined, UIApplication.shared.applicationState == .active else { return }
		_ = await ATTrackingManager.requestTrackingAuthorization()
	}

	private func applyRequestConfiguration(_ decision: AdPlan) {
		let configuration = MobileAds.shared.requestConfiguration
		switch decision.treatment {
		case .child:
			configuration.tagForChildDirectedTreatment = NSNumber(value: true)
			configuration.tagForUnderAgeOfConsent = NSNumber(value: true)
		case .teen:
			configuration.tagForChildDirectedTreatment = NSNumber(value: false)
			configuration.tagForUnderAgeOfConsent = NSNumber(value: true)
		default:
			configuration.tagForChildDirectedTreatment = NSNumber(value: false)
			configuration.tagForUnderAgeOfConsent = NSNumber(value: decision.tagForUnderAgeOfConsent)
		}
		switch decision.maxAdContentRating {
		case "G": configuration.maxAdContentRating = .general
		case "PG": configuration.maxAdContentRating = .parentalGuidance
		case "T": configuration.maxAdContentRating = .teen
		default: configuration.maxAdContentRating = .general
		}
	}

	private func requestConsent(_ presenter: UIViewController, _ verdict: Audience) async -> Bool {
		let parameters = RequestParameters()
		parameters.isTaggedForUnderAgeOfConsent = verdict.underAgeOfConsent
		do {
			try await withTimeout(seconds: 30) { try await ConsentInformation.shared.requestConsentInfoUpdate(with: parameters) }
		} catch {
			log.warning("consentement indisponible : \(error.localizedDescription, privacy: .public)")
			return false
		}
		do {
			try await withTimeout(seconds: 600) { try await ConsentForm.loadAndPresentIfRequired(from: presenter) }
		} catch {
			log.warning("formulaire : \(error.localizedDescription, privacy: .public)")
		}
		return ConsentInformation.shared.canRequestAds
	}

	func showPrivacyOptions() {
		guard audience.adsAllowed, let presenter = UIApplication.shared.topViewController() else { return }
		ConsentForm.presentPrivacyOptionsForm(from: presenter) { [weak self] _ in
			Task { @MainActor in
				self?.canRequestAds = ConsentInformation.shared.canRequestAds
				if self?.canRequestAds == false { InterstitialAds.shared.destroy(); RewardedAds.shared.destroy() }
			}
		}
	}
}

/// Tranche d'age declaree (iOS 26). Sous 18 ans : tout est coupe. Sans
/// reponse ou sur un iOS anterieur : grand public.
enum AgeRange {
	@MainActor
	static func resolve(from viewController: UIViewController) async -> Audience {
		#if canImport(DeclaredAgeRange)
		if #available(iOS 26.0, *) {
			do {
				let answer = try await withTimeout(seconds: 600) {
					try await AgeRangeService.shared.requestAgeRange(ageGates: 18, in: viewController)
				}
				switch answer {
				case .declinedSharing:
					return Audience(adsAllowed: true, treatment: .unrestricted, maxAdContentRating: AdPolicyKt.APP_MAX_AD_CONTENT_RATING, underAgeOfConsent: false, source: "age non partage", settled: true)
				case let .sharing(range):
					return Audience.companion.classify(ageLower: range.lowerBound.map { KotlinInt(int: Int32($0)) }, ageUpper: range.upperBound.map { KotlinInt(int: Int32($0)) }, source: "tranche d'age declaree")
				@unknown default:
					return Audience.companion.GENERAL
				}
			} catch is TimedOut {
				return Audience.companion.blocked(source: "delai depasse", settled: false)
			} catch {
				return Audience.companion.GENERAL
			}
		}
		#endif
		return Audience.companion.GENERAL
	}
}

struct TimedOut: Error {}

func withTimeout<T: Sendable>(seconds: UInt64, _ operation: @escaping @MainActor @Sendable () async throws -> T) async throws -> T {
	try await withThrowingTaskGroup(of: T.self) { group in
		group.addTask { try await operation() }
		group.addTask {
			try await Task.sleep(nanoseconds: seconds * 1_000_000_000)
			throw TimedOut()
		}
		defer { group.cancelAll() }
		guard let first = try await group.next() else { throw TimedOut() }
		return first
	}
}

enum MonotonicClock {
	private static let origin = ContinuousClock.now
	static func nowMs() -> Int64 {
		let elapsed = origin.duration(to: .now).components
		return elapsed.seconds * 1_000 + elapsed.attoseconds / 1_000_000_000_000_000
	}
}

extension UIApplication {
	func topViewController() -> UIViewController? {
		let scene = connectedScenes.compactMap { $0 as? UIWindowScene }.first { $0.activationState == .foregroundActive } ?? connectedScenes.compactMap { $0 as? UIWindowScene }.first
		var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
		while let presented = top?.presentedViewController { top = presented }
		return top
	}
}
