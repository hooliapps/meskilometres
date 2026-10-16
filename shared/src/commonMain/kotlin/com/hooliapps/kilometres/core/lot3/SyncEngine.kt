package com.hooliapps.kilometres.core.lot3

import com.hooliapps.kilometres.core.AppData
import com.hooliapps.kilometres.core.Features
import com.hooliapps.kilometres.core.backup.BackupCodec

/** Un dossier choisi par l'utilisateur dans son cloud : lecture et ecriture d'un fichier. */
interface SyncFolder {
	fun read(): String?
	fun write(content: String)
}

/**
 * LOT 3, INACTIF. Synchronisation sans compte ni serveur : chaque appareil
 * fusionne le fichier du dossier avec ses donnees, et le reecrit. La fusion
 * est celle de [BackupCodec.merge], deja testee.
 */
class SyncEngine(private val folder: SyncFolder) {
	fun sync(local: AppData): AppData {
		if (!Features.MULTI_DEVICE_SYNC) return local
		val remote = folder.read()?.let(BackupCodec::decode)
		val merged = if (remote == null) local else BackupCodec.merge(local, remote)
		folder.write(BackupCodec.encode(merged))
		return merged
	}
}

class MemorySyncFolder(var content: String? = null) : SyncFolder {
	override fun read() = content
	override fun write(content: String) { this.content = content }
}
