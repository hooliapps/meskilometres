package com.hooliapps.kilometres.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.core.AppData
import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.ExpenseKind
import com.hooliapps.kilometres.core.Purpose
import com.hooliapps.kilometres.core.VehicleKind
import com.hooliapps.kilometres.core.backup.BackupCodec
import com.hooliapps.kilometres.core.export.CsvExport
import com.hooliapps.kilometres.core.export.ExportLabels
import com.hooliapps.kilometres.core.export.Report
import com.hooliapps.kilometres.core.export.ReportDocument
import com.hooliapps.kilometres.core.export.XlsxExport
import com.hooliapps.kilometres.core.rules.Estimation
import com.hooliapps.kilometres.core.rules.EstimationNote
import com.hooliapps.kilometres.ui.Fmt
import com.hooliapps.kilometres.ui.expenseKindName
import com.hooliapps.kilometres.ui.purposeName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Produit un fichier dans le cache prive et l'ouvre dans la feuille de
 * partage par un FileProvider : aucune permission de stockage.
 */
object Exporter {

	suspend fun sharePdf(context: Context, data: AppData, from: Day, to: Day, estimation: Estimation?) {
		val labels = labels(context, data, estimation)
		val report = Report(data, from, to, estimation)
		val bytes = withContext(Dispatchers.Default) { PdfRenderer.render(ReportDocument.from(report, labels)) }
		share(context, bytes, "${baseName(from, to)}.pdf", "application/pdf", labels.reportTitle)
	}

	suspend fun shareXlsx(context: Context, data: AppData, from: Day, to: Day, estimation: Estimation?) {
		val labels = labels(context, data, estimation)
		val bytes = withContext(Dispatchers.Default) { XlsxExport.build(Report(data, from, to, estimation), labels) }
		share(context, bytes, "${baseName(from, to)}.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", labels.reportTitle)
	}

	suspend fun shareCsv(context: Context, data: AppData, from: Day, to: Day, estimation: Estimation?) {
		val labels = labels(context, data, estimation)
		val text = withContext(Dispatchers.Default) { CsvExport.build(Report(data, from, to, estimation), labels) }
		share(context, text.toByteArray(Charsets.UTF_8), "${baseName(from, to)}.csv", "text/csv", labels.reportTitle)
	}

	suspend fun shareBackup(context: Context, data: AppData) {
		val text = withContext(Dispatchers.Default) { BackupCodec.encode(data) }
		share(context, text.toByteArray(Charsets.UTF_8), "kilometres-${LocalDate.now()}.${BackupCodec.FILE_EXTENSION}", "application/json", context.getString(R.string.backup_title))
	}

	private fun baseName(from: Day, to: Day) = "kilometres-${from.toIso()}-${to.toIso()}"

	private suspend fun share(context: Context, bytes: ByteArray, name: String, mime: String, subject: String) {
		val dir = File(context.cacheDir, "exports").apply { mkdirs() }
		val file = File(dir, name)
		withContext(Dispatchers.IO) { file.writeBytes(bytes) }
		val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
		val send = Intent(Intent.ACTION_SEND).apply {
			type = mime
			putExtra(Intent.EXTRA_STREAM, uri)
			putExtra(Intent.EXTRA_SUBJECT, subject)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
	}

	fun labels(context: Context, data: AppData, e: Estimation?): ExportLabels {
		val currency = data.settings.currency
		val notes = e?.notes?.map {
			when (it) {
				EstimationNote.SCALE_UNCONFIRMED -> context.getString(R.string.note_scale_unconfirmed)
				EstimationNote.COMMUTE_CAP_APPLIED -> context.getString(R.string.note_cap_applied)
				EstimationNote.EXTRA_COMMUTE_LEGS_DROPPED -> context.getString(R.string.note_extra_legs)
				EstimationNote.TRIPS_WITHOUT_VEHICLE -> context.getString(R.string.note_no_vehicle)
				EstimationNote.FUEL_INCLUDED_IN_SCALE -> context.getString(R.string.note_fuel, Fmt.money(e.fuelIgnored, currency))
				EstimationNote.NO_SALARY_FOR_COMPARISON -> context.getString(R.string.note_no_salary)
			}
		} ?: emptyList()
		val vehicles = data.vehicles.joinToString(", ") { v ->
			val kind = context.getString(when (v.kind) { VehicleKind.CAR -> R.string.kind_car; VehicleKind.MOTORCYCLE -> R.string.kind_motorcycle; VehicleKind.MOPED -> R.string.kind_moped })
			if (v.kind == VehicleKind.MOPED) "${v.name} ($kind)" else "${v.name} ($kind, ${context.getString(R.string.cv, v.fiscalPower)})"
		}
		return ExportLabels(
			appName = context.getString(R.string.app_name), reportTitle = context.getString(R.string.report_title), period = context.getString(R.string.export_period),
			date = context.getString(R.string.field_date), from = context.getString(R.string.field_from), to = context.getString(R.string.field_to),
			purpose = context.getString(R.string.field_purpose), vehicle = context.getString(R.string.field_vehicle), roundTrip = context.getString(R.string.field_round_trip),
			distanceKm = "km", note = context.getString(R.string.field_note), totals = context.getString(R.string.estimation),
			commuteKm = context.getString(R.string.commute_km_retained), businessKm = context.getString(R.string.business_km), allowance = context.getString(R.string.allowance),
			tollsAndParking = context.getString(R.string.tolls_parking), total = context.getString(R.string.total), flatDeduction = context.getString(R.string.flat_deduction),
			expenses = context.getString(R.string.expenses), kind = context.getString(R.string.expense_kind), amount = context.getString(R.string.amount), place = context.getString(R.string.place),
			purposeNames = Purpose.entries.associateWith { context.purposeName(it) },
			expenseKindNames = ExpenseKind.entries.associateWith { context.expenseKindName(it) },
			disclaimer = context.getString(R.string.disclaimer_long),
			generatedOn = context.getString(R.string.generated_on, Fmt.dateTime(System.currentTimeMillis())),
			yes = context.getString(R.string.yes), no = context.getString(R.string.no), notes = notes,
			vehicleSummary = if (vehicles.isEmpty()) null else "${context.getString(R.string.vehicles)} : $vehicles"
		)
	}
}
