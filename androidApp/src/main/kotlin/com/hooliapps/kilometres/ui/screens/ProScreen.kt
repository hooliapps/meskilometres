package com.hooliapps.kilometres.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hooliapps.kilometres.Graph
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.ads.AdsManager
import com.hooliapps.kilometres.billing.Purchases
import com.hooliapps.kilometres.core.Features
import com.hooliapps.kilometres.core.Products
import com.hooliapps.kilometres.ui.AppViewModel
import com.hooliapps.kilometres.ui.LocalSnackbar
import kotlinx.coroutines.launch

/**
 * L'ecran Pro. Les prix viennent de la boutique ; a defaut, les prix de
 * reference. Les fonctions des lots inactifs n'apparaissent que lorsque leur
 * drapeau est leve : on ne promet que ce qui existe.
 */
@Composable
fun ProScreen(vm: AppViewModel) {
	val purchases = Graph.purchases
	val entitlement by vm.state.entitlement.collectAsState()
	val status by purchases.status.collectAsState()
	val products by purchases.products.collectAsState()
	val activity = LocalActivity.current
	val snackbar = LocalSnackbar.current
	val scope = rememberCoroutineScope()
	val restored = stringResource(R.string.purchase_restored)
	val nothing = stringResource(R.string.purchase_nothing)

	val features = listOfNotNull(
		R.string.pro_feature_unlimited, R.string.pro_feature_no_ads, R.string.pro_feature_estimation, R.string.pro_feature_export,
		R.string.pro_feature_import, R.string.pro_feature_expenses, R.string.pro_feature_cloud, R.string.pro_feature_widgets, R.string.pro_feature_watch,
		if (Features.GPS_RECORDING || Features.AUTO_DETECTION || Features.CAR_CONNECTION) R.string.pro_feature_gps else null,
		if (Features.RECEIPT_SCAN) R.string.pro_feature_scan else null
	)

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
		Icon(Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
		Spacer(Modifier.height(8.dp))
		Text(stringResource(R.string.pro_subtitle), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
		Spacer(Modifier.height(16.dp))
		Column(Modifier.fillMaxWidth()) {
			Text(stringResource(R.string.pro_features_title), style = MaterialTheme.typography.titleSmall)
			for (f in features) {
				ListItem(leadingContent = { Icon(Icons.Filled.CheckCircle, contentDescription = null) }, headlineContent = { Text(stringResource(f)) })
			}
		}
		Spacer(Modifier.height(16.dp))
		if (entitlement.isActive) {
			Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
				ListItem(leadingContent = { Icon(Icons.Filled.Verified, contentDescription = null) }, headlineContent = { Text(stringResource(R.string.pro_active)) },
					supportingContent = { Text(stringResource(R.string.pro_active_since, entitlement.productId ?: "")) },
					colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
			}
		} else {
			val yearly = products[Products.YEARLY]
			val monthly = products[Products.MONTHLY]
			PlanCard(stringResource(R.string.pro_yearly, yearly?.price ?: Products.YEARLY_FALLBACK_PRICE), stringResource(R.string.pro_yearly_hint), highlighted = true,
				enabled = status == Purchases.Status.READY && yearly != null && activity != null) { purchases.buy(activity!!, Products.YEARLY) }
			Spacer(Modifier.height(8.dp))
			PlanCard(stringResource(R.string.pro_monthly, monthly?.price ?: Products.MONTHLY_FALLBACK_PRICE), null, highlighted = false,
				enabled = status == Purchases.Status.READY && monthly != null && activity != null) { purchases.buy(activity!!, Products.MONTHLY) }
			Spacer(Modifier.height(8.dp))
			when (status) {
				Purchases.Status.CONNECTING -> CircularProgressIndicator()
				Purchases.Status.PENDING -> Text(stringResource(R.string.purchase_pending))
				Purchases.Status.UNAVAILABLE -> Text(stringResource(R.string.pro_unavailable), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
				Purchases.Status.FAILED -> Text(stringResource(R.string.purchase_failed), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
				else -> {}
			}
		}
		TextButton(enabled = status == Purchases.Status.READY, onClick = {
			scope.launch { snackbar.showSnackbar(if (purchases.restore()) restored else nothing) }
		}) { Text(stringResource(R.string.restore_purchases)) }
		if (activity != null && !entitlement.isActive && AdsManager.privacyOptionsAvailable(activity)) {
			TextButton(onClick = { AdsManager.showPrivacyOptions(activity) }) { Text(stringResource(R.string.ads_privacy_options)) }
		}
		Spacer(Modifier.height(8.dp))
		Text(stringResource(R.string.pro_legal), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
		Spacer(Modifier.height(24.dp))
	}
}

@Composable
private fun PlanCard(title: String, subtitle: String?, highlighted: Boolean, enabled: Boolean, onTap: () -> Unit) {
	Card(colors = CardDefaults.cardColors(containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
		ListItem(
			headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
			supportingContent = subtitle?.let { { Text(it) } },
			trailingContent = { Button(onClick = onTap, enabled = enabled) { Text(stringResource(R.string.subscribe)) } },
			colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
		)
	}
}
