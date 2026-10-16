package com.hooliapps.kilometres.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.Favorite
import com.hooliapps.kilometres.core.Purpose
import com.hooliapps.kilometres.core.RewardedUnlock
import com.hooliapps.kilometres.core.Trip
import com.hooliapps.kilometres.ui.AppViewModel
import com.hooliapps.kilometres.ui.Fmt
import com.hooliapps.kilometres.ui.LocalSnackbar
import com.hooliapps.kilometres.ui.Screen
import com.hooliapps.kilometres.ui.components.Choice
import com.hooliapps.kilometres.ui.components.ConfirmDeleteDialog
import com.hooliapps.kilometres.ui.components.DayPicker
import com.hooliapps.kilometres.ui.components.Picker
import com.hooliapps.kilometres.ui.components.RewardedButton
import com.hooliapps.kilometres.ui.components.SwitchRow
import com.hooliapps.kilometres.ui.purposeName
import kotlinx.coroutines.launch

/** Saisie d'un trajet : la page la plus utilisee, donc la plus simple. */
@Composable
fun TripFormScreen(vm: AppViewModel, screen: Screen.TripForm) {
	val data = vm.state.current
	val existing = remember { screen.tripId?.let { id -> data.trips.firstOrNull { it.id == id } } }
	val favorite = remember { screen.favoriteId?.let { id -> data.favorites.firstOrNull { it.id == id } } }
	val settings = data.settings
	val context = LocalContext.current

	var date by remember { mutableStateOf(existing?.date ?: Day.today()) }
	var from by remember { mutableStateOf(existing?.from ?: favorite?.from ?: if (existing == null && favorite == null && settings.homeWorkDistanceKm != null) settings.homeLabel else "") }
	var to by remember { mutableStateOf(existing?.to ?: favorite?.to ?: if (existing == null && favorite == null && settings.homeWorkDistanceKm != null) settings.workLabel else "") }
	var distance by remember {
		mutableStateOf((existing?.distanceKm ?: favorite?.distanceKm ?: if (existing == null && favorite == null) settings.homeWorkDistanceKm else null)?.let(Fmt::number) ?: "")
	}
	var roundTrip by remember { mutableStateOf(existing?.roundTrip ?: favorite?.roundTrip ?: true) }
	var purpose by remember { mutableStateOf(existing?.purpose ?: favorite?.purpose ?: Purpose.COMMUTE) }
	var justified by remember { mutableStateOf(existing?.justified ?: false) }
	var vehicleId by remember { mutableStateOf(existing?.vehicleId ?: favorite?.vehicleId ?: settings.defaultVehicleId) }
	var note by remember { mutableStateOf(existing?.note ?: "") }
	var saveFavorite by remember { mutableStateOf(false) }
	var favoriteLabel by remember { mutableStateOf("") }
	var distanceError by remember { mutableStateOf<String?>(null) }
	var confirmDelete by remember { mutableStateOf(false) }
	val snackbar = LocalSnackbar.current
	val scope = rememberCoroutineScope()
	val quotaMsg = stringResource(R.string.free_quota_reached)
	val invalid = stringResource(R.string.invalid_distance)

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
		DayPicker(stringResource(R.string.field_date), date, { date = it }) { Fmt.day(it) }
		Spacer(Modifier.height(8.dp))
		Choice(Purpose.entries, purpose, { purposeName(it) }) { purpose = it }
		Spacer(Modifier.height(16.dp))
		Row(verticalAlignment = Alignment.CenterVertically) {
			OutlinedTextField(value = from, onValueChange = { from = it }, label = { Text(stringResource(R.string.field_from)) }, modifier = Modifier.weight(1f), singleLine = true)
			IconButton(onClick = { val f = from; from = to; to = f }) { Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.swap)) }
			OutlinedTextField(value = to, onValueChange = { to = it }, label = { Text(stringResource(R.string.field_to)) }, modifier = Modifier.weight(1f), singleLine = true)
		}
		Spacer(Modifier.height(16.dp))
		OutlinedTextField(
			value = distance, onValueChange = { distance = it; distanceError = null },
			label = { Text(stringResource(R.string.field_distance)) }, suffix = { Text("km") },
			isError = distanceError != null, supportingText = distanceError?.let { { Text(it) } },
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth()
		)
		SwitchRow(stringResource(R.string.field_round_trip), roundTrip, { roundTrip = it })
		if (data.vehicles.isNotEmpty()) {
			Picker(stringResource(R.string.field_vehicle), data.vehicles, data.vehicleById(vehicleId), { it.name }) { vehicleId = it.id }
		} else {
			Text(stringResource(R.string.vehicle_needed), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
		}
		if (purpose == Purpose.COMMUTE) {
			ListItem(
				headlineContent = { Text(stringResource(R.string.field_justified)) },
				supportingContent = { Text(stringResource(R.string.field_justified_help)) },
				leadingContent = { Checkbox(checked = justified, onCheckedChange = { justified = it }) }
			)
		}
		OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.field_note)) }, modifier = Modifier.fillMaxWidth())
		if (existing == null) {
			SwitchRow(stringResource(R.string.save_as_favorite), saveFavorite, { saveFavorite = it })
			if (saveFavorite) {
				OutlinedTextField(value = favoriteLabel, onValueChange = { favoriteLabel = it }, label = { Text(stringResource(R.string.favorite_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
			}
		}
		Spacer(Modifier.height(24.dp))
		Button(modifier = Modifier.fillMaxWidth(), onClick = {
			val km = Fmt.parseNumber(distance)
			if (km == null || km <= 0) {
				distanceError = invalid
				return@Button
			}
			val trip = (existing ?: Trip(id = vm.state.newId(), date = date, distanceKm = km)).copy(
				date = date, distanceKm = km, from = from.trim(), to = to.trim(), roundTrip = roundTrip, purpose = purpose,
				vehicleId = vehicleId, note = note.trim(), justified = purpose == Purpose.COMMUTE && justified
			)
			if (existing != null) {
				vm.state.updateTrip(trip)
			} else {
				if (!vm.state.addTrip(trip)) {
					scope.launch { snackbar.showSnackbar(quotaMsg) }
					vm.open(Screen.Pro)
					return@Button
				}
				if (saveFavorite) {
					val label = favoriteLabel.trim().ifEmpty { listOf(trip.from, trip.to).filter { it.isNotEmpty() }.joinToString(" → ") }.ifEmpty { context.getString(R.string.purpose_commute) }
					vm.state.upsertFavorite(Favorite(vm.state.newId(), label, trip.distanceKm, trip.from, trip.to, trip.roundTrip, trip.purpose, trip.vehicleId))
				}
			}
			vm.back()
		}) {
			Icon(Icons.Filled.Check, contentDescription = null)
			Text(" " + stringResource(R.string.save))
		}
		if (existing == null && !vm.state.isPro && !vm.state.canAddTrip(date)) {
			Spacer(Modifier.height(8.dp))
			RewardedButton(RewardedUnlock.FIVE_TRIPS)
		}
		if (existing != null) {
			Spacer(Modifier.height(8.dp))
			androidx.compose.material3.TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
				Icon(Icons.Filled.DeleteOutline, contentDescription = null)
				Text(" " + stringResource(R.string.delete))
			}
		}
		Spacer(Modifier.height(32.dp))
	}
	if (confirmDelete) {
		ConfirmDeleteDialog(onConfirm = { vm.state.deleteTrip(existing!!.id); confirmDelete = false; vm.back() }, onDismiss = { confirmDelete = false })
	}
}
