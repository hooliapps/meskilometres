package com.hooliapps.kilometres.core.export

/**
 * Classeur tableur (.xlsx) ecrit directement : trois feuilles, chaines en
 * ligne, nombres en nombres. Aucune bibliotheque : le format est un ZIP de
 * XML, voir [Zip]. Le meme code produit le meme fichier sur toutes les
 * plateformes.
 */
object XlsxExport {

	private sealed interface Cell {
		data class Text(val s: String) : Cell
		data class Num(val v: Double) : Cell
	}

	fun build(r: Report, l: ExportLabels): ByteArray {
		val trips = mutableListOf<List<Cell>>()
		trips += listOf(l.date, l.from, l.to, l.purpose, l.vehicle, l.roundTrip, l.distanceKm, l.note).map { Cell.Text(it) }
		for (t in r.trips) {
			trips += listOf(
				Cell.Text(t.date.toIso()), Cell.Text(t.from), Cell.Text(t.to),
				Cell.Text(l.purposeNames[t.purpose] ?: t.purpose.name), Cell.Text(r.vehicleName(t)),
				Cell.Text(if (t.roundTrip) l.yes else l.no), Cell.Num(t.totalKm), Cell.Text(t.note)
			)
		}
		trips += listOf(Cell.Text(l.total), Cell.Text(""), Cell.Text(""), Cell.Text(""), Cell.Text(""), Cell.Text(""), Cell.Num(r.totalKm))

		val expenses = mutableListOf<List<Cell>>()
		expenses += listOf(l.date, l.kind, l.amount, l.place).map { Cell.Text(it) }
		for (e in r.expenses) {
			expenses += listOf(Cell.Text(e.date.toIso()), Cell.Text(l.expenseKindNames[e.kind] ?: e.kind.name), Cell.Num(e.amount), Cell.Text(e.place))
		}

		val summary = mutableListOf<List<Cell>>()
		summary += listOf(Cell.Text(l.reportTitle), Cell.Text("${r.from.toIso()} - ${r.to.toIso()}"))
		r.estimation?.let { e ->
			summary += listOf(Cell.Text(l.commuteKm), Cell.Num(e.commuteKmRetained))
			summary += listOf(Cell.Text(l.businessKm), Cell.Num(e.businessKm))
			summary += listOf(Cell.Text(l.allowance), Cell.Num(e.allowanceTotal))
			summary += listOf(Cell.Text(l.tollsAndParking), Cell.Num(e.tollsAndParking))
			summary += listOf(Cell.Text(l.total), Cell.Num(e.total))
			e.flatDeduction?.let { summary += listOf(Cell.Text(l.flatDeduction), Cell.Num(it)) }
			for (n in l.notes) summary += listOf(Cell.Text(n))
		}
		summary += listOf(Cell.Text(""))
		summary += listOf(Cell.Text(l.disclaimer))

		val sheets = listOf("Trajets" to trips, "Depenses" to expenses, "Synthese" to summary)
		val entries = mutableListOf<Pair<String, ByteArray>>()
		entries += "[Content_Types].xml" to contentTypes(sheets.size).encodeToByteArray()
		entries += "_rels/.rels" to RELS.encodeToByteArray()
		entries += "xl/workbook.xml" to workbook(sheets.map { it.first }).encodeToByteArray()
		entries += "xl/_rels/workbook.xml.rels" to workbookRels(sheets.size).encodeToByteArray()
		entries += "xl/styles.xml" to STYLES.encodeToByteArray()
		sheets.forEachIndexed { i, (_, rows) -> entries += "xl/worksheets/sheet${i + 1}.xml" to sheet(rows).encodeToByteArray() }
		return Zip.build(entries)
	}

	private fun sheet(rows: List<List<Cell>>): String = buildString {
		append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
		append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
		rows.forEachIndexed { ri, row ->
			append("<row r=\"${ri + 1}\">")
			row.forEachIndexed { ci, cell ->
				val ref = "${column(ci)}${ri + 1}"
				when (cell) {
					is Cell.Num -> append("<c r=\"$ref\"><v>${cell.v}</v></c>")
					is Cell.Text -> if (cell.s.isNotEmpty()) append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(cell.s)}</t></is></c>")
				}
			}
			append("</row>")
		}
		append("</sheetData></worksheet>")
	}

	private fun column(i: Int): String {
		var n = i
		val sb = StringBuilder()
		do {
			sb.insert(0, 'A' + n % 26)
			n = n / 26 - 1
		} while (n >= 0)
		return sb.toString()
	}

	private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

	private fun contentTypes(n: Int) = buildString {
		append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
		append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>""")
		append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
		append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
		for (i in 1..n) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
		append("</Types>")
	}

	private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

	private fun workbook(names: List<String>) = buildString {
		append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
		names.forEachIndexed { i, n -> append("""<sheet name="${xml(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
		append("</sheets></workbook>")
	}

	private fun workbookRels(n: Int) = buildString {
		append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
		for (i in 1..n) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
		append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
		append("</Relationships>")
	}

	private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs></styleSheet>"""
}
