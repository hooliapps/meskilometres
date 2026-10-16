package com.hooliapps.kilometres.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.ui.screens.BackupScreen
import com.hooliapps.kilometres.ui.screens.EstimateScreen
import com.hooliapps.kilometres.ui.screens.ExpenseFormScreen
import com.hooliapps.kilometres.ui.screens.ExpensesScreen
import com.hooliapps.kilometres.ui.screens.ExportScreen
import com.hooliapps.kilometres.ui.screens.FavoritesScreen
import com.hooliapps.kilometres.ui.screens.ImportScreen
import com.hooliapps.kilometres.ui.screens.LegalScreen
import com.hooliapps.kilometres.ui.screens.MoreScreen
import com.hooliapps.kilometres.ui.screens.ProScreen
import com.hooliapps.kilometres.ui.screens.SettingsScreen
import com.hooliapps.kilometres.ui.screens.TripFormScreen
import com.hooliapps.kilometres.ui.screens.TripsScreen
import com.hooliapps.kilometres.ui.screens.VehicleFormScreen
import com.hooliapps.kilometres.ui.screens.VehiclesScreen
import com.hooliapps.kilometres.ui.theme.KilometresTheme

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }
val LocalNav = staticCompositionLocalOf<AppViewModel> { error("AppViewModel absent") }

/** Coque de l'application : barre, onglets, et l'ecran courant. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Root(vm: AppViewModel) {
	KilometresTheme {
		val snackbar = remember { SnackbarHostState() }
		val screen = vm.current
		BackHandler(enabled = screen !is Screen.Tabs) { vm.back() }

		CompositionLocalProvider(LocalSnackbar provides snackbar, LocalNav provides vm) {
			Scaffold(
				snackbarHost = { SnackbarHost(snackbar) },
				topBar = {
					val title = titleFor(screen)
					if (screen !is Screen.Tabs) {
						TopAppBar(
							title = { Text(title) },
							navigationIcon = {
								IconButton(onClick = { vm.back() }) {
									Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
								}
							}
						)
					}
				},
				floatingActionButton = {
					if (screen is Screen.Tabs && vm.tab.value == 0) {
						ExtendedFloatingActionButton(
							onClick = { vm.open(Screen.TripForm()) },
							icon = { Icon(Icons.Filled.Add, contentDescription = null) },
							text = { Text(stringResource(R.string.add_trip)) }
						)
					}
				},
				bottomBar = {
					if (screen is Screen.Tabs) {
						val tab by vm.tab
						NavigationBar {
							NavigationBarItem(selected = tab == 0, onClick = { vm.tab.value = 0 },
								icon = { Icon(if (tab == 0) Icons.Filled.Route else Icons.Outlined.Route, null) }, label = { Text(stringResource(R.string.tab_trips)) })
							NavigationBarItem(selected = tab == 1, onClick = { vm.tab.value = 1 },
								icon = { Icon(if (tab == 1) Icons.Filled.Calculate else Icons.Outlined.Calculate, null) }, label = { Text(stringResource(R.string.tab_estimate)) })
							NavigationBarItem(selected = tab == 2, onClick = { vm.tab.value = 2 },
								icon = { Icon(Icons.Filled.MoreHoriz, null) }, label = { Text(stringResource(R.string.tab_more)) })
						}
					}
				}
			) { padding ->
				Box(Modifier.fillMaxSize().padding(padding)) {
					when (screen) {
						is Screen.Tabs -> when (vm.tab.value) {
							0 -> TripsScreen(vm)
							1 -> EstimateScreen(vm)
							else -> MoreScreen(vm)
						}
						is Screen.TripForm -> TripFormScreen(vm, screen)
						Screen.Vehicles -> VehiclesScreen(vm)
						is Screen.VehicleForm -> VehicleFormScreen(vm, screen.id)
						Screen.Favorites -> FavoritesScreen(vm)
						Screen.Expenses -> ExpensesScreen(vm)
						is Screen.ExpenseForm -> ExpenseFormScreen(vm, screen.id)
						Screen.Import -> ImportScreen(vm)
						is Screen.Export -> ExportScreen(vm, screen.year)
						Screen.Backup -> BackupScreen(vm)
						Screen.Settings -> SettingsScreen(vm)
						Screen.Pro -> ProScreen(vm)
						Screen.Legal -> LegalScreen()
					}
				}
			}
		}
	}
}

@Composable
private fun titleFor(screen: Screen): String = when (screen) {
	is Screen.Tabs -> ""
	is Screen.TripForm -> stringResource(if (screen.tripId == null) R.string.trip_new else R.string.trip_edit)
	Screen.Vehicles -> stringResource(R.string.vehicles)
	is Screen.VehicleForm -> stringResource(if (screen.id == null) R.string.add_vehicle else R.string.edit_vehicle)
	Screen.Favorites -> stringResource(R.string.favorites)
	Screen.Expenses -> stringResource(R.string.expenses)
	is Screen.ExpenseForm -> stringResource(if (screen.id == null) R.string.add_expense else R.string.edit_expense)
	Screen.Import -> stringResource(R.string.import_title)
	is Screen.Export -> stringResource(R.string.export_title)
	Screen.Backup -> stringResource(R.string.backup_title)
	Screen.Settings -> stringResource(R.string.settings)
	Screen.Pro -> stringResource(R.string.pro_title)
	Screen.Legal -> stringResource(R.string.legal_title)
}
