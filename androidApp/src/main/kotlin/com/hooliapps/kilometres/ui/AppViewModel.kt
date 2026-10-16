package com.hooliapps.kilometres.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.hooliapps.kilometres.core.store.AppState

/** Les ecrans. Une pile de profondeur un ou deux, aucun argument lourd. */
sealed class Screen {
	data class Tabs(val tab: Int = 0) : Screen()
	data class TripForm(val tripId: String? = null, val favoriteId: String? = null) : Screen()
	data object Vehicles : Screen()
	data class VehicleForm(val id: String? = null) : Screen()
	data object Favorites : Screen()
	data object Expenses : Screen()
	data class ExpenseForm(val id: String? = null) : Screen()
	data object Import : Screen()
	data class Export(val year: Int? = null) : Screen()
	data object Backup : Screen()
	data object Settings : Screen()
	data object Pro : Screen()
	data object Legal : Screen()
}

/**
 * Navigation et etat d'ecran. Les donnees elles-memes vivent dans [AppState]
 * et sont lues par les ecrans avec collectAsState().
 */
class AppViewModel(val state: AppState) : ViewModel() {
	val stack = mutableStateListOf<Screen>(Screen.Tabs())
	val tab = mutableStateOf(0)

	val current: Screen get() = stack.last()

	fun open(screen: Screen) {
		stack += screen
	}

	fun back(): Boolean {
		if (stack.size <= 1) return false
		stack.removeAt(stack.lastIndex)
		return true
	}

	fun home() {
		while (stack.size > 1) stack.removeAt(stack.lastIndex)
	}
}
