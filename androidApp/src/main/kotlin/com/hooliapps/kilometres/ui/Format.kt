package com.hooliapps.kilometres.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.ExpenseKind
import com.hooliapps.kilometres.core.Purpose
import com.hooliapps.kilometres.core.VehicleKind
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** Formats dans la langue de l'appareil. */
object Fmt {
	fun km(v: Double, locale: Locale = Locale.getDefault()): String =
		NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(v)

	fun money(v: Double, currency: String, locale: Locale = Locale.getDefault()): String {
		val nf = NumberFormat.getCurrencyInstance(locale)
		runCatching { nf.currency = Currency.getInstance(currency) }
		return nf.format(v)
	}

	fun day(d: Day, locale: Locale = Locale.getDefault()): String =
		LocalDate.of(d.year, d.month, d.day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

	fun dayLong(context: Context, d: Day): String {
		val today = Day.today()
		if (d == today) return context.getString(R.string.today)
		if (d == today.plusDays(-1)) return context.getString(R.string.yesterday)
		return LocalDate.of(d.year, d.month, d.day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))
	}

	fun dateTime(epochMillis: Long, locale: Locale = Locale.getDefault()): String =
		java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault())
			.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))

	/** Lit un nombre saisi avec virgule ou point. */
	fun parseNumber(s: String): Double? = s.trim().replace(',', '.').replace(" ", "").replace(" ", "").toDoubleOrNull()

	fun number(v: Double): String = if (v == Math.rint(v)) v.toLong().toString() else v.toString()
}

@Composable
fun purposeName(p: Purpose): String = stringResource(when (p) {
	Purpose.COMMUTE -> R.string.purpose_commute
	Purpose.BUSINESS -> R.string.purpose_business
	Purpose.PERSONAL -> R.string.purpose_personal
})

fun Context.purposeName(p: Purpose): String = getString(when (p) {
	Purpose.COMMUTE -> R.string.purpose_commute
	Purpose.BUSINESS -> R.string.purpose_business
	Purpose.PERSONAL -> R.string.purpose_personal
})

@Composable
fun expenseKindName(k: ExpenseKind): String = stringResource(when (k) {
	ExpenseKind.TOLL -> R.string.kind_toll
	ExpenseKind.PARKING -> R.string.kind_parking
	ExpenseKind.FUEL -> R.string.kind_fuel
	ExpenseKind.OTHER -> R.string.kind_other
})

fun Context.expenseKindName(k: ExpenseKind): String = getString(when (k) {
	ExpenseKind.TOLL -> R.string.kind_toll
	ExpenseKind.PARKING -> R.string.kind_parking
	ExpenseKind.FUEL -> R.string.kind_fuel
	ExpenseKind.OTHER -> R.string.kind_other
})

@Composable
fun vehicleKindName(k: VehicleKind): String = stringResource(when (k) {
	VehicleKind.CAR -> R.string.kind_car
	VehicleKind.MOTORCYCLE -> R.string.kind_motorcycle
	VehicleKind.MOPED -> R.string.kind_moped
})

@Composable
fun kmText(v: Double): String = stringResource(R.string.km_value, Fmt.km(v))

@Composable
fun currentContext(): Context = LocalContext.current
