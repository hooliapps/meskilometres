package com.hooliapps.kilometres.desktop

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.ExpenseKind
import com.hooliapps.kilometres.core.Favorite
import com.hooliapps.kilometres.core.Feature
import com.hooliapps.kilometres.core.Purpose
import com.hooliapps.kilometres.core.RuleSet
import com.hooliapps.kilometres.core.Trip
import com.hooliapps.kilometres.core.Vehicle
import com.hooliapps.kilometres.core.VehicleKind
import com.hooliapps.kilometres.core.backup.BackupCodec
import com.hooliapps.kilometres.core.export.CsvExport
import com.hooliapps.kilometres.core.export.ExportLabels
import com.hooliapps.kilometres.core.export.Report
import com.hooliapps.kilometres.core.export.ReportDocument
import com.hooliapps.kilometres.core.export.XlsxExport
import com.hooliapps.kilometres.core.importer.LocationHistoryParser
import com.hooliapps.kilometres.core.rules.Estimation
import com.hooliapps.kilometres.core.rules.EstimationNote
import com.hooliapps.kilometres.core.rules.Estimator
import com.hooliapps.kilometres.core.store.AppState
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Formats, dans la langue de la session. */
object Fmt {
	private val nf = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
	fun km(v: Double): String = nf.format(v)
	fun money(v: Double, currency: String): String = NumberFormat.getCurrencyInstance().apply { runCatching { this.currency = java.util.Currency.getInstance(currency) } }.format(v)
	fun day(d: Day): String = LocalDate.of(d.year, d.month, d.day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
	fun purpose(p: Purpose): String = Strings[when (p) { Purpose.COMMUTE -> "purpose_commute"; Purpose.BUSINESS -> "purpose_business"; Purpose.PERSONAL -> "purpose_personal" }]
	fun kind(k: ExpenseKind): String = Strings[when (k) { ExpenseKind.TOLL -> "kind_toll"; ExpenseKind.PARKING -> "kind_parking"; ExpenseKind.FUEL -> "kind_fuel"; ExpenseKind.OTHER -> "kind_other" }]
	fun number(v: Double): String = if (v == Math.rint(v)) v.toLong().toString() else v.toString()
	fun parse(s: String): Double? = s.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()
}

@Composable
fun TripForm(state: AppState, tripId: String?, onDone: () -> Unit, onQuota: (String) -> Unit) {
	val data = state.current
	val existing = remember(tripId) { tripId?.let { id -> data.trips.firstOrNull { it.id == id } } }
	var date by remember(tripId) { mutableStateOf((existing?.date ?: Day.today()).toIso()) }
	var from by remember(tripId) { mutableStateOf(existing?.from ?: data.settings.homeLabel) }
	var to by remember(tripId) { mutableStateOf(existing?.to ?: data.settings.workLabel) }
	var distance by remember(tripId) { mutableStateOf((existing?.distanceKm ?: data.settings.homeWorkDistanceKm)?.let(Fmt::number) ?: "") }
	var roundTrip by remember(tripId) { mutableStateOf(existing?.roundTrip ?: true) }
	var purpose by remember(tripId) { mutableStateOf(existing?.purpose ?: Purpose.COMMUTE) }
	var justified by remember(tripId) { mutableStateOf(existing?.justified ?: false) }
	var vehicleId by remember(tripId) { mutableStateOf(existing?.vehicleId ?: data.settings.defaultVehicleId) }
	var note by remember(tripId) { mutableStateOf(existing?.note ?: "") }
	var saveFavorite by remember(tripId) { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }

	Text(Strings[if (existing == null) "trip_new" else "trip_edit"], style = MaterialTheme.typography.titleLarge)
	Spacer(Modifier.height(12.dp))
	OutlinedTextField(date, { date = it }, label = { Text(Strings["field_date"]) }, singleLine = true, modifier = Modifier.fillMaxWidth(), supportingText = { Text("AAAA-MM-JJ") })
	Row { for (p in Purpose.entries) FilterChip(selected = purpose == p, onClick = { purpose = p }, label = { Text(Fmt.purpose(p)) }, modifier = Modifier.padding(end = 6.dp)) }
	OutlinedTextField(from, { from = it }, label = { Text(Strings["field_from"]) }, singleLine = true, modifier = Modifier.fillMaxWidth())
	OutlinedTextField(to, { to = it }, label = { Text(Strings["field_to"]) }, singleLine = true, modifier = Modifier.fillMaxWidth())
	OutlinedTextField(distance, { distance = it; error = null }, label = { Text(Strings["field_distance"]) }, singleLine = true, isError = error != null, supportingText = error?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth())
	Row(verticalAlignment = Alignment.CenterVertically) { Switch(roundTrip, { roundTrip = it }); Spacer(Modifier.width(8.dp)); Text(Strings["field_round_trip"]) }
	if (data.vehicles.isNotEmpty()) {
		Text(Strings["field_vehicle"], style = MaterialTheme.typography.labelMedium)
		Row { for (v in data.vehicles) FilterChip(selected = vehicleId == v.id, onClick = { vehicleId = v.id }, label = { Text(v.name) }, modifier = Modifier.padding(end = 6.dp)) }
	}
	if (purpose == Purpose.COMMUTE) {
		Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(justified, { justified = it }); Text(Strings["field_justified"]) }
		Text(Strings["field_justified_help"], style = MaterialTheme.typography.bodySmall)
	}
	OutlinedTextField(note, { note = it }, label = { Text(Strings["field_note"]) }, modifier = Modifier.fillMaxWidth())
	if (existing == null) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(saveFavorite, { saveFavorite = it }); Text(Strings["save_as_favorite"]) }
	Spacer(Modifier.height(12.dp))
	Row {
		Button(onClick = {
			val km = Fmt.parse(distance)
			val d = Day.parseOrNull(date)
			if (km == null || km <= 0 || d == null) { error = Strings["invalid_distance"]; return@Button }
			val trip = (existing ?: Trip(state.newId(), d, km)).copy(date = d, distanceKm = km, from = from.trim(), to = to.trim(), roundTrip = roundTrip, purpose = purpose, vehicleId = vehicleId, note = note.trim(), justified = purpose == Purpose.COMMUTE && justified)
			if (existing != null) state.updateTrip(trip) else {
				if (!state.addTrip(trip)) { onQuota(Strings["free_quota_reached"]); return@Button }
				if (saveFavorite) state.upsertFavorite(Favorite(state.newId(), listOf(trip.from, trip.to).filter { it.isNotEmpty() }.joinToString(" → ").ifEmpty { Fmt.purpose(purpose) }, trip.distanceKm, trip.from, trip.to, trip.roundTrip, trip.purpose, trip.vehicleId))
			}
			onDone()
		}) { Text(Strings["save"]) }
		Spacer(Modifier.width(8.dp))
		TextButton(onClick = onDone) { Text(Strings["cancel"]) }
		if (existing != null) { Spacer(Modifier.width(8.dp)); TextButton(onClick = { state.deleteTrip(existing.id); onDone() }) { Text(Strings["delete"]) } }
	}
}

