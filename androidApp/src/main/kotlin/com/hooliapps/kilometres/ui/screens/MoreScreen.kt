package com.hooliapps.kilometres.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.ui.AppViewModel
import com.hooliapps.kilometres.ui.Screen

@Composable
fun MoreScreen(vm: AppViewModel) {
	val entitlement by vm.state.entitlement.collectAsState()
	val pro = entitlement.isActive
	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
		Text(stringResource(R.string.tab_more), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
		Card(
			colors = CardDefaults.cardColors(containerColor = if (pro) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer),
			modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable { vm.open(Screen.Pro) }
		) {
			ListItem(
				leadingContent = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null) },
				headlineContent = { Text(stringResource(if (pro) R.string.pro_active else R.string.pro_title)) },
				supportingContent = { Text(if (pro) stringResource(R.string.pro_active_since, entitlement.productId ?: "") else stringResource(R.string.pro_subtitle)) },
				colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
			)
		}
		Row(vm, Icons.Outlined.DirectionsCar, R.string.vehicles, Screen.Vehicles)
		Row(vm, Icons.Outlined.Bolt, R.string.favorites, Screen.Favorites)
		Row(vm, Icons.Outlined.ReceiptLong, R.string.expenses, Screen.Expenses, proTag = !pro)
		HorizontalDivider()
		Row(vm, Icons.Outlined.IosShare, R.string.export_title, Screen.Export(), proTag = !pro)
		Row(vm, Icons.Outlined.FileDownload, R.string.import_title, Screen.Import, proTag = !pro)
		Row(vm, Icons.Outlined.Cloud, R.string.backup_title, Screen.Backup, proTag = false)
		HorizontalDivider()
		Row(vm, Icons.Outlined.Tune, R.string.settings, Screen.Settings)
		Row(vm, Icons.Outlined.Policy, R.string.legal_title, Screen.Legal)
	}
}

@Composable
private fun Row(vm: AppViewModel, icon: ImageVector, title: Int, target: Screen, proTag: Boolean = false) {
	ListItem(
		leadingContent = { Icon(icon, contentDescription = null) },
		headlineContent = { Text(stringResource(title)) },
		trailingContent = if (proTag) ({ SuggestionChip(onClick = { vm.open(Screen.Pro) }, label = { Text(stringResource(R.string.pro_badge)) }) }) else null,
		modifier = Modifier.clickable { vm.open(target) }
	)
}
