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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.Expense
import com.hooliapps.kilometres.core.ExpenseKind
import com.hooliapps.kilometres.core.ExpenseSource
import com.hooliapps.kilometres.core.Feature
import com.hooliapps.kilometres.ui.AppViewModel
import com.hooliapps.kilometres.ui.Fmt
import com.hooliapps.kilometres.ui.Screen
import com.hooliapps.kilometres.ui.components.Choice
import com.hooliapps.kilometres.ui.components.ConfirmDeleteDialog
import com.hooliapps.kilometres.ui.components.DayPicker
import com.hooliapps.kilometres.ui.components.ProLockedCard
import com.hooliapps.kilometres.ui.expenseKindName

/** Peages, parkings, carburant (Pro). */
@Composable
fun ExpensesScreen(vm: AppViewModel) {
	val data by vm.state.data.collectAsState()
	val entitlement by vm.state.entitlement.collectAsState()
	if (!entitlement.allows(Feature.EXPENSES)) {
		Box(Modifier.padding(16.dp)) { ProLockedCard() }
		return
	}
	val expenses = data.expenses.sortedByDescending { it.date }
	Box(Modifier.fillMaxSize()) {
		if (expenses.isEmpty()) {
			Text(stringResource(R.string.no_expenses), textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp))
		} else {
			Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
				for (e in expenses) {
					ListItem(
						leadingContent = {
							Icon(when (e.kind) {
								ExpenseKind.TOLL -> Icons.Outlined.Toll
								ExpenseKind.PARKING -> Icons.Outlined.LocalParking
								ExpenseKind.FUEL -> Icons.Outlined.LocalGasStation
								ExpenseKind.OTHER -> Icons.Outlined.Receipt
							}, contentDescription = null)
						},
						headlineContent = { Text(listOf(expenseKindName(e.kind), e.place).filter { it.isNotEmpty() }.joinToString(" · ")) },
						supportingContent = { Text(Fmt.day(e.date)) },
						trailingContent = { Text(Fmt.money(e.amount, e.currency.ifEmpty { data.settings.currency }), style = MaterialTheme.typography.titleMedium) },
						modifier = Modifier.clickable { vm.open(Screen.ExpenseForm(e.id)) }
					)
				}
			}
		}
		ExtendedFloatingActionButton(
			onClick = { vm.open(Screen.ExpenseForm()) },
			icon = { Icon(Icons.Filled.Add, contentDescription = null) },
			text = { Text(stringResource(R.string.add_expense)) },
			modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
		)
	}
}

@Composable
fun ExpenseFormScreen(vm: AppViewModel, id: String?) {
	val data = vm.state.current
	val existing = remember { id?.let { x -> data.expenses.firstOrNull { it.id == x } } }
	var date by remember { mutableStateOf(existing?.date ?: Day.today()) }
	var kind by remember { mutableStateOf(existing?.kind ?: ExpenseKind.TOLL) }
	var amount by remember { mutableStateOf(existing?.amount?.let { "%.2f".format(it) } ?: "") }
	var place by remember { mutableStateOf(existing?.place ?: "") }
	var litres by remember { mutableStateOf(existing?.litres?.let(Fmt::number) ?: "") }
	var odometer by remember { mutableStateOf(existing?.odometerKm?.let(Fmt::number) ?: "") }
	var amountError by remember { mutableStateOf<String?>(null) }
	var confirmDelete by remember { mutableStateOf(false) }
	val invalid = stringResource(R.string.invalid_amount)

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
		DayPicker(stringResource(R.string.field_date), date, { date = it }) { Fmt.day(it) }
		Spacer(Modifier.height(8.dp))
		Choice(ExpenseKind.entries, kind, { expenseKindName(it) }) { kind = it }
		Spacer(Modifier.height(16.dp))
		OutlinedTextField(
			value = amount, onValueChange = { amount = it; amountError = null }, label = { Text(stringResource(R.string.amount)) },
			suffix = { Text(data.settings.currency) }, isError = amountError != null, supportingText = amountError?.let { { Text(it) } },
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth()
		)
		Spacer(Modifier.height(8.dp))
		OutlinedTextField(value = place, onValueChange = { place = it }, label = { Text(stringResource(R.string.place)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
		if (kind == ExpenseKind.FUEL) {
			Spacer(Modifier.height(8.dp))
			OutlinedTextField(value = litres, onValueChange = { litres = it }, label = { Text(stringResource(R.string.litres)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
			Spacer(Modifier.height(8.dp))
			OutlinedTextField(value = odometer, onValueChange = { odometer = it }, label = { Text(stringResource(R.string.odometer)) }, suffix = { Text("km") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
		}
		Spacer(Modifier.height(24.dp))
		Button(modifier = Modifier.fillMaxWidth(), onClick = {
			val a = Fmt.parseNumber(amount)
			if (a == null || a <= 0) {
				amountError = invalid
				return@Button
			}
			vm.state.upsertExpense(Expense(
				id = existing?.id ?: vm.state.newId(), date = date, kind = kind, amount = a, currency = data.settings.currency, place = place.trim(),
				litres = if (kind == ExpenseKind.FUEL) Fmt.parseNumber(litres) else null,
				odometerKm = if (kind == ExpenseKind.FUEL) Fmt.parseNumber(odometer) else null,
				source = existing?.source ?: ExpenseSource.MANUAL, createdAt = existing?.createdAt
			))
			vm.back()
		}) {
			Icon(Icons.Filled.Check, contentDescription = null)
			Text(" " + stringResource(R.string.save))
		}
		if (existing != null) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete)) }
	}
	if (confirmDelete) ConfirmDeleteDialog(onConfirm = { vm.state.deleteExpense(existing!!.id); vm.back() }, onDismiss = { confirmDelete = false })
}
