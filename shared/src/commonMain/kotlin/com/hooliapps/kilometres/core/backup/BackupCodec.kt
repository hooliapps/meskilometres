package com.hooliapps.kilometres.core.backup

import com.hooliapps.kilometres.core.AppData
import com.hooliapps.kilometres.core.AppJson
import com.hooliapps.kilometres.core.Iso8601
import com.hooliapps.kilometres.core.Platform
import com.hooliapps.kilometres.core.Tombstone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class BackupEnvelope(
	val app: String,
	val backupVersion: Int,
	val createdAt: String,
	val data: AppData
)

/**
 * Une sauvegarde est le meme JSON que le fichier de donnees, enveloppe d'une
 * version et d'une date. La fusion permet de restaurer sans perdre ce qui a
 * ete saisi depuis, et sert aussi a la synchronisation (lot 3).
 */
object BackupCodec {
	const val VERSION = 1
	const val FILE_EXTENSION = "kmbackup.json"
	private const val APP = "kilometres"

	private val pretty = Json(AppJson) { prettyPrint = true }

	fun encode(data: AppData, atEpochMillis: Long = Platform.nowEpochMillis()): String =
		pretty.encodeToString(BackupEnvelope.serializer(), BackupEnvelope(APP, VERSION, Iso8601.formatUtc(atEpochMillis), data))

	/** Rend null si le texte n'est pas une sauvegarde de cette application. */
	fun decode(text: String): AppData? = runCatching {
		val env = AppJson.decodeFromString(BackupEnvelope.serializer(), text)
		if (env.app != APP) null else env.data
	}.getOrNull()

	/**
	 * Fusionne [incoming] dans [local] : pour chaque objet, la version la plus
	 * recemment modifiee gagne ; un objet supprime d'un cote apres sa derniere
	 * modification de l'autre reste supprime. Les reglages suivent la meme
	 * regle. Les cles d'import evitent les doublons de trajets importes.
	 */
	fun merge(local: AppData, incoming: AppData): AppData {
		val deleted = mutableMapOf<String, Long>()
		for (t in local.tombstones + incoming.tombstones) {
			val prev = deleted[t.id]
			if (prev == null || t.deletedAt > prev) deleted[t.id] = t.deletedAt
		}

		fun keep(id: String, updatedAt: Long?): Boolean {
			val d = deleted[id] ?: return true
			return updatedAt != null && updatedAt > d
		}

		fun <T> mergeList(a: List<T>, b: List<T>, id: (T) -> String, updated: (T) -> Long?): List<T> {
			val out = LinkedHashMap<String, T>()
			for (x in a + b) {
				val k = id(x)
				val prev = out[k]
				if (prev == null) {
					out[k] = x
				} else {
					val pu = updated(prev)
					val xu = updated(x)
					if (pu == null || (xu != null && xu > pu)) out[k] = x
				}
			}
			return out.values.filter { keep(id(it), updated(it)) }
		}

		val trips = mergeList(local.trips, incoming.trips, { it.id }, { it.updatedAt })
			.sortedBy { it.createdAt ?: 0 }
		val seenKeys = mutableSetOf<String>()
		val dedupedTrips = trips.filter { t -> t.importKey?.let { seenKeys.add(it) } ?: true }

		val settings = if (incoming.settings.updatedAt != null &&
			(local.settings.updatedAt == null || incoming.settings.updatedAt > local.settings.updatedAt)
		) incoming.settings else local.settings

		return AppData(
			vehicles = mergeList(local.vehicles, incoming.vehicles, { it.id }, { it.updatedAt }),
			trips = dedupedTrips,
			expenses = mergeList(local.expenses, incoming.expenses, { it.id }, { it.updatedAt }),
			favorites = mergeList(local.favorites, incoming.favorites, { it.id }, { it.updatedAt }),
			settings = settings,
			tombstones = deleted.map { (id, at) -> Tombstone(id, at) }
		)
	}
}
