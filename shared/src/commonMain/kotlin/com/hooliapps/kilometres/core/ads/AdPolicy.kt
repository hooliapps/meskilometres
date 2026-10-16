package com.hooliapps.kilometres.core.ads

/**
 * Regles d'affichage des annonces, sans une ligne de plateforme. Reprises
 * de QuanGame : memes planchers, memes portiers, memes raisons.
 *
 * L'application est gratuite avec publicite, ou Pro sans aucune publicite.
 * Deux formats seulement :
 *
 *   - un interstitiel a la fin d'une saisie, jamais plus d'un toutes les
 *     [MIN_INTERSTITIAL_INTERVAL_MS] ;
 *   - une annonce recompensee, CHOISIE par l'utilisateur, qui debloque une
 *     fonction Pro pour un temps (voir RewardedUnlock). C'est le format qui
 *     rapporte le plus par vue, et il ne gene personne.
 *
 * Pas de banniere : une annonce permanente sous un formulaire que l'on
 * touche sans arret est un clic accidentel assure, que la regie sanctionne.
 */
const val MIN_INTERSTITIAL_INTERVAL_MS = 180_000L

/** Delai minimal entre deux chargements d'annonce : une minute. */
const val MIN_AD_LOAD_INTERVAL_MS = 60_000L

/**
 * Classification maximale des annonces imposee a toute l'application.
 *
 * « T » (adolescents) : l'audience declaree est 18 ans et plus, il s'agit
 * d'un outil de travail, mais aucune annonce reservee aux adultes n'a sa
 * place dans une application que l'on ouvre au bureau. La valeur du SDK
 * ECRASE celle de la console : c'est ici qu'elle se decide.
 */
const val APP_MAX_AD_CONTENT_RATING = "T"

/**
 * Portier des interstitiels. L'horloge est fournie par l'appelant, monotone
 * en production : avec l'heure murale, reculer la pendule rouvrirait le
 * droit d'afficher.
 */
class InterstitialGate(minIntervalMs: Long = MIN_INTERSTITIAL_INTERVAL_MS) {
	private val minIntervalMs: Long = minIntervalMs.coerceAtLeast(MIN_INTERSTITIAL_INTERVAL_MS)
	private var lastShownAt: Long = Long.MIN_VALUE
	private var showing = false

	fun allows(now: Long, adAvailable: Boolean, consentGranted: Boolean, isPro: Boolean): Boolean {
		if (isPro) return false
		if (!consentGranted) return false
		if (showing) return false
		if (!adAvailable) return false
		return elapsedSince(now) >= minIntervalMs
	}

	/** A appeler quand l'annonce APPARAIT, pas quand elle se ferme. */
	fun markShown(now: Long) {
		lastShownAt = now
		showing = true
	}

	fun markClosed() {
		showing = false
	}

	fun elapsedSince(now: Long): Long {
		if (lastShownAt == Long.MIN_VALUE) return Long.MAX_VALUE
		return (now - lastShownAt).coerceAtLeast(0)
	}
}

/** Anti-emballement des requetes publicitaires : voir QuanGame. */
class LoadThrottle(minIntervalMs: Long = MIN_AD_LOAD_INTERVAL_MS) {
	private val minIntervalMs: Long = minIntervalMs.coerceAtLeast(MIN_AD_LOAD_INTERVAL_MS)
	private var lastAttemptAt: Long = Long.MIN_VALUE

	fun allow(now: Long): Boolean {
		if (lastAttemptAt != Long.MIN_VALUE && now - lastAttemptAt < minIntervalMs) return false
		lastAttemptAt = now
		return true
	}
}

/** Traitement publicitaire lie a l'age, decouple du SDK pour rester testable. */
enum class AdTreatment { UNRESTRICTED, TEEN, CHILD }

/**
 * Ce que l'on sait du public d'une session. L'audience declaree est 18 ans
 * et plus ; un signal d'age qui passe en dessous coupe TOUT : ni formulaire,
 * ni SDK, ni annonce, parce que meme une annonce non personnalisee fait
 * remonter l'identifiant publicitaire.
 */
data class Audience(
	val adsAllowed: Boolean = true,
	val treatment: AdTreatment = AdTreatment.UNRESTRICTED,
	val maxAdContentRating: String = APP_MAX_AD_CONTENT_RATING,
	val underAgeOfConsent: Boolean = false,
	val source: String = "defaut",
	/** Conclusion definitive pour cette session, ou provisoire ? */
	val settled: Boolean = true
) {
	companion object {
		val GENERAL = Audience()
		val PENDING = blocked("en attente", settled = false)

		fun blocked(source: String, settled: Boolean = true) = Audience(
			adsAllowed = false, treatment = AdTreatment.CHILD, maxAdContentRating = "G",
			underAgeOfConsent = true, source = source, settled = settled
		)

		/** Plancher de l'audience declaree. */
		const val MIN_AGE = 18

		/**
		 * Classement d'une fourchette d'age (bornes incluses, nulles si
		 * inconnues) : sous le plancher, tout est coupe ; sinon grand public.
		 * Les signaux d'age ne REGLENT jamais la diffusion, ils ne font que
		 * bloquer, conformement a leurs conditions d'usage.
		 */
		fun classify(ageLower: Int?, ageUpper: Int?, source: String): Audience = when {
			ageUpper != null && ageUpper < MIN_AGE -> blocked("$source : $ageLower-$ageUpper")
			ageLower != null && ageLower < MIN_AGE && (ageUpper == null || ageUpper < MIN_AGE) -> blocked("$source : $ageLower-$ageUpper")
			else -> Audience(source = source)
		}
	}
}

/** Ce que la chaine publicitaire a le droit de faire, decide sans plateforme. */
data class AdPlan(
	val askConsent: Boolean,
	val initialiseSdk: Boolean,
	val preload: Boolean,
	val adsAllowed: Boolean,
	val tagForUnderAgeOfConsent: Boolean,
	val treatment: AdTreatment,
	val maxAdContentRating: String
)

/** Faut-il seulement PRESENTER le formulaire a ce public ? */
fun shouldAskConsent(audience: Audience): Boolean = audience.settled && audience.adsAllowed

/**
 * La decision complete. Un Pro n'a JAMAIS de publicite : ni formulaire, ni
 * SDK, ni requete. C'est la promesse de la version payante, et c'est ici
 * qu'elle se tient.
 */
fun plan(audience: Audience, consentGranted: Boolean, isPro: Boolean = false): AdPlan {
	if (isPro || !shouldAskConsent(audience)) {
		return AdPlan(
			askConsent = false, initialiseSdk = false, preload = false, adsAllowed = false,
			tagForUnderAgeOfConsent = audience.underAgeOfConsent, treatment = audience.treatment,
			maxAdContentRating = audience.maxAdContentRating
		)
	}
	return AdPlan(
		askConsent = true,
		initialiseSdk = consentGranted,
		preload = consentGranted,
		adsAllowed = consentGranted,
		tagForUnderAgeOfConsent = false,
		treatment = audience.treatment,
		maxAdContentRating = audience.maxAdContentRating
	)
}
