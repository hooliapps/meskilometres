package com.hooliapps.kilometres.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.core.RuleSet
import com.hooliapps.kilometres.ui.AppViewModel
import com.hooliapps.kilometres.ui.Fmt
import com.hooliapps.kilometres.ui.components.Choice
import com.hooliapps.kilometres.ui.components.Picker

@Composable
fun SettingsScreen(vm: AppViewModel) {
	val data by vm.state.data.collectAsState()
	val s0 = remember { vm.state.current.settings }
	var rate by remember { mutableStateOf(Fmt.number(s0.genericRatePerKm)) }
	var currency by remember { mutableStateOf(s0.currency) }
	var salary by remember { mutableStateOf(s0.netTaxableSalary?.let(Fmt::number) ?: "") }
	var home by remember { mutableStateOf(s0.homeLabel) }
	var work by remember { mutableStateOf(s0.workLabel) }
	var homeWork by remember { mutableStateOf(s0.homeWorkDistanceKm?.let(Fmt::number) ?: "") }

	fun save() {
		val s = vm.state.current.settings
		vm.state.updateSettings(s.copy(
			genericRatePerKm = Fmt.parseNumber(rate) ?: s.genericRatePerKm,
			currency = currency.trim().uppercase().ifEmpty { s.currency },
			netTaxableSalary = Fmt.parseNumber(salary),
			homeLabel = home.trim(), workLabel = work.trim(),
			homeWorkDistanceKm = Fmt.parseNumber(homeWork)
		))
	}
	// Les champs texte s'enregistrent en quittant l'ecran, les choix tout de suite.
	DisposableEffect(Unit) { onDispose { save() } }

	val s = data.settings
	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
		Text(stringResource(R.string.rules_title), style = MaterialTheme.typography.titleMedium)
		Spacer(Modifier.height(8.dp))
		Choice(RuleSet.entries, s.ruleSet, { stringResource(if (it == RuleSet.FR) R.string.rule_fr else R.string.rule_generic) }) { vm.state.updateSettings(s.copy(ruleSet = it)) }
		Spacer(Modifier.height(12.dp))
		if (s.ruleSet == RuleSet.GENERIC) {
			OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text(stringResource(R.string.generic_rate)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
			Spacer(Modifier.height(8.dp))
		}
		OutlinedTextField(value = currency, onValueChange = { currency = it.take(3) }, label = { Text(stringResource(R.string.currency)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
		if (s.ruleSet == RuleSet.FR) {
			Spacer(Modifier.height(8.dp))
			OutlinedTextField(value = salary, onValueChange = { salary = it }, label = { Text(stringResource(R.string.net_taxable_salary)) }, supportingText = { Text(stringResource(R.string.enter_salary)) },
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
		}
		HorizontalDivider(Modifier.padding(vertical = 24.dp))
		Text(stringResource(R.string.places_help), style = MaterialTheme.typography.bodySmall)
		Spacer(Modifier.height(8.dp))
		OutlinedTextField(value = home, onValueChange = { home = it }, label = { Text(stringResource(R.string.home_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
		Spacer(Modifier.height(8.dp))
		OutlinedTextField(value = work, onValueChange = { work = it }, label = { Text(stringResource(R.string.work_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
		Spacer(Modifier.height(8.dp))
		OutlinedTextField(value = homeWork, onValueChange = { homeWork = it }, label = { Text(stringResource(R.string.home_work_distance)) }, suffix = { Text("km") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
		if (data.vehicles.isNotEmpty()) {
			HorizontalDivider(Modifier.padding(vertical = 24.dp))
			Picker(stringResource(R.string.default_vehicle), data.vehicles, data.vehicleById(s.defaultVehicleId), { it.name }) { vm.state.updateSettings(s.copy(defaultVehicleId = it.id)) }
		}
		Spacer(Modifier.height(32.dp))
	}
}