@Composable
fun ProLocked() {
	Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
		Column(Modifier.padding(16.dp)) {
			Text(Strings["pro_title"], style = MaterialTheme.typography.titleMedium)
			Spacer(Modifier.height(8.dp))
			Text(Strings["estimation_locked"])
			Spacer(Modifier.height(8.dp))
			Text(Strings["pro_unavailable"], style = MaterialTheme.typography.bodySmall)
		}
	}
}

@Composable
fun EstimatePanel(state: AppState, onMessage: (String) -> Unit) {
	val data by state.data.collectAsState()
	val entitlement by state.entitlement.collectAsState()
	val years = (setOf(Day.today().year) + data.trips.map { it.date.year }).sortedDescending()
	var year by remember { mutableStateOf(years.first()) }
	if (year !in years) year = years.first()
	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
		Text(Strings["estimation"], style = MaterialTheme.typography.headlineMedium)
		Spacer(Modifier.height(12.dp))
		Row { for (y in years) FilterChip(selected = y == year, onClick = { year = y }, label = { Text(Strings.format("income_year", y)) }, modifier = Modifier.padding(end = 6.dp)) }
		Spacer(Modifier.height(12.dp))
		if (!entitlement.allows(Feature.ESTIMATION)) { ProLocked(); return@Column }
		val e = Estimator.estimate(data, year)
		val cur = data.settings.currency
		Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) { Text(Strings["disclaimer_short"], Modifier.padding(12.dp)) }
		Spacer(Modifier.height(12.dp))
		Card(Modifier.fillMaxWidth()) {
			Column(Modifier.padding(16.dp)) {
				Text(Fmt.money(e.total, cur), style = MaterialTheme.typography.displaySmall)
				Text(Strings["total"], style = MaterialTheme.typography.labelLarge)
				HorizontalDivider(Modifier.padding(vertical = 12.dp))
				KV(Strings["commute_km_raw"], Strings.format("km_value", Fmt.km(e.commuteKmRaw)))
				KV(Strings["commute_km_retained"], Strings.format("km_value", Fmt.km(e.commuteKmRetained)))
				KV(Strings["business_km"], Strings.format("km_value", Fmt.km(e.businessKm)))
				KV(Strings["allowance"], Fmt.money(e.allowanceTotal, cur))
				KV(Strings["tolls_parking"], Fmt.money(e.tollsAndParking, cur))
				KV(Strings["total"], Fmt.money(e.total, cur))
				e.flatDeduction?.let { KV(Strings["flat_deduction"], Fmt.money(it, cur)); Text(Strings[if (e.realCostsBetter == true) "real_better" else "flat_better"]) }
				for (n in e.notes) Text("• " + Strings[when (n) {
					EstimationNote.SCALE_UNCONFIRMED -> "note_scale_unconfirmed"; EstimationNote.COMMUTE_CAP_APPLIED -> "note_cap_applied"
					EstimationNote.EXTRA_COMMUTE_LEGS_DROPPED -> "note_extra_legs"; EstimationNote.TRIPS_WITHOUT_VEHICLE -> "note_no_vehicle"
					EstimationNote.FUEL_INCLUDED_IN_SCALE -> "note_fuel"; EstimationNote.NO_SALARY_FOR_COMPARISON -> "note_no_salary"
				}].replace("%1\$s", Fmt.money(e.fuelIgnored, cur)), style = MaterialTheme.typography.bodySmall)
			}
		}
		Spacer(Modifier.height(12.dp))
		Row {
			Button(onClick = { export(state, year, "pdf", e, onMessage) }) { Text(Strings["export_pdf"]) }
			Spacer(Modifier.width(8.dp))
			OutlinedButton(onClick = { export(state, year, "xlsx", e, onMessage) }) { Text(Strings["export_xlsx"]) }
			Spacer(Modifier.width(8.dp))
			OutlinedButton(onClick = { export(state, year, "csv", e, onMessage) }) { Text(Strings["export_csv"]) }
		}
		Spacer(Modifier.height(16.dp))
		Text(Strings["disclaimer_long"], style = MaterialTheme.typography.bodySmall)
	}
}

