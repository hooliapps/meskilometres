package com.hooliapps.kilometres.core.export

/**
 * CSV en UTF-8 avec BOM et point-virgule : s'ouvre correctement dans les
 * tableurs francophones sans assistant d'import.
 */
object CsvExport {
	fun build(r: Report, l: ExportLabels): String = buildString {
		append('﻿')
		appendLine(row(listOf(l.date, l.from, l.to, l.purpose, l.vehicle, l.roundTrip, l.distanceKm, l.note)))
		for (t in r.trips) {
			appendLine(row(listOf(
				t.date.toIso(), t.from, t.to,
				l.purposeNames[t.purpose] ?: t.purpose.name,
				r.vehicleName(t),
				if (t.roundTrip) l.yes else l.no,
				fmt2(t.totalKm),
				t.note
			)))
		}
		if (r.expenses.isNotEmpty()) {
			appendLine()
			appendLine(row(listOf(l.date, l.kind, l.amount, l.place)))
			for (e in r.expenses) {
				appendLine(row(listOf(e.date.toIso(), l.expenseKindNames[e.kind] ?: e.kind.name, fmt2(e.amount), e.place)))
			}
		}
		appendLine()
		appendLine(row(listOf(l.disclaimer)))
	}

	private fun row(cells: List<String>) = cells.joinToString(";", transform = ::escape)

	private fun escape(s: String): String =
		if (s.contains(';') || s.contains('"') || s.contains('\n')) "\"${s.replace("\"", "\"\"")}\"" else s
}
