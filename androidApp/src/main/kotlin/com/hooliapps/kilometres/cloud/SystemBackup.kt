package com.hooliapps.kilometres.cloud

import android.app.backup.BackupManager
import android.content.Context

/**
 * Sauvegarde cloud sans compte : celle du systeme. Les deux fichiers de
 * donnees sont declares dans res/xml ; le systeme les copie sur le compte de
 * l'appareil et les restaure a la reinstallation. On ne peut que lui
 * signaler qu'il y a du nouveau, c'est ce que fait [requestBackup].
 */
object SystemBackup {
	fun requestBackup(context: Context) {
		runCatching { BackupManager(context.applicationContext).dataChanged() }
	}
}
