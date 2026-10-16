package com.hooliapps.kilometres.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.Favorite
import com.hooliapps.kilometres.core.Purpose
import com.hooliapps.kilometres.core.RewardedUnlock
import com.hooliapps.kilometres.core.Trip
import com.hooliapps.kilometres.core.TripSource
import com.hooliapps.kilometres.ui.AppViewModel
import com.hooliapps.kilometres.ui.Fmt
import com.hooliapps.kilometres.ui.LocalSnackbar
import com.hooliapps.kilometres.ui.Screen
import com.hooliapps.kilometres.ui.components.RewardedButton
import com.hooliapps.kilometres.ui.kmText
import com.hooliapps.kilometres.ui.purposeName
import kotlinx.coroutines.launch

@Composable
fun TripsScreen(vm: AppViewModel) {
	val data by vm.state.data.collectAsState()
	val entitlement by vm.state.entitlement.collectAsState()
	val today = Day.today()
	val monthTrips = data.trips.filter { it.date.year == today.year && it.date.month == today.month }
	val monthKm = monthTrips.sumOf { it.totalKm }
	val yearKm = data.trips.filter { it.date.year == today.year }.sumOf { it.totalKm }
	val grouped = data.trips.sortedWith(compareByDescending<Trip> { it.date }.thenByDescending { it.createdAt ?: 0 }).groupBy { it.date }
	val context = LocalContext.current

	LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
		item {
			Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(16.dp))
		}
		item {
			Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
				Row(Modifier.padding(16.dp)) {
					Column(Modifier.weight(1f)) {
						Text(stringResource(R.string.this_month), style = MaterialTheme.typography.labelMedium)
						Text(kmText(monthKm), style = MaterialTheme.typography.headlineSmall)
						Text(stringResource(R.string.trips_count, monthTrips.size), style = MaterialTheme.typography.bodySmall)
					}
					Column(Modifier.weight(1f)) {
						Text(stringResource(R.string.this_year), style = MaterialTheme.typography.labelMedium)
						Text(kmText(yearKm), style = MaterialTheme.typography.headlineSmall)
					}
				}
			}
		}
		if (!entitlement.isActive) {
			item {
				val remaining = vm.state.remainingFreeTrips(today)
				Card(
					colors = CardDefaults.cardColors(containerColor = if (remaining == 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
					modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clickable { vm.open(Screen.Pro) }
				) {
					Column(Modifier.padding(12.dp)) {
						Row(verticalAlignment = Alignment.CenterVertically) {
							Icon(Icons.Filled.Lock, contentDescription = null)
							Spacer(Modifier.width(8.dp))
							Text(if (remaining == 0) stringResource(R.string.free_limit_reached) else stringResource(R.string.free_remaining, remaining), Modifier.weight(1f))
							TextButton(onClick = { vm.open(Screen.Pro) }) { Text(stringResource(R.string.go_pro)) }
						}
						if (remaining == 0) RewardedButton(RewardedUnlock.FIVE_TRIPS)
					}
				}
			}
		}
		if (data.favorites.isNotEmpty()) {
			item {
				Column(Modifier.padding(horizontal = 16.dp)) {
					Text(stringResource(R.string.use_favorite), style = MaterialTheme.typography.labelLarge)
					val snackbar = LocalSnackbar.current
					val scope = rememberCoroutineScope()
					val added = stringResource(R.string.added)
					val undo = stringResource(R.string.undo)
					val quotaMsg = stringResource(R.string.free_quota_reached)
					LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						items(data.favorites) { f ->
							AssistChip(
								onClick = {
									val trip = quickTrip(vm, f)
									if (vm.state.addTrip(trip)) {
										scope.launch {
											if (snackbar.showSnackbar(added, actionLabel = undo) == SnackbarResult.ActionPerformed) vm.state.deleteTrip(trip.id)
										}
									} else {
										scope.launch { snackbar.showSnackbar(quotaMsg) }
										vm.open(Screen.Pro)
									}
								},
								leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null) },
								label = { Text("${f.label} · ${Fmt.km(f.totalKm)} km") }
							)
						}
					}
					Text(stringResource(R.string.quick_add_hint), style = MaterialTheme.typography.bodySmall)
				}
			}
		}
		if (data.trips.isEmpty()) {
			item {
				Text(stringResource(R.string.no_trips_yet), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
					modifier = Modifier.fillMaxWidth().padding(32.dp))
			}
		}
		for ((day, trips) in grouped) {
			item(key = "h${day.toIso()}") {
				Text(Fmt.dayLong(context, day), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp))
			}
			items(trips, key = { it.id }) { t -> TripRow(vm, t) }
		}
	}
}

private fun quickTrip(vm: AppViewModel, f: Favorite): Trip = Trip(
	id = vm.state.newId(), date = Day.today(), distanceKm = f.distanceKm, from = f.from, to = f.to,
	roundTrip = f.roundTrip, purpose = f.purpose, vehicleId = f.vehicleId ?: vm.state.current.settings.defaultVehicleId
)

@Composable
private fun TripRow(vm: AppViewModel, t: Trip) {
	val data by vm.state.data.collectAsState()
	val vehicle = data.vehicleById(t.vehicleId ?: data.settings.defaultVehicleId)
	val route = listOf(t.from, t.to).filter { it.isNotEmpty() }.joinToString(" → ")
	val subtitle = listOfNotNull(
		purposeName(t.purpose),
		if (t.roundTrip) stringResource(R.string.field_round_trip) else null,
		vehicle?.name,
		if (t.source == TripSource.IMPORTED) stringResource(R.string.import_title) else null
	).joinToString(" · ")
	ListItem(
		leadingContent = {
			Icon(when (t.purpose) {
				Purpose.COMMUTE -> Icons.Outlined.HomeWork
				Purpose.BUSINESS -> Icons.Outlined.Work
				Purpose.PERSONAL -> Icons.Outlined.Person
			}, contentDescription = null)
		},
		headlineContent = { Text(route.ifEmpty { purposeName(t.purpose) }) },
		supportingContent = { Text(subtitle) },
		trailingContent = { Text(kmText(t.totalKm), style = MaterialTheme.typography.titleMedium) },
		modifier = Modifier.clickable { vm.open(Screen.TripForm(tripId = t.id)) }
	)
}
