package com.hooliapps.kilometres.core.store

import com.hooliapps.kilometres.core.AppData
import com.hooliapps.kilometres.core.AppJson
import com.hooliapps.kilometres.core.AppSettings
import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.Entitlement
import com.hooliapps.kilometres.core.Expense
import com.hooliapps.kilometres.core.Favorite
import com.hooliapps.kilometres.core.Platform
import com.hooliapps.kilometres.core.PlatformFiles
import com.hooliapps.kilometres.core.Quota
import com.hooliapps.kilometres.core.RewardedUnlock
import com.hooliapps.kilometres.core.Tombstone
import com.hooliapps.kilometres.core.Trip
import com.hooliapps.kilometres.core.Vehicle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** Ou les donnees vivent. */
interface DataStore {
	fun load(): AppData
	fun save(data: AppData)
	fun loadEntitlement(): Entitlement
	fun saveEntitlement(e: Entitlement)
}

/**
 * Un seul fichier JSON, ecrit de facon atomique : un fichier temporaire puis
 * un renommage, de sorte qu'une coupure en pleine ecriture laisse toujours
 * l'ancienne version intacte. Un fichier illisible est mis de cote, jamais
 * ecrase.
 */
class FileDataStore(private val files: PlatformFiles) : DataStore {

	override fun load(): AppData {
		val text = files.readText(DATA_FILE) ?: return AppData.EMPTY
		return runCatching { AppJson.decodeFromString(AppData.serializer(), text) }.getOrElse {
			runCatching { files.copy(DATA_FILE, "$DATA_FILE.corrupt-${Platform.nowEpochMillis()}") }
			AppData.EMPTY
		}
	}

	override fun save(data: AppData) = files.writeTextAtomic(DATA_FILE, AppJson.encodeToString(AppData.serializer(), data))

	override fun loadEntitlement(): Entitlement {
		val text = files.readText(ENTITLEMENT_FILE) ?: return Entitlement.FREE
		return runCatching { AppJson.decodeFromString(Entitlement.serializer(), text) }.getOrDefault(Entitlement.FREE)
	}

	override fun saveEntitlement(e: Entitlement) =
		files.writeTextAtomic(ENTITLEMENT_FILE, AppJson.encodeToString(Entitlement.serializer(), e))

	companion object {
		const val DATA_FILE = "kilometres-data.json"
		const val ENTITLEMENT_FILE = "kilometres-entitlement.json"
	}
}

class MemoryDataStore(var data: AppData = AppData.EMPTY, var entitlement: Entitlement = Entitlement.FREE) : DataStore {
	override fun load() = data
	override fun save(data: AppData) { this.data = data }
	override fun loadEntitlement() = entitlement
	override fun saveEntitlement(e: Entitlement) { entitlement = e }
}

/**
 * L'etat de l'application, observable par toutes les interfaces : Compose
 * lit les StateFlow, SwiftUI s'abonne par [observe]. Toute modification
 * passe par ici, est persistee aussitot, et notifie.
 *
 * Les appels sont synchrones et courts : un fichier de quelques dizaines de
 * kilo-octets. L'interface les lance depuis le fil principal sans attente
 * perceptible ; une tres grosse base passerait par un Dispatcher dedie.
 */
