package com.hooliapps.kilometres.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.Feature
import com.hooliapps.kilometres.core.PlatformFiles
import com.hooliapps.kilometres.core.Entitlement
import com.hooliapps.kilometres.core.backup.BackupCodec
import com.hooliapps.kilometres.core.store.AppState
import com.hooliapps.kilometres.core.store.FileDataStore
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat

/**
 * Fenetre principale : un rail de navigation a gauche, l'ecran a droite,
 * comme une application de bureau et non un telephone etire. Les formulaires
 * s'ouvrent dans un panneau lateral.
 */
fun main() = application {
	val state = remember {
		AppState(FileDataStore(PlatformFiles(DesktopFiles.dataDirectory().absolutePath))).also {
			it.load()
			// Windows est Pro par construction : aucune boutique n'y vend
			// l'abonnement, et un carnet plafonne a cinq trajets sans moyen
			// de payer ne servirait a rien. L'application de bureau est le
			// complement d'un abonnement mobile (voir docs/STORES.md).
			if (!it.currentEntitlement.pro) it.setEntitlement(Entitlement(pro = true, productId = "windows"))
		}
	}
	Window(onCloseRequest = ::exitApplication, title = Strings["app_name"], icon = androidx.compose.ui.res.painterResource("icon-256.png"), state = rememberWindowState(width = 1100.dp, height = 760.dp)) {
		val dark = false
		MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(primary = Color(0xFF1F6F5F), primaryContainer = Color(0xFFCDEFE4))) {
			App(state)
		}
	}
}

enum class Tab { TRIPS, ESTIMATE, MORE }

@Composable
fun App(state: AppState) {
	var tab by remember { mutableStateOf(Tab.TRIPS) }
	var editing by remember { mutableStateOf<String?>(null) }   // identifiant du trajet en cours d'edition, "" pour un nouveau
	val snackbar = remember { SnackbarHostState() }
	val data by state.data.collectAsState()
	val entitlement by state.entitlement.collectAsState()
	val scope = rememberCoroutineScope()

	// Sauvegarde automatique dans Documents : le dossier est en general synchronise.
	androidx.compose.runtime.LaunchedEffect(data) {
		runCatching { File(DesktopFiles.documentsBackup(), "kilometres.${BackupCodec.FILE_EXTENSION}").writeText(BackupCodec.encode(data)) }
	}

	Scaffold(
		snackbarHost = { SnackbarHost(snackbar) },
		floatingActionButton = {
			if (tab == Tab.TRIPS && editing == null) {
				ExtendedFloatingActionButton(onClick = { editing = "" }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(Strings["add_trip"]) })
			}
		}
	) { padding ->
		Row(Modifier.fillMaxSize().padding(padding)) {
			NavigationRail {
				Spacer(Modifier.height(16.dp))
				NavigationRailItem(selected = tab == Tab.TRIPS, onClick = { tab = Tab.TRIPS; editing = null }, icon = { Icon(Icons.Filled.Route, null) }, label = { Text(Strings["tab_trips"]) })
				NavigationRailItem(selected = tab == Tab.ESTIMATE, onClick = { tab = Tab.ESTIMATE; editing = null }, icon = { Icon(Icons.Filled.Calculate, null) }, label = { Text(Strings["tab_estimate"]) })
				NavigationRailItem(selected = tab == Tab.MORE, onClick = { tab = Tab.MORE; editing = null }, icon = { Icon(Icons.Filled.MoreHoriz, null) }, label = { Text(Strings["tab_more"]) })
			}
			Box(Modifier.weight(1f).fillMaxSize()) {
				when (tab) {
					Tab.TRIPS -> TripsPanel(state, onEdit = { editing = it })
					Tab.ESTIMATE -> EstimatePanel(state, onMessage = { m -> scope.launch { snackbar.showSnackbar(m) } })
					Tab.MORE -> MorePanel(state, onMessage = { m -> scope.launch { snackbar.showSnackbar(m) } })
				}
			}
			editing?.let { id ->
				Column(Modifier.widthIn(min = 360.dp, max = 420.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
					TripForm(state, tripId = id.ifEmpty { null }, onDone = { editing = null }, onQuota = { m -> scope.launch { snackbar.showSnackbar(m) } })
				}
			}
		}
	}
}

