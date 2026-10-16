package com.hooliapps.kilometres.core

import kotlinx.serialization.Serializable

/**
 * Ce que la version Pro debloque, et ce qui est gratuit pour tous.
 *
 * La sauvegarde (fichier, cloud du systeme, export JSON) est GRATUITE :
 * personne ne doit perdre ses donnees, abonne ou non. Seule la restauration
 * d'un fichier de sauvegarde (import JSON) reste Pro : ce n'est pas une
 * obligation du RGPD, dont le droit a la portabilite porte sur l'EXPORT
 * des donnees (article 20), pas sur leur reimport. La restauration par le
 * systeme (reinstallation, nouvel appareil) reste automatique et gratuite.
 */
enum class Feature(val free: Boolean = false) {
	UNLIMITED_TRIPS,
	ESTIMATION,
	EXPORT,
	IMPORT_HISTORY,
	/** Sauvegarde fichier, cloud et export JSON : pour tous. */
	CLOUD_BACKUP(free = true),
	/** Restauration d'un fichier de sauvegarde. */
	BACKUP_RESTORE,
	EXPENSES,
	WIDGETS,
	WATCH,
	// Lot 2
	GPS_RECORDING,
	CAR_CONNECTION,
	// Lot 3
	AUTO_DETECTION,
	RECEIPT_SCAN,
	MULTI_DEVICE_SYNC;

	/** La fonction existe-t-elle dans cette version de l'application ? */
	val available: Boolean
		get() = when (this) {
			GPS_RECORDING -> Features.GPS_RECORDING
			CAR_CONNECTION -> Features.CAR_CONNECTION
			AUTO_DETECTION -> Features.AUTO_DETECTION
			RECEIPT_SCAN -> Features.RECEIPT_SCAN
			MULTI_DEVICE_SYNC -> Features.MULTI_DEVICE_SYNC
			else -> true
		}
}

/** Identifiants des abonnements, identiques sur toutes les boutiques. */
object Products {
	const val MONTHLY = "kilometres_pro_monthly"
	const val YEARLY = "kilometres_pro_yearly"
	val ALL = setOf(MONTHLY, YEARLY)

	/** Prix de reference, affiches tant que la boutique n'a pas repondu. */
	const val MONTHLY_FALLBACK_PRICE = "4,99 €"
	const val YEARLY_FALLBACK_PRICE = "49,99 €"
}

/**
 * Ce qu'une annonce recompensee peut debloquer, pour un temps. C'est la
 * porte d'entree vers le Pro : on essaie une fonction, on voit sa valeur.
 *
 * Les durees sont des PLANCHERS de generosite : une recompense trop courte
 * se vit comme une arnaque et se paie en avis.
 */
enum class RewardedUnlock(val feature: Feature, val durationMs: Long) {
	/** L'estimation, lisible pendant 24 heures. */
	ESTIMATION_DAY(Feature.ESTIMATION, 24L * 60 * 60 * 1000),

	/** Un export, a faire dans l'heure. */
	EXPORT_HOUR(Feature.EXPORT, 60L * 60 * 1000),

	/** Cinq trajets de plus ce mois-ci. Pas une duree : un compteur, voir [Grants]. */
	FIVE_TRIPS(Feature.UNLIMITED_TRIPS, 0);

	val isCounter: Boolean get() = durationMs == 0L
}

/** Une recompense obtenue, et jusqu'a quand elle vaut. */
@Serializable
data class Grant(val unlock: RewardedUnlock, val untilEpochMillis: Long = 0, val monthKey: String = "", val count: Int = 0)

/**
 * Droit d'acces courant. Sans serveur : on fait confiance a la boutique.
 * Le droit Pro reste acquis tant que la boutique n'a pas dit explicitement
 * qu'aucun abonnement n'est actif ; hors ligne, rien ne change.
 *
 * Les recompenses publicitaires s'ajoutent par-dessus, bornees dans le temps
 * ou en nombre, et ne survivent pas a un passage en Pro, qui les rend inutiles.
 */
@Serializable
data class Entitlement(
	val pro: Boolean = false,
	val productId: String? = null,
	/** Derniere confirmation par la boutique, en millisecondes depuis 1970. */
	val confirmedAt: Long? = null,
	val grants: List<Grant> = emptyList()
) {
	val isActive: Boolean get() = pro

	/** Vrai si la fonction est disponible : gratuite, Pro ou recompense valable, ET fonction existante. */
	fun allows(feature: Feature, now: Long = Platform.nowEpochMillis()): Boolean {
		if (!feature.available) return false
		if (feature.free) return true
		if (pro) return true
		return grants.any { it.unlock.feature == feature && !it.unlock.isCounter && it.untilEpochMillis > now }
	}

	/** Pour Swift : la meme chose, a l'instant present. */
	fun allowsNow(feature: Feature): Boolean = allows(feature, Platform.nowEpochMillis())

	/** Trajets gratuits supplementaires accordes pour le mois de [day]. */
	fun bonusTrips(day: Day): Int =
		grants.filter { it.unlock == RewardedUnlock.FIVE_TRIPS && it.monthKey == monthKey(day) }.sumOf { it.count }

	/**
	 * Vrai si regarder une annonce pour [unlock] apporterait quelque chose.
	 * Faux pour un Pro, et faux pour les cinq trajets quand ils ont deja ete
	 * accordes ce mois-ci : la recompense est UNE fois par mois (dix trajets
	 * gratuits au plus), sans quoi la version gratuite remplacerait Pro a
	 * coups d'annonces. Les boutons « regarder une annonce » s'effacent quand
	 * c'est faux : on ne fait jamais regarder une annonce pour rien.
	 */
	fun canGrant(unlock: RewardedUnlock, day: Day = Day.today()): Boolean {
		if (pro) return false
		if (unlock == RewardedUnlock.FIVE_TRIPS) return bonusTrips(day) == 0
		return true
	}

	/** Pour Swift : la meme chose, aujourd'hui. */
	fun canGrantNow(unlock: RewardedUnlock): Boolean = canGrant(unlock, Day.today())

	/** Ajoute une recompense. Un Pro n'en a pas besoin, et un plafond atteint ne change rien. */
	fun withGrant(unlock: RewardedUnlock, now: Long = Platform.nowEpochMillis(), day: Day = Day.today()): Entitlement {
		if (!canGrant(unlock, day)) return this
		val grant = if (unlock.isCounter) {
			Grant(unlock, monthKey = monthKey(day), count = 5)
		} else {
			Grant(unlock, untilEpochMillis = now + unlock.durationMs)
		}
		return copy(grants = grants.filter { it.untilEpochMillis > now || it.unlock.isCounter } + grant)
	}

	/** Oublie ce qui est perime, pour garder le fichier petit. */
	fun pruned(now: Long = Platform.nowEpochMillis(), day: Day = Day.today()): Entitlement =
		copy(grants = grants.filter { (it.unlock.isCounter && it.monthKey == monthKey(day)) || (!it.unlock.isCounter && it.untilEpochMillis > now) })

	companion object {
		val FREE = Entitlement()

		fun monthKey(day: Day): String = "${day.year}-${day.month.toString().padStart(2, '0')}"
	}
}
