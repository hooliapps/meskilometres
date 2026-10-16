package com.hooliapps.kilometres.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.hooliapps.kilometres.Graph
import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.Feature
import com.hooliapps.kilometres.core.Trip

/**
 * Montre (Pro) : le telephone publie ses favoris et le total du mois sur la
 * couche de donnees ; la montre renvoie l'identifiant d'un favori a saisir.
 * Aucun serveur : tout passe par la liaison locale entre les deux appareils.
 */
object WearSync {
	const val PATH_STATE = "/kilometres/state"
	const val PATH_ADD = "/kilometres/add"
	private const val TAG = "WearSync"

	fun publish(context: Context, state: com.hooliapps.kilometres.core.store.AppState) {
		val data = state.current
		val today = Day.today()
		val monthKm = data.trips.filter { it.date.year == today.year && it.date.month == today.month }.sumOf { it.totalKm }
		val todayKm = data.trips.filter { it.date == today }.sumOf { it.totalKm }
		val request = PutDataMapRequest.create(PATH_STATE).apply {
			dataMap.putBoolean("pro", state.entitlement.value.allows(Feature.WATCH))
			dataMap.putDouble("monthKm", monthKm)
			dataMap.putDouble("todayKm", todayKm)
			dataMap.putStringArrayList("favoriteIds", ArrayList(data.favorites.map { it.id }))
			dataMap.putStringArrayList("favoriteLabels", ArrayList(data.favorites.map { it.label }))
			dataMap.putStringArrayList("favoriteKm", ArrayList(data.favorites.map { it.totalKm.toString() }))
			dataMap.putLong("at", System.currentTimeMillis())
		}.asPutDataRequest().setUrgent()
		runCatching {
			val client: DataClient = Wearable.getDataClient(context)
			client.putDataItem(request).addOnFailureListener { Log.i(TAG, "pas de montre : ${it.message}") }
		}
	}
}

/** Recoit « ajoute ce favori » depuis la montre. */
class PhoneWearListenerService : WearableListenerService() {
	override fun onMessageReceived(event: MessageEvent) {
		if (event.path != WearSync.PATH_ADD) return
		val state = Graph.state
		if (!state.entitlement.value.allows(Feature.WATCH)) return
		val favoriteId = String(event.data, Charsets.UTF_8)
		val f = state.current.favorites.firstOrNull { it.id == favoriteId } ?: return
		state.addTrip(Trip(
			id = state.newId(), date = Day.today(), distanceKm = f.distanceKm, from = f.from, to = f.to,
			roundTrip = f.roundTrip, purpose = f.purpose, vehicleId = f.vehicleId ?: state.current.settings.defaultVehicleId
		))
	}
}
