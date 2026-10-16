import Foundation
import KilometresCore

/// Sauvegarde cloud sans compte : une copie du fichier de donnees dans le
/// conteneur iCloud de l'application. Sans la capacite activee, le conteneur
/// est nul et la fonction se declare indisponible.
enum CloudBackup {
	private static let fileName = "kilometres.kmbackup.json"

	static var available: Bool { url != nil }

	private static var url: URL? {
		FileManager.default.url(forUbiquityContainerIdentifier: nil)?.appendingPathComponent("Documents", isDirectory: true).appendingPathComponent(fileName)
	}

	static var lastBackup: Date? {
		guard let url, let attrs = try? FileManager.default.attributesOfItem(atPath: url.path) else { return nil }
		return attrs[.modificationDate] as? Date
	}

	@discardableResult
	static func write(_ data: AppData) -> Bool {
		guard let url else { return false }
		do {
			try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
			try BackupCodec.shared.encode(data: data, atEpochMillis: Platform.shared.nowEpochMillis()).write(to: url, atomically: true, encoding: .utf8)
			return true
		} catch {
			return false
		}
	}

	static func read() -> AppData? {
		guard let url else { return nil }
		try? FileManager.default.startDownloadingUbiquitousItem(at: url)
		guard let text = try? String(contentsOf: url, encoding: .utf8) else { return nil }
		return BackupCodec.shared.decode(text: text)
	}
}
