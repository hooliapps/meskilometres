package com.hooliapps.kilometres.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

/** Les exports rendent des tableaux d'octets Kotlin ; Swift veut un NSData. */
@OptIn(ExperimentalForeignApi::class)
fun bytesToNSData(bytes: ByteArray): NSData = bytes.usePinned { pinned ->
	NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
}