@Composable
private fun KV(k: String, v: String) {
	Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(k, Modifier.weight(1f)); Text(v) }
}

private fun labels(data: com.hooliapps.kilometres.core.AppData, e: Estimation?): ExportLabels = ExportLabels(
	appName = Strings["app_name"], reportTitle = Strings["report_title"], period = Strings["export_period"], date = Strings["field_date"], from = Strings["field_from"], to = Strings["field_to"],
	purpose = Strings["field_purpose"], vehicle = Strings["field_vehicle"], roundTrip = Strings["field_round_trip"], distanceKm = "km", note = Strings["field_note"], totals = Strings["estimation"],
	commuteKm = Strings["commute_km_retained"], businessKm = Strings["business_km"], allowance = Strings["allowance"], tollsAndParking = Strings["tolls_parking"], total = Strings["total"],
	flatDeduction = Strings["flat_deduction"], expenses = Strings["expenses"], kind = Strings["expense_kind"], amount = Strings["amount"], place = Strings["place"],
	purposeNames = Purpose.entries.associateWith(Fmt::purpose), expenseKindNames = ExpenseKind.entries.associateWith(Fmt::kind),
	disclaimer = Strings["disclaimer_long"], generatedOn = Strings.format("generated_on", LocalDate.now().toString()), yes = Strings["yes"], no = Strings["no"],
	notes = e?.notes?.map { Strings[when (it) {
		EstimationNote.SCALE_UNCONFIRMED -> "note_scale_unconfirmed"; EstimationNote.COMMUTE_CAP_APPLIED -> "note_cap_applied"; EstimationNote.EXTRA_COMMUTE_LEGS_DROPPED -> "note_extra_legs"
		EstimationNote.TRIPS_WITHOUT_VEHICLE -> "note_no_vehicle"; EstimationNote.FUEL_INCLUDED_IN_SCALE -> "note_fuel"; EstimationNote.NO_SALARY_FOR_COMPARISON -> "note_no_salary"
	}] } ?: emptyList(),
	vehicleSummary = data.vehicles.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }
)

