package com.hooliapps.kilometres.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdPreloader
import com.hooliapps.kilometres.BuildConfig
import com.hooliapps.kilometres.core.ads.InterstitialGate
import com.hooliapps.kilometres.core.ads.MIN_INTERSTITIAL_INTERVAL_MS

/**
 * Blocs d'annonces : un interstitiel apres une saisie, une annonce
 * recompensee pour essayer une fonction Pro. Pas de banniere.
 *
 * En debogage, les blocs de demonstration publics. En production, A
 * REMPLACER par les blocs crees dans la console pour cette application.
 */
object AdUnits {
	val INTERSTITIAL: String get() = if (BuildConfig.DEBUG) DEMO_INTERSTITIAL else REAL_INTERSTITIAL
	val REWARDED: String get() = if (BuildConfig.DEBUG) DEMO_REWARDED else REAL_REWARDED

	private const val REAL_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
	private const val REAL_REWARDED = "ca-app-pub-3940256099942544/5224354917"
	private const val DEMO_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
	private const val DEMO_REWARDED = "ca-app-pub-3940256099942544/5224354917"
}

private val mainHandler = Handler(Looper.getMainLooper())

private fun onMain(action: () -> Unit) {
	if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
}

/** Interstitiels, servis depuis le prechargeur du SDK. Voir QuanGame. */
object InterstitialAds {
	private const val TAG = "InterstitialAds"
	private const val BUFFER_SIZE = 1
	private const val PRELOAD_ID = "interstitiel-apres-saisie"

	private val gate = InterstitialGate(MIN_INTERSTITIAL_INTERVAL_MS)

	@Volatile private var preloadStarted = false

	fun startPreload() {
		if (preloadStarted || !AdsManager.canRequestAds) return
		preloadStarted = true
		val accepted = InterstitialAdPreloader.start(PRELOAD_ID, PreloadConfiguration(AdRequest.Builder(AdUnits.INTERSTITIAL).build(), BUFFER_SIZE), object : PreloadCallback {
			override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) { Log.i(TAG, "annonce prete") }
			override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) { Log.w(TAG, "prechargement refuse : ${adError.message}") }
			override fun onAdsExhausted(preloadId: String) { Log.i(TAG, "tampon epuise") }
		})
		if (!accepted) Log.w(TAG, "le SDK a refuse de demarrer le prechargement")
	}

	fun canShow(isPro: Boolean): Boolean {
		val available = preloadStarted && runCatching { InterstitialAdPreloader.isAdAvailable(PRELOAD_ID) }.getOrDefault(false)
		return gate.allows(SystemClock.elapsedRealtime(), available, AdsManager.canRequestAds, isPro)
	}

	/** Affiche un interstitiel s'il est autorise ; [onClosed] est appele dans tous les cas. */
	fun showIfAllowed(activity: Activity, isPro: Boolean, onClosed: () -> Unit = {}) {
		if (!canShow(isPro)) { onClosed(); return }
		val ad = InterstitialAdPreloader.pollAd(PRELOAD_ID) ?: run { onClosed(); return }
		ad.adEventCallback = object : InterstitialAdEventCallback {
			override fun onAdDismissedFullScreenContent() = onMain { gate.markClosed(); onClosed() }
			override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) = onMain { gate.markClosed(); onClosed() }
			override fun onAdShowedFullScreenContent() = onMain { gate.markShown(SystemClock.elapsedRealtime()) }
		}
		runCatching { ad.show(activity) }.onFailure { gate.markClosed(); onClosed() }
	}

	fun destroy() {
		runCatching { InterstitialAdPreloader.destroy(PRELOAD_ID) }
		preloadStarted = false
		gate.markClosed()
	}
}

/**
 * Annonces recompensees : l'utilisateur les demande, jamais l'inverse. La
 * recompense (voir RewardedUnlock) est accordee par l'appelant quand le SDK
 * confirme que l'annonce a ete regardee jusqu'au bout.
 */
object RewardedAds {
	private const val TAG = "RewardedAds"
	private const val PRELOAD_ID = "recompense-essai-pro"

	@Volatile private var preloadStarted = false
	@Volatile private var showing = false

	fun startPreload() {
		if (preloadStarted || !AdsManager.canRequestAds) return
		preloadStarted = true
		val accepted = RewardedAdPreloader.start(PRELOAD_ID, PreloadConfiguration(AdRequest.Builder(AdUnits.REWARDED).build(), 1), object : PreloadCallback {
			override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) { Log.i(TAG, "annonce recompensee prete") }
			override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) { Log.w(TAG, "prechargement refuse : ${adError.message}") }
			override fun onAdsExhausted(preloadId: String) { Log.i(TAG, "tampon epuise") }
		})
		if (!accepted) Log.w(TAG, "le SDK a refuse de demarrer le prechargement")
	}

	fun isAvailable(): Boolean = preloadStarted && !showing && runCatching { RewardedAdPreloader.isAdAvailable(PRELOAD_ID) }.getOrDefault(false)

	/** Affiche l'annonce si elle est prete ; [onResult] recoit vrai si la recompense est gagnee. */
	fun showIfAvailable(activity: Activity, onResult: (Boolean) -> Unit) {
		if (!AdsManager.canRequestAds || !isAvailable()) { onResult(false); return }
		val ad = RewardedAdPreloader.pollAd(PRELOAD_ID) ?: run { onResult(false); return }
		var earned = false
		showing = true
		ad.adEventCallback = object : RewardedAdEventCallback {
			override fun onAdDismissedFullScreenContent() = onMain { showing = false; onResult(earned) }
			override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) = onMain { showing = false; onResult(false) }
		}
		runCatching { ad.show(activity) { earned = true } }.onFailure { showing = false; onResult(false) }
	}

	fun destroy() {
		runCatching { RewardedAdPreloader.destroy(PRELOAD_ID) }
		preloadStarted = false
		showing = false
	}
}
