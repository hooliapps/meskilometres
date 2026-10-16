package com.hooliapps.kilometres.core.lot3

import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.Expense
import com.hooliapps.kilometres.core.ExpenseKind
import com.hooliapps.kilometres.core.ExpenseSource

/**
 * Ce que l'on retient d'un ticket. Jamais l'image : la date, le montant, la
 * devise, le lieu, et quand ils y sont, les litres et le kilometrage.
 */
data class ReceiptReading(
	val kind: ExpenseKind,
	val date: Day? = null,
	val amount: Double? = null,
	val currency: String = "EUR",
	val place: String = "",
	val litres: Double? = null,
	val odometerKm: Double? = null
) {
	val isUsable: Boolean get() = amount != null

	fun toExpense(id: String, fallbackDate: Day): Expense = Expense(
		id = id, date = date ?: fallbackDate, kind = kind, amount = amount ?: 0.0, currency = currency,
		place = place, litres = litres, odometerKm = odometerKm, source = ExpenseSource.SCAN
	)
}

/**
 * LOT 3, INACTIF. Transforme le TEXTE reconnu sur un ticket en donnees
 * structurees. La reconnaissance elle-meme se fait sur l'appareil, par
 * plateforme, et ne rend que du texte : l'image n'est jamais ecrite.
 */
object ReceiptParser {
	private val TOLL = listOf("PEAGE", "PÉAGE", "TOLL", "AUTOROUTE", "MAUT", "PEDAGGIO", "PEAJE")
	private val PARKING = listOf("PARKING", "STATIONNEMENT", "PARKEN", "PARCHEGGIO", "APARCAMIENTO", "HORODATEUR")
	private val FUEL = listOf(
		"CARBURANT", "GAZOLE", "GASOIL", "DIESEL", "SP95", "SP98", "E10", "E85", "ESSENCE", "SANS PLOMB",
		"FUEL", "PETROL", "GASOLINE", "UNLEADED", "BENZIN", "KRAFTSTOFF", "LITRE", "LITER", "GPL", "LPG"
	)

	private val AMOUNT_LINE = Regex("""(?:TOTAL|MONTANT|NET A PAYER|NET À PAYER|A PAYER|À PAYER|TTC|AMOUNT|SUMME|BETRAG|IMPORTE)[^\d\n]{0,20}(\d{1,5}[.,]\d{2})""", RegexOption.IGNORE_CASE)
	private val ANY_AMOUNT = Regex("""(\d{1,5}[.,]\d{2})(?!\d)\s*(€|EUR|CHF|GBP|£|\$|USD)?""", RegexOption.IGNORE_CASE)
	private val DATE_DMY = Regex("""\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})\b""")
	private val DATE_ISO = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
	private val LITRES = Regex("""(\d{1,3}[.,]\d{1,3})\s*(?:L\b|LITRES?|LITERS?|LTR)""", RegexOption.IGNORE_CASE)
	private val ODOMETER = Regex("""(?:KM|KILOM[EÉ]TRAGE|ODOM[EÈ]TRE|ODOMETER)\D{0,6}(\d{4,7})\b""", RegexOption.IGNORE_CASE)

	fun parse(text: String): ReceiptReading {
		val upper = text.uppercase()
		val lines = text.split(Regex("\r?\n")).map { it.trim() }.filter { it.isNotEmpty() }
		val kind = when {
			FUEL.any { upper.contains(it) } -> ExpenseKind.FUEL
			TOLL.any { upper.contains(it) } -> ExpenseKind.TOLL
			PARKING.any { upper.contains(it) } -> ExpenseKind.PARKING
			else -> ExpenseKind.OTHER
		}
		return ReceiptReading(
			kind = kind,
			date = findDate(text),
			amount = findAmount(text),
			currency = findCurrency(upper),
			place = guessPlace(lines),
			litres = if (kind == ExpenseKind.FUEL) LITRES.find(text)?.groupValues?.get(1)?.let(::toDouble) else null,
			odometerKm = ODOMETER.find(text)?.groupValues?.get(1)?.let(::toDouble)
		)
	}

	private fun findAmount(text: String): Double? {
		// Les dates ecrites avec des points ressemblent a des montants.
		val cleaned = text.replace(DATE_DMY, " ").replace(DATE_ISO, " ")
		AMOUNT_LINE.find(cleaned)?.let { return toDouble(it.groupValues[1]) }
		return ANY_AMOUNT.findAll(cleaned).mapNotNull { toDouble(it.groupValues[1]) }.maxOrNull()
	}

	private fun findCurrency(upper: String) = when {
		upper.contains('€') || upper.contains("EUR") -> "EUR"
		upper.contains("CHF") -> "CHF"
		upper.contains('£') || upper.contains("GBP") -> "GBP"
		upper.contains('$') || upper.contains("USD") -> "USD"
		else -> "EUR"
	}

	private fun findDate(text: String): Day? {
		DATE_ISO.find(text)?.let { m -> return day(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
		DATE_DMY.find(text)?.let { m ->
			var y = m.groupValues[3].toInt()
			if (y < 100) y += 2000
			return day(y, m.groupValues[2].toInt(), m.groupValues[1].toInt())
		}
		return null
	}

	private fun day(y: Int, m: Int, d: Int): Day? = if (y in 2000..2100) runCatching { Day(y, m, d) }.getOrNull() else null

	private fun toDouble(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

	private fun guessPlace(lines: List<String>): String {
		val skip = Regex("(TOTAL|TICKET|RECU|REÇU|FACTURE|TVA|VAT|SIRET|TEL)", RegexOption.IGNORE_CASE)
		for (l in lines.take(6)) {
			if (l.length < 3 || l.length > 40) continue
			if (Regex("""^[\d\s.,:/€-]+$""").matches(l)) continue
			if (DATE_DMY.containsMatchIn(l) || DATE_ISO.containsMatchIn(l)) continue
			if (skip.containsMatchIn(l)) continue
			return l
		}
		return ""
	}
}
