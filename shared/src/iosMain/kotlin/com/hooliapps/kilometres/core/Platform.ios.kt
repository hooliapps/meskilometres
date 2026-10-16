package com.hooliapps.kilometres.core

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

actual object Platform {
	actual fun nowEpochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

	actual fun localDayOf(epochMillis: Long): Day {
		val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
		val c = NSCalendar.currentCalendar.components(NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay, date)
		return Day(c.year.toInt(), c.month.toInt(), c.day.toInt())
	}

	actual val name: String = "iOS"
}

@OptIn(ExperimentalForeignApi::class)
actual class PlatformFiles actual constructor(actual val directory: String) {
	private fun path(name: String) = "$directory/$name"

	actual fun exists(name: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path(name))

	actual fun readText(name: String): String? =
		if (!exists(name)) null else NSString.stringWithContentsOfFile(path(name), NSUTF8StringEncoding, null)

	actual fun writeTextAtomic(name: String, content: String) {
		NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
		@Suppress("CAST_NEVER_SUCCEEDS")
		val ok = (content as NSString).writeToFile(path(name), true, NSUTF8StringEncoding, null)
		if (!ok) throw IllegalStateException("ecriture impossible : ${path(name)}")
	}

	actual fun copy(name: String, toName: String) {
		NSFileManager.defaultManager.removeItemAtPath(path(toName), null)
		NSFileManager.defaultManager.copyItemAtPath(path(name), path(toName), null)
	}

	actual fun lastModifiedEpochMillis(name: String): Long? {
		val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path(name), null) ?: return null
		val date = attrs["NSFileModificationDate"] as? NSDate ?: return null
		return (date.timeIntervalSince1970 * 1000).toLong()
	}
}