class AppState(
	private val store: DataStore,
	private val newIdProvider: () -> String = { randomId() },
	private val clock: () -> Long = { Platform.nowEpochMillis() }
) {
	private val _data = MutableStateFlow(AppData.EMPTY)
	private val _entitlement = MutableStateFlow(Entitlement.FREE)

	val data: StateFlow<AppData> = _data.asStateFlow()
	val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

	/** Constructeur pour Swift, qui ne voit pas les valeurs par defaut. */
	constructor(store: DataStore) : this(store, { randomId() }, { Platform.nowEpochMillis() })

	val current: AppData get() = _data.value
	val currentEntitlement: Entitlement get() = _entitlement.value
	val isPro: Boolean get() = _entitlement.value.isActive

	fun load() {
		_data.value = store.load()
		_entitlement.value = store.loadEntitlement().pruned(clock())
	}

	fun newId(): String = newIdProvider()
	fun now(): Long = clock()

	private fun commit(next: AppData) {
		_data.value = next
		store.save(next)
	}

	fun setEntitlement(e: Entitlement) {
		_entitlement.value = e
		store.saveEntitlement(e)
	}

	/** Recompense publicitaire obtenue. */
	fun grant(unlock: RewardedUnlock) = setEntitlement(_entitlement.value.withGrant(unlock, clock(), Day.today()))

	/** Remplace toutes les donnees, apres une restauration par exemple. */
	fun replaceData(next: AppData) = commit(next)

	// ---- Trajets ------------------------------------------------------------

	fun remainingFreeTrips(day: Day): Int = Quota.remaining(current.trips, day, _entitlement.value.bonusTrips(day))

	fun canAddTrip(day: Day): Boolean = Quota.canAdd(isPro, current.trips, day, _entitlement.value.bonusTrips(day))

	/** Ajoute un trajet. Rend false si le quota gratuit est atteint. */
	fun addTrip(trip: Trip): Boolean {
		if (!canAddTrip(trip.date)) return false
		val t = trip.copy(createdAt = trip.createdAt ?: clock(), updatedAt = clock())
		commit(current.copy(trips = current.trips + t))
		return true
	}

	/** Ajoute plusieurs trajets d'un coup (import, Pro). Rend le nombre ajoute. */
	fun addTrips(trips: Iterable<Trip>): Int {
		val existingKeys = current.trips.mapNotNull { it.importKey }.toMutableSet()
		val added = mutableListOf<Trip>()
		for (t in trips) {
			val k = t.importKey
			if (k != null && !existingKeys.add(k)) continue
			added += t.copy(createdAt = t.createdAt ?: clock(), updatedAt = clock())
		}
		if (added.isEmpty()) return 0
		commit(current.copy(trips = current.trips + added))
		return added.size
	}

	fun updateTrip(trip: Trip) {
		val t = trip.copy(updatedAt = clock())
		commit(current.copy(trips = current.trips.map { if (it.id == t.id) t else it }))
	}

	fun deleteTrip(id: String) = commit(current.copy(
		trips = current.trips.filter { it.id != id },
		expenses = current.expenses.map { if (it.tripId == id) it.copy(tripId = null) else it },
		tombstones = current.tombstones + Tombstone(id, clock())
	))

	// ---- Vehicules ----------------------------------------------------------

	fun upsertVehicle(v: Vehicle) {
		val stamped = v.copy(updatedAt = clock())
		val exists = current.vehicles.any { it.id == v.id }
		val vehicles = if (exists) current.vehicles.map { if (it.id == v.id) stamped else it } else current.vehicles + stamped
		var settings = current.settings
		if (settings.defaultVehicleId == null) settings = settings.copy(defaultVehicleId = v.id, updatedAt = clock())
		commit(current.copy(vehicles = vehicles, settings = settings))
	}

	fun deleteVehicle(id: String) {
		var settings = current.settings
		if (settings.defaultVehicleId == id) settings = settings.copy(defaultVehicleId = null, updatedAt = clock())
		commit(current.copy(
			vehicles = current.vehicles.filter { it.id != id },
			trips = current.trips.map { if (it.vehicleId == id) it.copy(vehicleId = null) else it },
			favorites = current.favorites.map { if (it.vehicleId == id) it.copy(vehicleId = null) else it },
			settings = settings,
			tombstones = current.tombstones + Tombstone(id, clock())
		))
	}

	// ---- Favoris ------------------------------------------------------------

	fun upsertFavorite(f: Favorite) {
		val stamped = f.copy(updatedAt = clock())
		val exists = current.favorites.any { it.id == f.id }
		commit(current.copy(favorites = if (exists) current.favorites.map { if (it.id == f.id) stamped else it } else current.favorites + stamped))
	}

	fun deleteFavorite(id: String) = commit(current.copy(
		favorites = current.favorites.filter { it.id != id },
		tombstones = current.tombstones + Tombstone(id, clock())
	))

	// ---- Depenses -----------------------------------------------------------

	fun upsertExpense(e: Expense) {
		val stamped = e.copy(createdAt = e.createdAt ?: clock(), updatedAt = clock())
		val exists = current.expenses.any { it.id == e.id }
		commit(current.copy(expenses = if (exists) current.expenses.map { if (it.id == e.id) stamped else it } else current.expenses + stamped))
	}

	fun deleteExpense(id: String) = commit(current.copy(
		expenses = current.expenses.filter { it.id != id },
		tombstones = current.tombstones + Tombstone(id, clock())
	))

	// ---- Reglages -----------------------------------------------------------

	fun updateSettings(s: AppSettings) = commit(current.copy(settings = s.copy(updatedAt = clock())))

	companion object {
		private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"

		/** Identifiant aleatoire de 20 caracteres : 36^20, pas de collision a craindre. */
		fun randomId(): String = buildString { repeat(20) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) } }
	}
}

/** Une souscription annulable, pour Swift, qui ne lit pas les StateFlow directement. */
class Subscription internal constructor(private val job: Job) {
	fun cancel() = job.cancel()
}

/**
 * Observation depuis SwiftUI : chaque changement est livre sur le fil
 * principal. Le modele Swift s'en sert pour republier dans un
 * ObservableObject.
 */
fun AppState.watch(onData: (AppData) -> Unit, onEntitlement: (Entitlement) -> Unit): Subscription {
	val job = SupervisorJob()
	val scope = CoroutineScope(Dispatchers.Main + job)
	scope.launch { data.collect { onData(it) } }
	scope.launch { entitlement.collect { onEntitlement(it) } }
	return Subscription(job)
}
