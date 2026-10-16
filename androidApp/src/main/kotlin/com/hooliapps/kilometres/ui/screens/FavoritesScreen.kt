package com.hooliapps.kilometres.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.ui.AppViewModel
import com.hooliapps.kilometres.ui.Screen
import com.hooliapps.kilometres.ui.components.ConfirmDeleteDialog
import com.hooliapps.kilometres.ui.kmText
import com.hooliapps.kilometres.ui.purposeName

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(vm: AppViewModel) {
	val data by vm.state.data.collectAsState()
	var toDelete by remember { mutableStateOf<String?>(null) }
	Box(Modifier.fillMaxSize()) {
		if (data.favorites.isEmpty()) {
			Text(stringResource(R.string.no_favorites), textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp))
		} else {
			Column(Modifier.verticalScroll(rememberScrollState())) {
				for (f in data.favorites) {
					ListItem(
						leadingContent = { Icon(Icons.Outlined.Bolt, contentDescription = null) },
						headlineContent = { Text(f.label) },
						supportingContent = {
							Text(listOf(listOf(f.from, f.to).filter { it.isNotEmpty() }.joinToString(" → "), purposeName(f.purpose), if (f.roundTrip) stringResource(R.string.field_round_trip) else "").filter { it.isNotEmpty() }.joinToString(" · "))
						},
						trailingContent = { Text(kmText(f.totalKm)) },
						modifier = Modifier.combinedClickable(onClick = { vm.open(Screen.TripForm(favoriteId = f.id)) }, onLongClick = { toDelete = f.id })
					)
				}
			}
		}
	}
	toDelete?.let { id -> ConfirmDeleteDialog(onConfirm = { vm.state.deleteFavorite(id); toDelete = null }, onDismiss = { toDelete = null }) }
}
