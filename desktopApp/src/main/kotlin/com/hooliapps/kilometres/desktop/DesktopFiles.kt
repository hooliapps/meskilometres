package com.hooliapps.kilometres.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Boites de dialogue du systeme, pour enregistrer et ouvrir des fichiers. */
object DesktopFiles {
	fun save(bytes: ByteArray, suggestedName: String): Boolean {
		val dialog = FileDialog(null as Frame?, Strings["export"], FileDialog.SAVE).apply { file = suggestedName; isVisible = true }
		val dir = dialog.directory ?: return false
		val name = dialog.file ?: return false
		File(dir, name).writeBytes(bytes)
		return true
	}

	fun openText(): String? {
		val dialog = FileDialog(null as Frame?, Strings["import_pick"], FileDialog.LOAD).apply { isVisible = true }
		val dir = dialog.directory ?: return null
		val name = dialog.file ?: return null
		return File(dir, name).readText(Charsets.UTF_8)
	}

	/** Dossier de donnees de l'application, prive a l'utilisateur. */
	fun dataDirectory(): File {
		val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".local/share")
		return File(base, "Kilometres").apply { mkdirs() }
	}

	/** Dossier Documents, en general synchronise par le cloud de l'utilisateur. */
	fun documentsBackup(): File = File(File(System.getProperty("user.home"), "Documents"), "Kilometres").apply { mkdirs() }
}