private fun export(state: AppState, year: Int, format: String, e: Estimation, onMessage: (String) -> Unit) {
	val data = state.current
	val report = Report(data, Day(year, 1, 1), Day(year, 12, 31), e)
	val l = labels(data, e)
	val name = "kilometres-$year"
	val ok = when (format) {
		"pdf" -> DesktopFiles.save(DesktopPdf.render(ReportDocument.from(report, l)), "$name.pdf")
		"xlsx" -> DesktopFiles.save(XlsxExport.build(report, l), "$name.xlsx")
		else -> DesktopFiles.save(CsvExport.build(report, l).toByteArray(Charsets.UTF_8), "$name.csv")
	}
	if (!ok) onMessage(Strings["export_failed"])
}

@Composable
fun MorePanel(state: AppState, onMessage: (String) -> Unit) {
	val data by state.data.collectAsState()
	val entitlement by state.entitlement.collectAsState()
	val s = data.settings
	var vehicleName by remember { mutableStateOf("") }
	var vehiclePower by remember { mutableStateOf("5") }
	var salary by remember { mutableStateOf(s.netTaxableSalary?.let(Fmt::number) ?: "") }
	var home by remember { mutableStateOf(s.homeLabel) }
	var work by remember { mutableStateOf(s.workLabel) }
	var homeWork by remember { mutableStateOf(s.homeWorkDistanceKm?.let(Fmt::number) ?: "") }
	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
		Text(Strings["tab_more"], style = MaterialTheme.typography.headlineMedium)
		Spacer(Modifier.height(12.dp))
		Card(colors = CardDefaults.cardColors(containerColor = if (entitlement.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
			Column(Modifier.padding(16.dp)) {
				Text(Strings[if (entitlement.isActive) "pro_active" else "pro_title"], style = MaterialTheme.typography.titleMedium)
				Text(if (entitlement.isActive) Strings.format("pro_active_since", entitlement.productId ?: "") else Strings["pro_unavailable"])
			}
		}
		Spacer(Modifier.height(16.dp))
		Text(Strings["vehicles"], style = MaterialTheme.typography.titleMedium)
		for (v in data.vehicles) ListItem(headlineContent = { Text(v.name) }, supportingContent = { Text(Strings.format("cv", v.fiscalPower)) }, trailingContent = { TextButton(onClick = { state.deleteVehicle(v.id) }) { Text(Strings["delete"]) } })
		Row(verticalAlignment = Alignment.CenterVertically) {
			OutlinedTextField(vehicleName, { vehicleName = it }, label = { Text(Strings["vehicle_name"]) }, singleLine = true, modifier = Modifier.weight(1f))
			Spacer(Modifier.width(8.dp))
			OutlinedTextField(vehiclePower, { vehiclePower = it }, label = { Text(Strings["fiscal_power"]) }, singleLine = true, modifier = Modifier.width(140.dp))
			Spacer(Modifier.width(8.dp))
			Button(enabled = vehicleName.isNotBlank(), onClick = { state.upsertVehicle(Vehicle(state.newId(), vehicleName.trim(), VehicleKind.CAR, vehiclePower.toIntOrNull() ?: 5)); vehicleName = "" }) { Text(Strings["add_vehicle"]) }
		}
		Spacer(Modifier.height(16.dp))
		Text(Strings["settings"], style = MaterialTheme.typography.titleMedium)
		Row { for (r in RuleSet.entries) FilterChip(selected = s.ruleSet == r, onClick = { state.updateSettings(s.copy(ruleSet = r)) }, label = { Text(Strings[if (r == RuleSet.FR) "rule_fr" else "rule_generic"]) }, modifier = Modifier.padding(end = 6.dp)) }
		OutlinedTextField(salary, { salary = it }, label = { Text(Strings["net_taxable_salary"]) }, singleLine = true, modifier = Modifier.fillMaxWidth())
		OutlinedTextField(home, { home = it }, label = { Text(Strings["home_label"]) }, singleLine = true, modifier = Modifier.fillMaxWidth())
		OutlinedTextField(work, { work = it }, label = { Text(Strings["work_label"]) }, singleLine = true, modifier = Modifier.fillMaxWidth())
		OutlinedTextField(homeWork, { homeWork = it }, label = { Text(Strings["home_work_distance"]) }, singleLine = true, modifier = Modifier.fillMaxWidth())
		Button(onClick = { state.updateSettings(state.current.settings.copy(netTaxableSalary = Fmt.parse(salary), homeLabel = home.trim(), workLabel = work.trim(), homeWorkDistanceKm = Fmt.parse(homeWork))) }) { Text(Strings["save"]) }
		Spacer(Modifier.height(16.dp))
		Text(Strings["backup_title"], style = MaterialTheme.typography.titleMedium)
		Text(Strings["backup_explain"], style = MaterialTheme.typography.bodySmall)
		Row {
			Button(onClick = { if (!DesktopFiles.save(BackupCodec.encode(data).toByteArray(Charsets.UTF_8), "kilometres.${BackupCodec.FILE_EXTENSION}")) onMessage(Strings["error_generic"]) }) { Text(Strings["backup_export"]) }
			Spacer(Modifier.width(8.dp))
			OutlinedButton(enabled = entitlement.allows(Feature.BACKUP_RESTORE), onClick = {
				val text = DesktopFiles.openText() ?: return@OutlinedButton
				val incoming = BackupCodec.decode(text)
				if (incoming == null) onMessage(Strings["backup_invalid"]) else { val merged = BackupCodec.merge(data, incoming); state.replaceData(merged); onMessage(Strings.format("backup_restored", merged.trips.size)) }
			}) { Text(Strings["backup_restore"]) }
		}
		Spacer(Modifier.height(16.dp))
		Text(Strings["import_title"], style = MaterialTheme.typography.titleMedium)
		Text(Strings["import_help"], style = MaterialTheme.typography.bodySmall)
		Button(enabled = entitlement.allows(Feature.IMPORT_HISTORY), onClick = {
			val text = DesktopFiles.openText() ?: return@Button
			val r = LocationHistoryParser.parse(text)
			val trips = LocationHistoryParser.toTrips(r.trips, state::newId, Purpose.BUSINESS, data.settings.defaultVehicleId, data.settings.homeLabel, data.settings.workLabel)
			onMessage(Strings.format("import_done", state.addTrips(trips)))
		}) { Text(Strings["import_pick"]) }
		Spacer(Modifier.height(24.dp))
		Text(Strings["legal_title"], style = MaterialTheme.typography.titleMedium)
		Text(Strings["disclaimer_long"], style = MaterialTheme.typography.bodySmall)
		Text(Strings["privacy_text"], style = MaterialTheme.typography.bodySmall)
	}
}
