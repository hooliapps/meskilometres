package com.hooliapps.kilometres.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.Feature
import com.hooliapps.kilometres.core.RewardedUnlock
import com.hooliapps.kilometres.core.RuleSet
import com.hooliapps.kilometres.core.rules.EstimationNote
import com.hooliapps.kilometres.core.rules.Estimator
import com.hooliapps.kilometres.ui.AppViewModel
import com.hooliapps.kilometres.ui.Fmt
import com.hooliapps.kilometres.ui.Screen
import com.hooliapps.kilometres.ui.components.DisclaimerBanner
import com.hooliapps.kilometres.ui.components.KeyValueRow
import com.hooliapps.kilometres.ui.components.ProLockedCard
import com.hooliapps.kilometres.ui.components.RewardedButton
import com.hooliapps.kilometres.ui.kmText

@Composable
fun EstimateScreen(vm: AppViewModel) {
	val data by vm.state.data.collectAsState()
	val entitlement by vm.state.entitlement.collectAsState()
	val years = (setOf(Day.today().year) + data.trips.map { it.date.year }).sortedDescending()
	var yearChoice by remember { mutableStateOf<Int?>(null) }
	val year = yearChoice?.takeIf { it in years } ?: years.first()

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
		Text(stringResource(R.string.estimation), style = MaterialTheme.typography.headlineMedium)
		Spacer(Modifier.height(12.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			for (y in years) FilterChip(selected = y == year, onClick = { yearChoice = y }, label = { Text(stringResource(R.string.income_year, y)) })
		}
		Spacer(Modifier.height(12.dp))
		if (!entitlement.allows(Feature.ESTIMATION)) {
			ProLockedCard(rewarded = { RewardedButton(RewardedUnlock.ESTIMATION_DAY) })
		} else {
			EstimationBody(vm, year)
		}
	}
}

@Composable
private fun EstimationBody(vm: AppViewModel, year: Int) {
	val data by vm.state.data.collectAsState()
	val currency = data.settings.currency
	val e = Estimator.estimate(data, year)
	fun m(v: Double) = Fmt.money(v, currency)
	val notes = e.notes.map {
		when (it) {
			EstimationNote.SCALE_UNCONFIRMED -> stringResource(R.string.note_scale_unconfirmed)
			EstimationNote.COMMUTE_CAP_APPLIED -> stringResource(R.string.note_cap_applied)
			EstimationNote.EXTRA_COMMUTE_LEGS_DROPPED -> stringResource(R.string.note_extra_legs)
			EstimationNote.TRIPS_WITHOUT_VEHICLE -> stringResource(R.string.note_no_vehicle)
			EstimationNote.FUEL_INCLUDED_IN_SCALE -> stringResource(R.string.note_fuel, m(e.fuelIgnored))
			EstimationNote.NO_SALARY_FOR_COMPARISON -> stringResource(R.string.note_no_salary)
		}
	}

	DisclaimerBanner()
	Spacer(Modifier.height(12.dp))
	if (data.vehicles.isEmpty()) {
		Card(Modifier.fillMaxWidth()) {
			ListItem(
				leadingContent = { Icon(Icons.Outlined.DirectionsCar, contentDescription = null) },
				headlineContent = { Text(stringResource(R.string.no_vehicle)) },
				trailingContent = { TextButton(onClick = { vm.open(Screen.Vehicles) }) { Text(stringResource(R.string.vehicles)) } }
			)
		}
		Spacer(Modifier.height(8.dp))
	}
	Card(Modifier.fillMaxWidth()) {
		Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
			Text(m(e.total), style = MaterialTheme.typography.displaySmall)
			Text(stringResource(R.string.total), style = MaterialTheme.typography.labelLarge)
			HorizontalDivider(Modifier.padding(vertical = 12.dp))
			KeyValueRow(stringResource(R.string.commute_km_raw), kmText(e.commuteKmRaw))
			KeyValueRow(stringResource(R.string.commute_km_retained), kmText(e.commuteKmRetained))
			KeyValueRow(stringResource(R.string.business_km), kmText(e.businessKm))
			KeyValueRow(stringResource(R.string.allowance), m(e.allowanceTotal))
			KeyValueRow(stringResource(R.string.tolls_parking), m(e.tollsAndParking))
			KeyValueRow(stringResource(R.string.total), m(e.total), bold = true)
		}
	}
	if (e.ruleSet == RuleSet.FR) {
		Spacer(Modifier.height(8.dp))
		Card(Modifier.fillMaxWidth()) {
			Column(Modifier.padding(16.dp)) {
				Text(stringResource(R.string.flat_deduction), style = MaterialTheme.typography.titleMedium)
				Spacer(Modifier.height(8.dp))
				val flat = e.flatDeduction
				if (flat == null) {
					Text(stringResource(R.string.enter_salary))
					TextButton(onClick = { vm.open(Screen.Settings) }) { Text(stringResource(R.string.net_taxable_salary)) }
				} else {
					KeyValueRow(stringResource(R.string.flat_deduction), m(flat))
					KeyValueRow(stringResource(R.string.total), m(e.total))
					Spacer(Modifier.height(8.dp))
					val better = e.realCostsBetter == true
					Row(verticalAlignment = Alignment.CenterVertically) {
						Icon(if (better) Icons.Filled.ThumbUp else Icons.Filled.Balance, contentDescription = null)
						Spacer(Modifier.width(8.dp))
						Text(stringResource(if (better) R.string.real_better else R.string.flat_better))
					}
				}
			}
		}
	}
	if (e.byVehicle.isNotEmpty()) {
		Spacer(Modifier.height(8.dp))
		Card(Modifier.fillMaxWidth()) {
			Column(Modifier.padding(16.dp)) {
				Text(stringResource(R.string.per_vehicle), style = MaterialTheme.typography.titleMedium)
				for (v in e.byVehicle) KeyValueRow("${v.vehicle.name} · ${kmText(v.totalKm)}", m(v.allowance))
			}
		}
	}
	if (notes.isNotEmpty()) {
		Spacer(Modifier.height(8.dp))
		Card(Modifier.fillMaxWidth()) {
			Column(Modifier.padding(16.dp)) {
				for (n in notes) Text("• $n", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
			}
		}
	}
	Spacer(Modifier.height(12.dp))
	Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		OutlinedButton(onClick = { vm.open(Screen.Expenses) }, modifier = Modifier.weight(1f)) {
			Icon(Icons.Outlined.ReceiptLong, contentDescription = null)
			Text(" " + stringResource(R.string.expenses))
		}
		Button(onClick = { vm.open(Screen.Export(year)) }, modifier = Modifier.weight(1f)) {
			Icon(Icons.Outlined.IosShare, contentDescription = null)
			Text(" " + stringResource(R.string.export))
		}
	}
	Spacer(Modifier.height(16.dp))
	Text(stringResource(R.string.documents_title), style = MaterialTheme.typography.titleMedium)
	for (line in stringResource(R.string.documents_list).split('\n')) {
		ListItem(leadingContent = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) }, headlineContent = { Text(line) })
	}
	Spacer(Modifier.height(8.dp))
	DisclaimerBanner(long = true)
	Spacer(Modifier.height(32.dp))
}
