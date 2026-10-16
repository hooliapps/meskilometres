package com.hooliapps.kilometres.core

/**
 * Lecture d'un instant ISO 8601 (« 2025-03-03T08:05:00.000+01:00 »,
 * « 2025-03-03T07:05:00Z »), en millisecondes depuis 1970. Sans
 * bibliotheque : la seule forme a lire est celle des fichiers d'historique.
 */
object Iso8601 {
	private val RE = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?\s*(Z|[+-]\d{2}:?\d{2})?$""")

	fun parseEpochMillisOrNull(s: String): Long? {
		val m = RE.matchEntire(s.trim()) ?: return null
		val g = m.groupValues
		val day = runCatching { Day(g[1].toInt(), g[2].toInt(), g[3].toInt()) }.getOrNull() ?: return null
		val hour = g[4].toInt()
		val minute = g[5].toInt()
		val second = g[6].ifEmpty { "0" }.toInt()
		val millis = g[7].ifEmpty { "0" }.padEnd(3, '0').take(3).toInt()
		if (hour > 23 || minute > 59 || second > 60) return null
		val offsetMinutes = when (val z = g[8]) {
			"", "Z" -> 0
			else -> {
				val sign = if (z[0] == '-') -1 else 1
				val digits = z.substring(1).replace(":", "")
				sign * (digits.substring(0, 2).toInt() * 60 + digits.substring(2, 4).toInt())
			}
		}
		val local = day.epochDay * 86_400_000L + hour * 3_600_000L + minute * 60_000L + second * 1_000L + millis
		return local - offsetMinutes * 60_000L
	}

	/** Ecrit un instant en UTC, « 2025-03-03T07:05:00Z ». */
	fun formatUtc(epochMillis: Long): String {
		val days = Math.floorDiv(epochMillis, 86_400_000L)
		val rem = Math.floorMod(epochMillis, 86_400_000L)
		val d = Day.fromEpochDay(days)
		val h = rem / 3_600_000
		val mi = rem % 3_600_000 / 60_000
		val s = rem % 60_000 / 1_000
		return "${d.toIso()}T${h.toString().padStart(2, '0')}:${mi.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}Z"
	}
}

private object Math {
	fun floorDiv(a: Long, b: Long): Long = if ((a xor b) >= 0 || a % b == 0L) a / b else a / b - 1
	fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b
}
