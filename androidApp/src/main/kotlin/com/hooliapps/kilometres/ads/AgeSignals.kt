package com.hooliapps.kilometres.ads

import android.app.Activity
import android.util.Log
import com.google.android.play.agesignals.AgeSignalsAccessRequest
import com.google.android.play.agesignals.AgeSignalsManager
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.model.AgeSignalsStatus
import com.google.android.play.agesignals.model.SignificantChangeStatus
import com.hooliapps.kilometres.core.ads.Audience
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Interrogation des signaux d'age de la boutique, reprise de QuanGame.
 *
 * L'audience declaree est 18 ans et plus. Sous ce plancher, tout est coupe :
 * ni formulaire, ni SDK, ni annonce. Les signaux BLOQUENT, ils ne reglent
 * jamais la diffusion, conformement a leurs conditions d'usage. En cas de
 * doute ou d'erreur, on retombe sur le grand public ; un service MUET est
 * un cas a part : rien ne part tant qu'il n'a pas repondu.
 */
object AgeSignals {
	private const val TAG = "AgeSignals"
	const val TIMEOUT_MS = 10_000L

	suspend fun resolve(activity: Activity): Audience = try {
		withTimeout(TIMEOUT_MS) { query(activity) }
	} catch (e: CancellationException) {
		if (e !is TimeoutCancellationException) throw e
		Log.i(TAG, "pas de reponse en $TIMEOUT_MS ms : tout reste ferme, nouvel essai a suivre")
		Audience.blocked("delai depasse", settled = false)
	} catch (e: Exception) {
		Log.i(TAG, "signaux indisponibles (${e.javaClass.simpleName}), grand public")
		Audience.GENERAL.copy(source = "indisponible")
	}

	private suspend fun query(activity: Activity): Audience {
		val manager: AgeSignalsManager = AgeSignalsManagerFactory.create(activity)
		val access = manager.requestAgeSignalsAccess(AgeSignalsAccessRequest.builder().setActivity(activity).build()).await()
		if (access.ageSignalsStatus() != AgeSignalsStatus.SHARED) return Audience.GENERAL.copy(source = "age non partage")
		val signals = manager.checkAgeSignals(AgeSignalsRequest.builder().build()).await()
		return classify(signals.ageLower(), signals.ageUpper(), signals.significantChangeStatus())
	}

	fun classify(ageLower: Int?, ageUpper: Int?, significantChangeStatus: Int?): Audience {
		if (significantChangeStatus == SignificantChangeStatus.DECLINED) return Audience.blocked("version refusee par le parent")
		if (significantChangeStatus == SignificantChangeStatus.PENDING) return Audience.blocked("approbation parentale en attente")
		return Audience.classify(ageLower, ageUpper, "signaux d'age")
	}
}
