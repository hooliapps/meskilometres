package com.hooliapps.kilometres.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.core.Day
import java.time.LocalDate
import java.time.ZoneOffset

/** Un choix parmi quelques valeurs, en boutons segmentes. */
@Composable
fun <T> Choice(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
	SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
		options.forEachIndexed { i, o ->
			SegmentedButton(
				selected = o == selected,
				onClick = { onSelect(o) },
				shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size)
			) { Text(label(o)) }
		}
	}
}

/** Une liste deroulante simple, sans API experimentale. */
@Composable
fun <T> Picker(label: String, options: List<T>, selected: T?, text: (T) -> String, onSelect: (T) -> Unit) {
	var open by remember { mutableStateOf(false) }
	Box {
		ListItem(
			headlineContent = { Text(selected?.let(text) ?: "") },
			overlineContent = { Text(label) },
			trailingContent = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
			modifier = Modifier.clickable { open = true }
		)
		DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
			for (o in options) {
				DropdownMenuItem(text = { Text(text(o)) }, onClick = { onSelect(o); open = false })
			}
		}
	}
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, supporting: String? = null) {
	ListItem(
		headlineContent = { Text(label) },
		supportingContent = supporting?.let { { Text(it) } },
		trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
		modifier = Modifier.clickable { onChange(!checked) }
	)
}

/** Choix d'une date : une ligne qui ouvre le calendrier du systeme. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPicker(label: String, day: Day, onChange: (Day) -> Unit, text: (Day) -> String) {
	var open by remember { mutableStateOf(false) }
	ListItem(
		headlineContent = { Text(text(day)) },
		overlineContent = { Text(label) },
		modifier = Modifier.clickable { open = true }
	)
	if (open) {
		val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.of(day.year, day.month, day.day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
		DatePickerDialog(
			onDismissRequest = { open = false },
			confirmButton = {
				TextButton(onClick = {
					state.selectedDateMillis?.let { ms ->
						val d = java.time.Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
						onChange(Day(d.year, d.monthValue, d.dayOfMonth))
					}
					open = false
				}) { Text(stringResource(R.string.ok)) }
			},
			dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } }
		) { DatePicker(state = state) }
	}
}
