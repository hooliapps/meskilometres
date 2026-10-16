package com.hooliapps.kilometres.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Button
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
import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.Feature
import com.hooliapps.kilometres.core.RewardedUnlock
import com.hooliapps.kilometres.core.rules.Estimator
import com.hooliapps.kilometres.export.Exporter
import com.hooliapps.kilometres.ui.AppViewModel
import com.hooliapps.kilometres.ui.Fmt
import com.hooliapps.kilometres.ui.LocalSnackbar
import com.hooliapps.kilometres.ui.components.Choice
import com.hooliapps.kilometres.ui.components.DayPicker
import com.hooliapps.kilometres.ui.components.Picker
import com.hooliapps.kilometres.ui.components.ProLockedCard
import com.hooliapps.kilometres.ui.components.RewardedButton
import com.hooliapps.kilometres.ui.components.SwitchRow
import kotlinx.coroutines.launch

private enum class Format { PDF, XLSX, CSV }

/** Export PDF, tableur ou CSV (Pro, ou un export contre une annonce). */
@Composable
fun ExportScreen(vm: AppViewModel, initialYear: Int?) {
	val entitlement by vm.state.entitlement.collectAsState()
	if (!entitlement.allows(Feature.EXPORT)) {
		Box(Modifier.padding(16.dp)) { ProLockedCard(rewarded = { RewardedButton(RewardedUnlock.EXPORT_HOUR) }) }
		return
	}
	val context = LocalContext.current
	val data by vm.state.data.collectAsState()
	val snackbar = LocalSnackbar.current
	val scope = rememberCoroutineScope()
	val years = (setOf(Day.today().year) + data.trips.map { it.date.year }).sortedDescending()
	var format by remember { mutableStateOf(Format.PDF) }
	var year by remember { mutableStateOf(initialYear?.takeIf { it in years } ?: years.first()) }
	var wholeYear by remember { mutableStateOf(true) }
	var from by remember { mutableStateOf(Day(year, 1, 1)) }
	var to by remember { mutableStateOf(Day(year, 12, 31)) }
	var includeEstimation by remember { mutableStateOf(true) }
	var busy by remember { mutableStateOf(false) }
	val failed = stringResource(R.string.export_failed)

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
		Choice(Format.entries, format, {
			when (it) {
				Format.PDF -> stringResource(R.string.export_pdf)
				Format.XLSX -> stringResource(R.string.export_xlsx)
				Format.CSV -> stringResource(R.string.export_csv)
			}
		}) { format = it }
		Spacer(Modifier.height(24.dp))
		Text(stringResource(R.string.export_period), style = MaterialTheme.typography.titleMedium)
		SwitchRow(stringResource(R.string.year), wholeYear, { wholeYear = it })
		if (wholeYear) {
			Picker(stringResource(R.string.year), years, year, { it.toString() }) { year = it }
		} else {
			Row {
				Box(Modifier.weight(1f)) { DayPicker(stringResource(R.string.export_from), from, { from = it }) { Fmt.day(it) } }
				Box(Modifier.weight(1f)) { DayPicker(stringResource(R.string.export_to), to, { to = it }) { Fmt.day(it) } }
			}
		}
		SwitchRow(stringResource(R.string.export_include_estimation), includeEstimation, { includeEstimation = it })
		Spacer(Modifier.height(16.dp))
		Text(stringResource(R.string.export_share), style = MaterialTheme.typography.bodySmall)
		Spacer(Modifier.height(16.dp))
		Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
			busy = true
			scope.launch {
				val f = if (wholeYear) Day(year, 1, 1) else from
				val t = if (wholeYear) Day(year, 12, 31) else to
				val estimation = if (includeEstimation) Estimator.estimate(data, f.year) else null
				val ok = runCatching {
					when (format) {
						Format.PDF -> Exporter.sharePdf(context, data, f, t, estimation)
						Format.XLSX -> Exporter.shareXlsx(context, data, f, t, estimation)
						Format.CSV -> Exporter.shareCsv(context, data, f, t, estimation)
					}
				}.isSuccess
				if (!ok) snackbar.showSnackbar(failed)
				busy = false
			}
		}) {
			Icon(Icons.Outlined.IosShare, contentDescription = null)
			Text(" " + stringResource(R.string.export))
		}
	}
}
