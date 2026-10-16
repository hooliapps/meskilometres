import Foundation

/// Chaines traduites, lues dans <langue>.lproj/Localizable.strings, generes
/// par tools/i18n/generate.js depuis la meme table que les autres cibles.
func tr(_ key: String, _ args: CVarArg...) -> String {
	let format = NSLocalizedString(key, comment: "")
	return args.isEmpty ? format : String(format: format, locale: Locale.current, arguments: args)
}
