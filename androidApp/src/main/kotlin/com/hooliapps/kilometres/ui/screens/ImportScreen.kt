package com.hooliapps.kilometres.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.core.Feature
import com.hooliapps.kilometres.core.Platform
import com.hooliapps.kilometres.core.Purpose
import com.hooliapps.kilometres.core.importer.HistoryFormat
import com.hooliapps.kilometres.core.importer.ImportResult
import com.hooliapps.kilometres.core.importer.LocationHistoryParser
import com.hooliapps.kilometres.ui.AppViewModel
import com.hooliapps.kilometres.ui.Fmt
import com.hooliapps.kilometres.ui.LocalSnackbar
import com.hooliapps.kilometres.ui.components.Choice
import com.hooliapps.kilometres.ui.components.Picker
import com.hooliapps.kilometres.ui.components.ProLockedCard
import com.hooliapps.kilometres.ui.kmText
import com.hooliapps.kilometres.ui.purposeName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Import d'un historique de positions (Pro). Le fichier est lu en memoire, analyse, puis oublie. */
@Composable
fun ImportScreen(vm: AppViewModel) {
	val entitlement by vm.state.entitlement.collectAsState()
	if (!entitlement.allows(Feature.IMPORT_HISTORY)) {
		Box(Modifier.padding(16.dp)) { ProLockedCard() }
		return
	}
	val context = LocalContext.current
	val data by vm.state.data.collectAsState()
	val snackbar = LocalSnackbar.current
	val scope = rememberCoroutineScope()
	var result by remember { mutableStateOf<ImportResult?>(null) }
	var reading by remember { mutableStateOf(false) }
	var defaultPurpose by remember { mutableStateOf(Purpose.BUSINESS) }
	var vehicleId by remember { mutableStateOf(data.settings.defaultVehicleId) }
	val alreadyImported = remember { data.trips.mapNotNull { it.importKey }.toSet() }
	val doneFmt = stringResource(R.string.import_done)

	val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult
		reading = true
		scope.launch {
			val parsed = withContext(Dispatchers.IO) {
				val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
				LocationHistoryParser.parse(text)
			}
			result = parsed
			reading = false
		}
	}

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
		Text(stringResource(R.string.import_help))
		Spacer(Modifier.height(8.dp))
		Text(stringResource(R.string.import_commute_hint), style = MaterialTheme.typography.bodySmall)
		Spacer(Modifier.height(16.dp))
		Button(onClick = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) }, enabled = !reading, modifier = Modifier.fillMaxWidth()) {
			Icon(Icons.Filled.FolderOpen, contentDescription = null)
			Text(" " + stringResource(if (reading) R.string.import_reading else R.string.import_pick))
		}
		Spacer(Modifier.height(24.dp))
		val r = result ?: return@Column
		if (r.format == HistoryFormat.UNKNOWN) {
			Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(stringResource(R.string.import_unreadable), Modifier.padding(16.dp)) }
			return@Column
		}
		val newCount = r.trips.count { it.key !in alreadyImported }
		Card(Modifier.fillMaxWidth()) {
			Column(Modifier.padding(16.dp)) {
				Text(stringResource(R.string.import_found, r.trips.size), style = MaterialTheme.typography.titleMedium)
				if (r.trips.isNotEmpty()) {
					Text(stringResource(R.string.import_period, Fmt.day(Platform.localDayOf(r.trips.first().startEpochMillis)), Fmt.day(Platform.localDayOf(r.trips.last().startEpochMillis))))
					Text(kmText(r.trips.sumOf { it.distanceKm }))
				}
				if (r.skippedNonVehicle > 0) Text(stringResource(R.string.import_skipped, r.skippedNonVehicle), style = MaterialTheme.typography.bodySmall)
			}
		}
		if (r.trips.isEmpty()) return@Column
		Spacer(Modifier.height(16.dp))
		Text(stringResource(R.string.import_default_purpose), style = MaterialTheme.typography.labelLarge)
		Choice(Purpose.entries, defaultPurpose, { purposeName(it) }) { defaultPurpose = it }
		if (data.vehicles.isNotEmpty()) {
			Picker(stringResource(R.string.field_vehicle), data.vehicles, data.vehicleById(vehicleId), { it.name }) { vehicleId = it.id }
		}
		Spacer(Modifier.height(24.dp))
		Button(enabled = newCount > 0, modifier = Modifier.fillMaxWidth(), onClick = {
			val settings = vm.state.current.settings
			val trips = LocationHistoryParser.toTrips(r.trips, vm.state::newId, defaultPurpose, vehicleId, settings.homeLabel, settings.workLabel)
			val added = vm.state.addTrips(trips)
			scope.launch { snackbar.showSnackbar(doneFmt.format(added)) }
			vm.back()
		}) {
			Icon(Icons.Filled.DownloadDone, contentDescription = null)
			Text(" " + stringResource(R.string.import_confirm, newCount))
		}
		if (newCount == 0) Text(stringResource(R.string.import_nothing_new), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
	}
}
