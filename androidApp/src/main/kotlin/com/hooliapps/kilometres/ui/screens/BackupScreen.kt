package com.hooliapps.kilometres.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.cloud.SystemBackup
import com.hooliapps.kilometres.core.Feature
import com.hooliapps.kilometres.core.backup.BackupCodec
import com.hooliapps.kilometres.export.Exporter
import com.hooliapps.kilometres.ui.AppViewModel
import com.hooliapps.kilometres.ui.LocalSnackbar
import com.hooliapps.kilometres.ui.components.ProLockedCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sauvegarde, gratuite : fichier a deposer ou l'on veut, et sauvegarde du systeme. Seule la restauration d'un fichier est Pro. */
@Composable
fun BackupScreen(vm: AppViewModel) {
	val entitlement by vm.state.entitlement.collectAsState()
	val context = LocalContext.current
	val snackbar = LocalSnackbar.current
	val scope = rememberCoroutineScope()
	val restoredFmt = stringResource(R.string.backup_restored)
	val invalid = stringResource(R.string.backup_invalid)
	val done = stringResource(R.string.cloud_done)

	val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult
		scope.launch {
			val incoming = withContext(Dispatchers.IO) {
				val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
				BackupCodec.decode(text)
			}
			if (incoming == null) {
				snackbar.showSnackbar(invalid)
			} else {
				val merged = BackupCodec.merge(vm.state.current, incoming)
				vm.state.replaceData(merged)
				snackbar.showSnackbar(restoredFmt.format(merged.trips.size))
			}
		}
	}

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
		Text(stringResource(R.string.backup_explain))
		Spacer(Modifier.height(16.dp))
		Button(onClick = { scope.launch { Exporter.shareBackup(context, vm.state.current) } }, modifier = Modifier.fillMaxWidth()) {
			Icon(Icons.Filled.UploadFile, contentDescription = null)
			Text(" " + stringResource(R.string.backup_export))
		}
		Spacer(Modifier.height(8.dp))
		if (entitlement.allows(Feature.BACKUP_RESTORE)) {
			OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
				Icon(Icons.Filled.Restore, contentDescription = null)
				Text(" " + stringResource(R.string.backup_restore))
			}
			Text(stringResource(R.string.backup_restore_help), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
		} else {
			ProLockedCard()
		}
		HorizontalDivider(Modifier.padding(vertical = 24.dp))
		Text(stringResource(R.string.cloud_title), style = MaterialTheme.typography.titleMedium)
		Spacer(Modifier.height(8.dp))
		Text(stringResource(R.string.cloud_explain))
		Spacer(Modifier.height(4.dp))
		Text(stringResource(R.string.cloud_system_restore), style = MaterialTheme.typography.bodySmall)
		Spacer(Modifier.height(8.dp))
		FilledTonalButton(onClick = {
			SystemBackup.requestBackup(context)
			scope.launch { snackbar.showSnackbar(done) }
		}, modifier = Modifier.fillMaxWidth()) {
			Icon(Icons.Outlined.CloudUpload, contentDescription = null)
			Text(" " + stringResource(R.string.cloud_now))
		}
	}
}
