package com.hooliapps.kilometres.core

import java.io.File
import java.time.Instant
import java.time.ZoneId

actual object Platform {
	actual fun nowEpochMillis(): Long = System.currentTimeMillis()

	actual fun localDayOf(epochMillis: Long): Day {
		val d = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
		return Day(d.year, d.monthValue, d.dayOfMonth)
	}

	actual val name: String = System.getProperty("os.name") ?: "jvm"
}

actual class PlatformFiles actual constructor(actual val directory: String) {
	private fun file(name: String) = File(directory, name)

	actual fun exists(name: String): Boolean = file(name).exists()

	actual fun readText(name: String): String? = file(name).takeIf { it.exists() }?.readText(Charsets.UTF_8)

	actual fun writeTextAtomic(name: String, content: String) {
		val target = file(name)
		target.parentFile?.mkdirs()
		val tmp = File(directory, "$name.tmp")
		tmp.writeText(content, Charsets.UTF_8)
		if (!tmp.renameTo(target)) {
			// Windows refuse parfois le renommage par-dessus un fichier existant.
			target.delete()
			if (!tmp.renameTo(target)) throw java.io.IOException("renommage impossible : ${target.path}")
		}
	}

	actual fun copy(name: String, toName: String) {
		file(name).copyTo(file(toName), overwrite = true)
	}

	actual fun lastModifiedEpochMillis(name: String): Long? = file(name).takeIf { it.exists() }?.lastModified()
}
