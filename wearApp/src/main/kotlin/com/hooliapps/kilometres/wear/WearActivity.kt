package com.hooliapps.kilometres.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.NumberFormat

class WearActivity : ComponentActivity(), DataClient.OnDataChangedListener {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		WatchStore.load(this)
		setContent { MaterialTheme { WatchScreen(onAdd = ::sendAdd) } }
	}

	override fun onResume() {
		super.onResume()
		Wearable.getDataClient(this).addListener(this)
	}

	override fun onPause() {
		Wearable.getDataClient(this).removeListener(this)
		super.onPause()
	}

	override fun onDataChanged(events: DataEventBuffer) {
		// Le service recoit aussi ; relire les preferences suffit.
		for (e in events) if (e.type == DataEvent.TYPE_CHANGED) WatchStore.load(this)
	}

	/** Envoie l'identifiant du favori au telephone, qui fait la saisie. Rend vrai si un telephone a repondu. */
	private suspend fun sendAdd(favoriteId: String): Boolean = withContext(Dispatchers.IO) {
		runCatching {
			val nodes = Wearable.getNodeClient(this@WearActivity).connectedNodes.await()
			val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() ?: return@runCatching false
			Wearable.getMessageClient(this@WearActivity).sendMessage(node.id, "/kilometres/add", favoriteId.toByteArray(Charsets.UTF_8)).await()
			true
		}.getOrDefault(false)
	}
}

@Composable
private fun WatchScreen(onAdd: suspend (String) -> Boolean) {
	val state by WatchStore.state.collectAsState()
	val scope = rememberCoroutineScope()
	var feedback by remember { mutableStateOf<String?>(null) }
	val nf = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
	val added = stringResource(R.string.watch_added)

	ScalingLazyColumn(modifier = Modifier.fillMaxWidth()) {
		item { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
		if (!state.pro) {
			item { Text(stringResource(R.string.widget_pro_needed), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp)) }
			return@ScalingLazyColumn
		}
		item { Text("${stringResource(R.string.today)} · ${stringResource(R.string.km_value, nf.format(state.todayKm))}", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
		item { Text("${stringResource(R.string.this_month)} · ${stringResource(R.string.km_value, nf.format(state.monthKm))}", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) }
		feedback?.let { item { Text(it, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
		if (state.favorites.isEmpty()) {
			item { Text(stringResource(R.string.watch_no_favorites), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp)) }
		} else {
			item { Text(stringResource(R.string.watch_quick_add), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
			items(state.favorites) { f ->
				Button(onClick = {
					scope.launch { feedback = if (onAdd(f.id)) "$added · ${f.label}" else null }
				}, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
					Text("${f.label} · ${nf.format(f.km)} km")
				}
			}
		}
	}
}
