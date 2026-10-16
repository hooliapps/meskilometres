package com.hooliapps.kilometres.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.ui.LocalNav
import com.hooliapps.kilometres.ui.Screen

/** Carte « reserve au Pro » avec le bouton vers l'abonnement, et l'essai par annonce s'il y en a un. */
@Composable
fun ProLockedCard(message: String = stringResource(R.string.estimation_locked), rewarded: (@Composable () -> Unit)? = null) {
	val nav = LocalNav.current
	Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
		Column(Modifier.padding(16.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Icon(Icons.Filled.WorkspacePremium, contentDescription = null)
				Spacer(Modifier.width(8.dp))
				Text(stringResource(R.string.pro_title), style = MaterialTheme.typography.titleMedium)
			}
			Spacer(Modifier.height(8.dp))
			Text(message)
			Spacer(Modifier.height(12.dp))
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
				rewarded?.invoke()
				Spacer(Modifier.width(8.dp))
				Button(onClick = { nav.open(Screen.Pro) }) { Text(stringResource(R.string.go_pro)) }
			}
		}
	}
}

@Composable
fun DisclaimerBanner(long: Boolean = false) {
	Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
		Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
			Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.padding(top = 2.dp))
			Spacer(Modifier.width(8.dp))
			Text(stringResource(if (long) R.string.disclaimer_long else R.string.disclaimer_short), style = MaterialTheme.typography.bodyMedium)
		}
	}
}

@Composable
fun KeyValueRow(label: String, value: String, bold: Boolean = false) {
	val style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
	Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
		Text(label, style = style, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
		Spacer(Modifier.width(12.dp))
		Text(value, style = style, fontWeight = if (bold) FontWeight.Bold else null)
	}
}

@Composable
fun ConfirmDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.confirm_delete)) },
		confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
		dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
	)
}
