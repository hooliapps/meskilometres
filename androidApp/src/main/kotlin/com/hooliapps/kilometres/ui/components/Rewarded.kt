package com.hooliapps.kilometres.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.hooliapps.kilometres.Graph
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.ads.AdsManager
import com.hooliapps.kilometres.ads.RewardedAds
import com.hooliapps.kilometres.core.RewardedUnlock
import com.hooliapps.kilometres.ui.LocalSnackbar
import kotlinx.coroutines.launch

/**
 * Le bouton « regarder une annonce » qui debloque une fonction pour un temps.
 * Absent tant que la publicite n'est pas autorisee (Pro, consentement refuse,
 * public protege) : on ne propose jamais une annonce qu'on ne peut pas servir.
 */
@Composable
fun RewardedButton(unlock: RewardedUnlock) {
	if (!AdsManager.canRequestAds) return
	// Plafond atteint (cinq trajets deja accordes ce mois-ci) ou Pro : pas de bouton.
	if (!Graph.state.currentEntitlement.canGrant(unlock)) return
	val activity = LocalActivity.current ?: return
	val snackbar = LocalSnackbar.current
	val scope = rememberCoroutineScope()
	val unavailable = stringResource(R.string.rewarded_unavailable)
	val granted = stringResource(R.string.rewarded_granted)
	val label = stringResource(when (unlock) {
		RewardedUnlock.ESTIMATION_DAY -> R.string.rewarded_estimation
		RewardedUnlock.EXPORT_HOUR -> R.string.rewarded_export
		RewardedUnlock.FIVE_TRIPS -> R.string.rewarded_trips
	})
	OutlinedButton(onClick = {
		RewardedAds.showIfAvailable(activity) { earned ->
			if (earned) {
				Graph.state.grant(unlock)
				scope.launch { snackbar.showSnackbar(granted) }
			} else {
				scope.launch { snackbar.showSnackbar(unavailable) }
			}
		}
	}) {
		Icon(Icons.Filled.PlayCircle, contentDescription = null)
		Text(" $label")
	}
}
