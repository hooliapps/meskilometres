package com.hooliapps.kilometres.desktop

import java.util.Locale
import java.util.Properties

/**
 * Les chaines, lues depuis les fichiers .properties generes par
 * tools/i18n/generate.js : la langue de la session, puis l'anglais.
 */
object Strings {
	private val table: Map<String, String> by lazy { load(Locale.getDefault()) }

	private fun load(locale: Locale): Map<String, String> {
		val candidates = listOf(
			locale.toLanguageTag(),
			"${locale.language}-${locale.script}".takeIf { locale.script.isNotEmpty() },
			if (locale.language == "zh") if (locale.country in setOf("TW", "HK", "MO")) "zh-Hant" else "zh-Hans" else null,
			locale.language,
			"en"
		).filterNotNull()
		for (c in candidates) {
			val stream = Strings::class.java.getResourceAsStream("/i18n/strings_$c.properties") ?: continue
			val p = Properties()
			stream.reader(Charsets.UTF_8).use { p.load(it) }
			return p.entries.associate { (k, v) -> k.toString() to v.toString() }
		}
		return emptyMap()
	}

	operator fun get(key: String): String = table[key] ?: key

	fun format(key: String, vararg args: Any?): String {
		var s = get(key)
		args.forEachIndexed { i, a -> s = s.replace("%${i + 1}\$s", a.toString()).replace("%${i + 1}\$d", a.toString()) }
		return s
	}
}
