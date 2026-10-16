import KilometresCore
import SwiftUI

struct MoreView: View {
	@EnvironmentObject private var model: AppModel

	var body: some View {
		NavigationStack {
			List {
				Section {
					NavigationLink(destination: ProView()) {
						Label {
							VStack(alignment: .leading) {
								Text(tr(model.isPro ? "pro_active" : "pro_title")).font(.headline)
								Text(model.isPro ? tr("pro_active_since", model.entitlement.productId ?? "") : tr("pro_subtitle")).font(.caption)
							}
						} icon: { Image(systemName: "crown.fill") }
					}
				}
				Section {
					NavigationLink(destination: VehiclesView()) { Label(tr("vehicles"), systemImage: "car") }
					NavigationLink(destination: FavoritesView()) { Label(tr("favorites"), systemImage: "bolt") }
					NavigationLink(destination: ExpensesView()) { row(tr("expenses"), "doc.text", pro: !model.allows(.expenses)) }
				}
				Section {
					NavigationLink(destination: ExportView(year: nil)) { row(tr("export_title"), "square.and.arrow.up", pro: !model.allows(.export)) }
					NavigationLink(destination: ImportView()) { row(tr("import_title"), "square.and.arrow.down", pro: !model.allows(.importHistory)) }
					NavigationLink(destination: BackupView()) { Label(tr("backup_title"), systemImage: "icloud") }
				}
				Section {
					NavigationLink(destination: SettingsView()) { Label(tr("settings"), systemImage: "slider.horizontal.3") }
					NavigationLink(destination: LegalView()) { Label(tr("legal_title"), systemImage: "hand.raised") }
				}
			}
			.navigationTitle(tr("tab_more"))
		}
	}

	private func row(_ title: String, _ icon: String, pro: Bool) -> some View {
		HStack {
			Label(title, systemImage: icon)
			Spacer()
			if pro { Text(tr("pro_badge")).font(.caption2).padding(.horizontal, 8).padding(.vertical, 3).background(Capsule().fill(Color.accentColor.opacity(0.15))) }
		}
	}
}
