package com.hooliapps.kilometres.core.export

/**
 * Le rapport PDF, decrit sans mise en page : titres, lignes, tableaux et
 * un pied de page. Chaque plateforme le dessine avec son propre moteur de
 * texte et ses propres polices, ce qui garantit toutes les ecritures (arabe,
 * chinois, devanagari) sans embarquer une seule police :
 *
 *   - Android : android.graphics.pdf.PdfDocument ;
 *   - iOS : UIGraphicsPDFRenderer, dans l'application Swift ;
 *   - Windows : voir desktopApp, rendu par le JDK.
 */
data class ReportDocument(
	val title: String,
	val subtitle: String,
	val headerLines: List<String>,
	val summary: List<Pair<String, String>>,
	val summaryTitle: String,
	val notes: List<String>,
	val tripsTable: Table,
	val expensesTitle: String,
	val expensesTable: Table?,
	val footer: String,
	val generated: String
) {
	data class Table(val headers: List<String>, val rows: List<List<String>>, val rightAligned: Set<Int>)

	companion object {
		fun from(r: Report, l: ExportLabels): ReportDocument {
			val est = r.estimation
			val summary = mutableListOf<Pair<String, String>>()
			if (est != null) {
				summary += l.commuteKm to fmt2(est.commuteKmRetained)
				summary += l.businessKm to fmt2(est.businessKm)
				summary += l.allowance to fmt2(est.allowanceTotal)
				summary += l.tollsAndParking to fmt2(est.tollsAndParking)
				summary += l.total to fmt2(est.total)
				est.flatDeduction?.let { summary += l.flatDeduction to fmt2(it) }
			}
			val trips = Table(
				headers = listOf(l.date, l.from, l.to, l.purpose, l.vehicle, l.roundTrip, l.distanceKm),
				rows = r.trips.map { t ->
					listOf(t.date.toIso(), t.from, t.to, l.purposeNames[t.purpose] ?: t.purpose.name, r.vehicleName(t), if (t.roundTrip) l.yes else l.no, fmt2(t.totalKm))
				} + listOf(listOf("", "", "", "", "", l.total, fmt2(r.totalKm))),
				rightAligned = setOf(6)
			)
			val expenses = if (r.expenses.isEmpty()) null else Table(
				headers = listOf(l.date, l.kind, l.amount, l.place),
				rows = r.expenses.map { e -> listOf(e.date.toIso(), l.expenseKindNames[e.kind] ?: e.kind.name, fmt2(e.amount), e.place) },
				rightAligned = setOf(2)
			)
			return ReportDocument(
				title = l.reportTitle,
				subtitle = "${l.period} : ${r.from.toIso()} – ${r.to.toIso()}",
				headerLines = listOfNotNull(l.vehicleSummary),
				summary = summary,
				summaryTitle = l.totals,
				notes = if (est != null) l.notes else emptyList(),
				tripsTable = trips,
				expensesTitle = l.expenses,
				expensesTable = expenses,
				footer = l.disclaimer,
				generated = "${l.appName} · ${l.generatedOn}"
			)
		}
	}
}
