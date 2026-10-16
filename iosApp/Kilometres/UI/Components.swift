import KilometresCore
import SwiftUI

/// Carte « reserve au Pro », avec l'essai par annonce quand il y en a un.
struct ProLockedCard: View {
	var message: String = tr("estimation_locked")
	var rewarded: RewardedUnlock? = nil
	@State private var showPro = false

	var body: some View {
		VStack(alignment: .leading, spacing: 10) {
			Label(tr("pro_title"), systemImage: "crown.fill").font(.headline)
			Text(message)
			HStack {
				if let rewarded { RewardedButton(unlock: rewarded) }
				Spacer()
				Button(tr("go_pro")) { showPro = true }.buttonStyle(.borderedProminent)
			}
		}
		.padding()
		.background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))
		.sheet(isPresented: $showPro) { NavigationStack { ProView() } }
	}
}

/// Le bouton « regarder une annonce ». Absent quand la publicite n'est pas autorisee.
struct RewardedButton: View {
	let unlock: RewardedUnlock
	@EnvironmentObject private var model: AppModel
	@ObservedObject private var ads = AdsManager.shared
	@State private var message: String?

	private var label: String {
		switch unlock {
		case .estimationDay: return tr("rewarded_estimation")
		case .exportHour: return tr("rewarded_export")
		default: return tr("rewarded_trips")
		}
	}

	var body: some View {
		// Plafond atteint (cinq trajets deja accordes ce mois-ci) ou Pro : pas de bouton.
		if ads.canRequestAds, model.entitlement.canGrantNow(unlock: unlock) {
			VStack(alignment: .leading) {
				Button {
					RewardedAds.shared.showIfAvailable { earned in
						if earned {
							model.state.grant(unlock: unlock)
							message = tr("rewarded_granted")
						} else {
							message = tr("rewarded_unavailable")
						}
					}
				} label: { Label(label, systemImage: "play.circle") }
				.buttonStyle(.bordered)
				if let message { Text(message).font(.caption) }
			}
		}
	}
}

struct DisclaimerBanner: View {
	var long = false
	var body: some View {
		HStack(alignment: .top, spacing: 8) {
			Image(systemName: "info.circle")
			Text(tr(long ? "disclaimer_long" : "disclaimer_short")).font(.callout)
		}
		.padding(12)
		.frame(maxWidth: .infinity, alignment: .leading)
		.background(Color.yellow.opacity(0.18), in: RoundedRectangle(cornerRadius: 12))
	}
}

struct KeyValueRow: View {
	let label: String
	let value: String
	var bold = false
	var body: some View {
		HStack {
			Text(label).fontWeight(bold ? .bold : .regular)
			Spacer()
			Text(value).fontWeight(bold ? .bold : .regular)
		}
	}
}
