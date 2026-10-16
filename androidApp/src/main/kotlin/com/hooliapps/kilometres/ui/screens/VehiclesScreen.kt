package com.hooliapps.kilometres.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Moped
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.core.Vehicle
import com.hooliapps.kilometres.core.VehicleKind
import com.hooliapps.kilometres.ui.AppViewModel
import com.hooliapps.kilometres.ui.Screen
import com.hooliapps.kilometres.ui.components.Choice
import com.hooliapps.kilometres.ui.components.ConfirmDeleteDialog
import com.hooliapps.kilometres.ui.components.Picker
import com.hooliapps.kilometres.ui.components.SwitchRow
import com.hooliapps.kilometres.ui.vehicleKindName

@Composable
fun VehiclesScreen(vm: AppViewModel) {
	val data by vm.state.data.collectAsState()
	Box(Modifier.fillMaxSize()) {
		if (data.vehicles.isEmpty()) {
			Text(stringResource(R.string.no_vehicle), textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp))
		} else {
			Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
				for (v in data.vehicles) {
					ListItem(
						leadingContent = {
							Icon(when (v.kind) {
								VehicleKind.CAR -> Icons.Outlined.DirectionsCar
								VehicleKind.MOTORCYCLE -> Icons.Outlined.TwoWheeler
								VehicleKind.MOPED -> Icons.Outlined.Moped
							}, contentDescription = null)
						},
						headlineContent = { Text(v.name) },
						supportingContent = {
							Text(listOfNotNull(vehicleKindName(v.kind), if (v.kind != VehicleKind.MOPED) stringResource(R.string.cv, v.fiscalPower) else null, if (v.isElectric) stringResource(R.string.electric) else null).joinToString(" · "))
						},
						trailingContent = if (data.settings.defaultVehicleId == v.id) ({ SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.default_vehicle)) }) }) else null,
						modifier = Modifier.clickable { vm.open(Screen.VehicleForm(v.id)) }
					)
				}
			}
		}
		ExtendedFloatingActionButton(
			onClick = { vm.open(Screen.VehicleForm()) },
			icon = { Icon(Icons.Filled.Add, contentDescription = null) },
			text = { Text(stringResource(R.string.add_vehicle)) },
			modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
		)
	}
}

@Composable
fun VehicleFormScreen(vm: AppViewModel, id: String?) {
	val data = vm.state.current
	val existing = remember { id?.let { x -> data.vehicles.firstOrNull { it.id == x } } }
	var name by remember { mutableStateOf(existing?.name ?: "") }
	var kind by remember { mutableStateOf(existing?.kind ?: VehicleKind.CAR) }
	var power by remember { mutableStateOf(existing?.fiscalPower ?: 5) }
	var electric by remember { mutableStateOf(existing?.isElectric ?: false) }
	var default by remember { mutableStateOf(existing != null && data.settings.defaultVehicleId == existing.id) }
	var confirmDelete by remember { mutableStateOf(false) }

	val powers = when (kind) {
		VehicleKind.CAR -> listOf(3, 4, 5, 6, 7)
		VehicleKind.MOTORCYCLE -> listOf(2, 3, 6)
		VehicleKind.MOPED -> emptyList()
	}
	if (powers.isNotEmpty() && power !in powers) {
		power = when (kind) {
			VehicleKind.MOTORCYCLE -> if (power <= 2) 2 else if (power <= 5) 3 else 6
			else -> power.coerceIn(3, 7)
		}
	}

	@Composable
	fun powerLabel(p: Int): String = when (kind) {
		VehicleKind.CAR -> if (p == 7) stringResource(R.string.cv_or_more, 7) else stringResource(R.string.cv, p)
		VehicleKind.MOTORCYCLE -> when (p) { 2 -> stringResource(R.string.cv_or_less, 2); 6 -> stringResource(R.string.cv_or_more, 6); else -> stringResource(R.string.cv_range_3_5) }
		VehicleKind.MOPED -> ""
	}

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
		OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.vehicle_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
		Spacer(Modifier.height(16.dp))
		Choice(VehicleKind.entries, kind, { vehicleKindName(it) }) { kind = it }
		Spacer(Modifier.height(8.dp))
		if (powers.isNotEmpty()) {
			val labels = powers.associateWith { powerLabel(it) }
			Picker(stringResource(R.string.fiscal_power), powers, power, { labels[it] ?: "" }) { power = it }
		}
		SwitchRow(stringResource(R.string.electric), electric, { electric = it })
		SwitchRow(stringResource(R.string.default_vehicle), default, { default = it })
		Spacer(Modifier.height(24.dp))
		Button(modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank(), onClick = {
			val vid = existing?.id ?: vm.state.newId()
			vm.state.upsertVehicle(Vehicle(vid, name.trim(), kind, power, electric))
			if (default) vm.state.updateSettings(vm.state.current.settings.copy(defaultVehicleId = vid))
			vm.back()
		}) {
			Icon(Icons.Filled.Check, contentDescription = null)
			Text(" " + stringResource(R.string.save))
		}
		if (existing != null) {
			TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete)) }
		}
	}
	if (confirmDelete) ConfirmDeleteDialog(onConfirm = { vm.state.deleteVehicle(existing!!.id); vm.back() }, onDismiss = { confirmDelete = false })
}
