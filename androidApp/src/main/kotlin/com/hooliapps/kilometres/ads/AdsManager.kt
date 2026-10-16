package com.hooliapps.kilometres.ads

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AgeRestrictedTreatment
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.hooliapps.kilometres.BuildConfig
import com.hooliapps.kilometres.core.Features
import com.hooliapps.kilometres.core.ads.AdPlan
import com.hooliapps.kilometres.core.ads.AdTreatment
import com.hooliapps.kilometres.core.ads.Audience
import com.hooliapps.kilometres.core.ads.plan
import com.hooliapps.kilometres.core.ads.shouldAskConsent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Mise en route de la publicite, dans l'ordre qu'imposent les regles, repris
 * de QuanGame :
 *
 *   1. SIGNAUX D'AGE : arret immediat si l'utilisateur est sous l'audience.
 *   2. PRO : arret immediat, et pour toujours tant que le droit est actif.
 *      Un Pro ne voit ni formulaire ni annonce.
 *   3. FORMULAIRE DE CONSENTEMENT, decide par la plateforme selon le pays.
 *   4. INITIALISATION DU SDK avec la configuration de requete.
 *   5. PRECHARGEMENT des deux formats.
 */
object AdsManager {
	private const val TAG = "AdsManager"
	private const val MAX_ATTEMPTS = 4
	private const val RETRY_DELAY_MS = 5_000L
	private const val RECHECK_AFTER_MS = 12 * 60 * 60 * 1_000L
	private const val CONSENT_INFO_TIMEOUT_MS = 30_000L
	private const val CONSENT_FORM_TIMEOUT_MS = 10 * 60_000L

	@Volatile private var starting = false
	@Volatile private var initialisedFor: Audience? = null
	@Volatile private var settledAt: Long = Long.MIN_VALUE

	var audience: Audience by mutableStateOf(Audience.PENDING)
		private set

	/** Public protege ET verdict etabli : pour ce que l'utilisateur LIT. */
	val protectedAudienceKnown: Boolean get() = audience.settled && !audience.adsAllowed

	var current: AdPlan = plan(Audience.PENDING, consentGranted = false)
		private set

	var canRequestAds by mutableStateOf(false)
		private set

	/**
	 * Saveur sans services Google (AppGallery) : ni SDK publicitaire, ni
	 * signaux d'age, ni consentement. Tout reste eteint, sans message : il
	 * n'y a rien a expliquer a l'utilisateur, et rien a lui demander.
	 */
	private val storeWithoutGoogleServices: Boolean = BuildConfig.STORE == "huawei"

	suspend fun start(activity: Activity, isPro: Boolean) {
		if (!Features.ADS || starting || storeWithoutGoogleServices) return
		if (isPro) { shutdownForPro(); return }
		if (audience.settled && !isStale() && chaineTerminee()) return
		starting = true
		try {
			run(activity)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Log.e(TAG, "chaine publicitaire interrompue, publicite coupee", e)
			shutdownAds()
		} finally {
			starting = false
		}
	}

	/** Un Pro n'a plus de publicite : on coupe tout, et rien ne redemarre tant qu'il l'est. */
	fun shutdownForPro() {
		current = plan(audience, consentGranted = false, isPro = true)
		shutdownAds()
	}

	private fun isStale(): Boolean = SystemClock.elapsedRealtime() - settledAt > RECHECK_AFTER_MS

	private fun chaineTerminee(): Boolean = initialisedFor != null || !shouldAskConsent(audience)

	private suspend fun run(activity: Activity) {
		val verdict = resolveWithRetries(activity)
		audience = verdict
		if (verdict.settled) settledAt = SystemClock.elapsedRealtime()
		Log.i(TAG, "public : ${verdict.source} (publicite=${verdict.adsAllowed}, definitif=${verdict.settled})")

		if (!shouldAskConsent(verdict)) {
			current = plan(verdict, consentGranted = false)
			shutdownAds()
			return
		}
		val previous = initialisedFor
		if (previous != null) {
			if (previous.treatment != verdict.treatment || previous.maxAdContentRating != verdict.maxAdContentRating) shutdownAds()
			return
		}
		val granted = requestConsent(activity, verdict)
		val decision = plan(verdict, granted)
		current = decision
		canRequestAds = decision.adsAllowed
		if (!decision.initialiseSdk) return
		initialize(activity, decision)
		initialisedFor = verdict
	}

	private suspend fun resolveWithRetries(activity: Activity): Audience {
		var verdict = AgeSignals.resolve(activity)
		var attempt = 1
		while (!verdict.settled && attempt < MAX_ATTEMPTS) {
			audience = verdict
			delay(RETRY_DELAY_MS)
			attempt++
			verdict = AgeSignals.resolve(activity)
		}
		return verdict
	}

	private fun shutdownAds() {
		canRequestAds = false
		InterstitialAds.destroy()
		RewardedAds.destroy()
	}

	private suspend fun initialize(activity: Activity, decision: AdPlan) {
		val appContext = activity.applicationContext
		val configuration = requestConfiguration(decision)
		val config = InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID).setRequestConfiguration(configuration).build()
		withContext(Dispatchers.IO) {
			suspendCancellableCoroutine { cont ->
				MobileAds.initialize(appContext, config) { if (cont.isActive) cont.resume(Unit) }
			}
		}
		MobileAds.setRequestConfiguration(configuration)
		Log.i(TAG, "SDK publicitaire pret, note max=${decision.maxAdContentRating}")
		if (decision.preload) {
			InterstitialAds.startPreload()
			RewardedAds.startPreload()
		}
	}

	private fun requestConfiguration(decision: AdPlan): RequestConfiguration {
		val treatment = when (decision.treatment) {
			AdTreatment.CHILD -> AgeRestrictedTreatment.CHILD
			AdTreatment.TEEN -> AgeRestrictedTreatment.TEEN
			AdTreatment.UNRESTRICTED -> AgeRestrictedTreatment.UNSPECIFIED
		}
		val rating = when (decision.maxAdContentRating) {
			"G" -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_G
			"PG" -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_PG
			"T" -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_T
			else -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_G
		}
		return RequestConfiguration.Builder().setAgeRestrictedTreatment(treatment).setMaxAdContentRating(rating).build()
	}

	private suspend fun requestConsent(activity: Activity, verdict: Audience): Boolean {
		val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)
		val updated = withTimeoutOrNull(CONSENT_INFO_TIMEOUT_MS) {
			suspendCancellableCoroutine { cont ->
				val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(verdict.underAgeOfConsent).build()
				info.requestConsentInfoUpdate(activity, params,
					{ if (cont.isActive) cont.resume(true) },
					{ e -> Log.w(TAG, "consentement indisponible : ${e.message}"); if (cont.isActive) cont.resume(false) })
			}
		} ?: false
		if (!updated) return false
		return withTimeoutOrNull(CONSENT_FORM_TIMEOUT_MS) {
			suspendCancellableCoroutine { cont ->
				UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
					if (formError != null) Log.w(TAG, "formulaire : ${formError.message}")
					if (cont.isActive) cont.resume(info.canRequestAds())
				}
			}
		} ?: false
	}

	fun showPrivacyOptions(activity: Activity) {
		if (!audience.adsAllowed) return
		UserMessagingPlatform.showPrivacyOptionsForm(activity) { e -> if (e != null) Log.w(TAG, "options de confidentialite : ${e.message}") }
	}

	fun privacyOptionsAvailable(activity: Activity): Boolean {
		if (!audience.adsAllowed) return false
		return UserMessagingPlatform.getConsentInformation(activity).privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
	}
}
