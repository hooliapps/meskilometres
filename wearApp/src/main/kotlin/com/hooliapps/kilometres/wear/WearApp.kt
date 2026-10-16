package com.hooliapps.kilometres.wear

import android.app.Application
import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WearApp : Application()

/** Ce que le telephone a publie en dernier : cache en preferences, pour l'afficher hors connexion. */
data class WatchState(
	val pro: Boolean = false,
	val todayKm: Double = 0.0,
	val monthKm: Double = 0.0,
	val favorites: List<Favorite> = emptyList(),
	val received: Boolean = false
) {
	data class Favorite(val id: String, val label: String, val km: Double)
}

object WatchStore {
	private const val PREFS = "kilometres-watch"
	private val _state = MutableStateFlow(WatchState())
	val state: StateFlow<WatchState> = _state.asStateFlow()

	fun load(context: Context) {
		val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		if (!p.contains("monthKm")) return
		val ids = p.getString("ids", "")!!.split('\u0001').filter { it.isNotEmpty() }
		val labels = p.getString("labels", "")!!.split('\u0001')
		val kms = p.getString("kms", "")!!.split('\u0001')
		_state.value = WatchState(
			pro = p.getBoolean("pro", false),
			todayKm = p.getFloat("todayKm", 0f).toDouble(),
			monthKm = p.getFloat("monthKm", 0f).toDouble(),
			favorites = ids.indices.map { i -> WatchState.Favorite(ids[i], labels.getOrElse(i) { "" }, kms.getOrElse(i) { "0" }.toDoubleOrNull() ?: 0.0) },
			received = true
		)
	}

	fun update(context: Context, s: WatchState) {
		_state.value = s
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
			.putBoolean("pro", s.pro)
			.putFloat("todayKm", s.todayKm.toFloat())
			.putFloat("monthKm", s.monthKm.toFloat())
			.putString("ids", s.favorites.joinToString("\u0001") { it.id })
			.putString("labels", s.favorites.joinToString("\u0001") { it.label })
			.putString("kms", s.favorites.joinToString("\u0001") { it.km.toString() })
			.apply()
	}
}

/** Recoit l'etat publie par le telephone. */
class WearListenerService : WearableListenerService() {
	override fun onDataChanged(events: DataEventBuffer) {
		for (e in events) {
			if (e.type != DataEvent.TYPE_CHANGED || e.dataItem.uri.path != "/kilometres/state") continue
			val m = DataMapItem.fromDataItem(e.dataItem).dataMap
			val ids = m.getStringArrayList("favoriteIds") ?: arrayListOf()
			val labels = m.getStringArrayList("favoriteLabels") ?: arrayListOf()
			val kms = m.getStringArrayList("favoriteKm") ?: arrayListOf()
			WatchStore.update(this, WatchState(
				pro = m.getBoolean("pro", false),
				todayKm = m.getDouble("todayKm", 0.0),
				monthKm = m.getDouble("monthKm", 0.0),
				favorites = ids.indices.map { i -> WatchState.Favorite(ids[i], labels.getOrElse(i) { "" }, kms.getOrElse(i) { "0" }.toDoubleOrNull() ?: 0.0) },
				received = true
			))
		}
	}
}