@Composable
fun TripsPanel(state: AppState, onEdit: (String) -> Unit) {
	val data by state.data.collectAsState()
	val entitlement by state.entitlement.collectAsState()
	val today = Day.today()
	val nf = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
	val monthTrips = data.trips.filter { it.date.year == today.year && it.date.month == today.month }
	val grouped = data.trips.sortedByDescending { it.date }.groupBy { it.date }
	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
		Text(Strings["app_name"], style = MaterialTheme.typography.headlineLarge)
		Spacer(Modifier.height(16.dp))
		Row {
			Card(Modifier.weight(1f)) {
				Column(Modifier.padding(16.dp)) {
					Text(Strings["this_month"], style = MaterialTheme.typography.labelMedium)
					Text(Strings.format("km_value", nf.format(monthTrips.sumOf { it.totalKm })), style = MaterialTheme.typography.headlineSmall)
					Text(Strings.format("trips_count", monthTrips.size), style = MaterialTheme.typography.bodySmall)
				}
			}
			Spacer(Modifier.width(16.dp))
			Card(Modifier.weight(1f)) {
				Column(Modifier.padding(16.dp)) {
					Text(Strings["this_year"], style = MaterialTheme.typography.labelMedium)
					Text(Strings.format("km_value", nf.format(data.trips.filter { it.date.year == today.year }.sumOf { it.totalKm })), style = MaterialTheme.typography.headlineSmall)
				}
			}
		}
		if (!entitlement.isActive) {
			Spacer(Modifier.height(12.dp))
			val remaining = state.remainingFreeTrips(today)
			Card(colors = CardDefaults.cardColors(containerColor = if (remaining == 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)) {
				Text(if (remaining == 0) Strings["free_limit_reached"] else Strings.format("free_remaining", remaining), Modifier.padding(12.dp))
			}
		}
		if (data.favorites.isNotEmpty()) {
			Spacer(Modifier.height(12.dp))
			Text(Strings["use_favorite"], style = MaterialTheme.typography.labelLarge)
			Row {
				for (f in data.favorites) {
					OutlinedButton(onClick = {
						state.addTrip(com.hooliapps.kilometres.core.Trip(state.newId(), today, f.distanceKm, f.from, f.to, f.roundTrip, f.purpose, f.vehicleId ?: data.settings.defaultVehicleId))
					}, modifier = Modifier.padding(end = 8.dp)) { Text("${f.label} · ${nf.format(f.totalKm)} km") }
				}
			}
		}
		Spacer(Modifier.height(16.dp))
		if (data.trips.isEmpty()) Text(Strings["no_trips_yet"], style = MaterialTheme.typography.bodyLarge)
		for ((day, trips) in grouped) {
			Text(Fmt.day(day), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
			for (t in trips) {
				val vehicle = data.vehicleById(t.vehicleId ?: data.settings.defaultVehicleId)
				ListItem(
					headlineContent = { Text(listOf(t.from, t.to).filter { it.isNotEmpty() }.joinToString(" → ").ifEmpty { Fmt.purpose(t.purpose) }) },
					supportingContent = { Text(listOfNotNull(Fmt.purpose(t.purpose), if (t.roundTrip) Strings["field_round_trip"] else null, vehicle?.name).joinToString(" · ")) },
					trailingContent = { Text(Strings.format("km_value", nf.format(t.totalKm)), style = MaterialTheme.typography.titleMedium) },
					modifier = Modifier.fillMaxWidth().clickable { onEdit(t.id) }
				)
				HorizontalDivider()
			}
		}
		Spacer(Modifier.height(80.dp))
	}
}
