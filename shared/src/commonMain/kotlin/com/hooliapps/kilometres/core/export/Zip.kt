package com.hooliapps.kilometres.core.export

/**
 * Ecriture d'une archive ZIP sans compression (entrees « stored »), en Kotlin
 * commun : c'est tout ce qu'il faut pour un classeur tableur, qui n'est
 * qu'un ZIP de fichiers XML. Aucune dependance, le meme code sur toutes les
 * plateformes.
 */
internal object Zip {

	private val CRC_TABLE = IntArray(256) { n ->
		var c = n
		repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
		c
	}

	fun crc32(bytes: ByteArray): Int {
		var c = -1
		for (b in bytes) c = CRC_TABLE[(c xor b.toInt()) and 0xFF] xor (c ushr 8)
		return c xor -1
	}

	fun build(entries: List<Pair<String, ByteArray>>): ByteArray {
		val out = ByteBuilder()
		val central = ByteBuilder()
		for ((name, data) in entries) {
			val nameBytes = name.encodeToByteArray()
			val crc = crc32(data)
			val offset = out.size
			// En-tete local.
			out.int(0x04034b50); out.short(20); out.short(0x0800); out.short(0); out.short(0); out.short(0)
			out.int(crc); out.int(data.size); out.int(data.size); out.short(nameBytes.size); out.short(0)
			out.bytes(nameBytes); out.bytes(data)
			// Repertoire central.
			central.int(0x02014b50); central.short(20); central.short(20); central.short(0x0800); central.short(0)
			central.short(0); central.short(0); central.int(crc); central.int(data.size); central.int(data.size)
			central.short(nameBytes.size); central.short(0); central.short(0); central.short(0); central.short(0)
			central.int(0); central.int(offset); central.bytes(nameBytes)
		}
		val centralOffset = out.size
		out.bytes(central.toByteArray())
		out.int(0x06054b50); out.short(0); out.short(0); out.short(entries.size); out.short(entries.size)
		out.int(central.size); out.int(centralOffset); out.short(0)
		return out.toByteArray()
	}

	private class ByteBuilder {
		private var buf = ByteArray(4096)
		var size = 0
			private set

		private fun ensure(n: Int) {
			if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
		}

		fun byte(v: Int) { ensure(1); buf[size++] = v.toByte() }
		fun short(v: Int) { byte(v and 0xFF); byte((v ushr 8) and 0xFF) }
		fun int(v: Int) { short(v and 0xFFFF); short((v ushr 16) and 0xFFFF) }
		fun bytes(b: ByteArray) { ensure(b.size); b.copyInto(buf, size); size += b.size }
		fun toByteArray(): ByteArray = buf.copyOf(size)
	}
}
