package com.hooliapps.kilometres.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.hooliapps.kilometres.core.Entitlement
import com.hooliapps.kilometres.core.Products
import com.hooliapps.kilometres.core.store.AppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Abonnements Pro par la boutique. Sans serveur : la boutique est la seule
 * source de verite, et on lui redemande a chaque connexion. Une reponse
 * reussie sans abonnement actif retire le droit Pro ; une reponse absente
 * (hors ligne) ne change rien.
 *
 * Sur un appareil sans la boutique (saveurs xiaomi et huawei sans services),
 * la connexion echoue et l'ecran Pro l'explique : l'application reste
 * utilisable en gratuit.
 */
class Purchases(private val context: Context, private val state: AppState) : PurchasesUpdatedListener {

	enum class Status { IDLE, CONNECTING, READY, UNAVAILABLE, PENDING, FAILED }

	/** Un abonnement tel que la boutique le decrit : prix formate et jeton d'offre. */
	data class Plan(val id: String, val price: String, val offerToken: String, val details: ProductDetails)

	private val _status = MutableStateFlow(Status.IDLE)
	val status: StateFlow<Status> = _status.asStateFlow()

	private val _products = MutableStateFlow<Map<String, Plan>>(emptyMap())
	val products: StateFlow<Map<String, Plan>> = _products.asStateFlow()

	private val client: BillingClient = BillingClient.newBuilder(context)
		.setListener(this)
		.enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
		.enableAutoServiceReconnection()
		.build()

	fun connect() {
		if (client.isReady || _status.value == Status.CONNECTING) return
		_status.value = Status.CONNECTING
		client.startConnection(object : BillingClientStateListener {
			override fun onBillingSetupFinished(result: BillingResult) {
				if (result.responseCode != BillingClient.BillingResponseCode.OK) {
					Log.w(TAG, "boutique indisponible : ${result.debugMessage}")
					_status.value = Status.UNAVAILABLE
					return
				}
				_status.value = Status.READY
				queryProducts()
				checkActiveSubscriptions(revokeIfNone = true)
			}

			override fun onBillingServiceDisconnected() {
				// La reconnexion automatique est activee ; rien a faire.
			}
		})
	}

	private fun queryProducts() {
		val params = QueryProductDetailsParams.newBuilder().setProductList(Products.ALL.map {
			QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
		}).build()
		client.queryProductDetailsAsync(params) { result, details ->
			if (result.responseCode != BillingClient.BillingResponseCode.OK) {
				Log.w(TAG, "produits illisibles : ${result.debugMessage}")
				return@queryProductDetailsAsync
			}
			val plans = details.productDetailsList.mapNotNull { pd ->
				// Le plan de base le moins cher : il n'y en a qu'un par produit.
				val offer = pd.subscriptionOfferDetails?.minByOrNull { o -> o.pricingPhases.pricingPhaseList.lastOrNull()?.priceAmountMicros ?: Long.MAX_VALUE } ?: return@mapNotNull null
				val price = offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice ?: return@mapNotNull null
				pd.productId to Plan(pd.productId, price, offer.offerToken, pd)
			}.toMap()
			_products.value = plans
		}
	}

	fun buy(activity: Activity, productId: String) {
		val plan = _products.value[productId] ?: return
		val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
			BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(plan.details).setOfferToken(plan.offerToken).build()
		)).build()
		val result = client.launchBillingFlow(activity, params)
		if (result.responseCode != BillingClient.BillingResponseCode.OK) {
			Log.w(TAG, "achat refuse : ${result.debugMessage}")
			_status.value = Status.FAILED
		} else {
			_status.value = Status.PENDING
		}
	}

	override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
		when (result.responseCode) {
			BillingClient.BillingResponseCode.OK -> {
				purchases?.forEach(::handle)
				_status.value = Status.READY
			}
			BillingClient.BillingResponseCode.USER_CANCELED -> _status.value = Status.READY
			BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
				checkActiveSubscriptions(revokeIfNone = false)
				_status.value = Status.READY
			}
			else -> {
				Log.w(TAG, "achat : ${result.responseCode} ${result.debugMessage}")
				_status.value = Status.FAILED
			}
		}
	}

	private fun handle(p: Purchase) {
		if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
		val product = p.products.firstOrNull { it in Products.ALL } ?: return
		if (!p.isAcknowledged) {
			client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r ->
				if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "accuse de reception refuse : ${r.debugMessage}")
			}
		}
		state.setEntitlement(Entitlement(pro = true, productId = product, confirmedAt = System.currentTimeMillis()))
	}

	/** Restauration demandee par l'utilisateur. Rend vrai si un abonnement actif a ete retrouve. */
	suspend fun restore(): Boolean = suspendCancellableCoroutine { cont ->
		client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
			val active = result.responseCode == BillingClient.BillingResponseCode.OK && purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.any { id -> id in Products.ALL } }
			purchases.forEach(::handle)
			if (cont.isActive) cont.resume(active)
		}
	}

	private fun checkActiveSubscriptions(revokeIfNone: Boolean) {
		client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
			if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
			val active = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.any { id -> id in Products.ALL } }
			if (active.isNotEmpty()) {
				active.forEach(::handle)
			} else if (revokeIfNone && state.entitlement.value.pro) {
				Log.i(TAG, "aucun abonnement actif selon la boutique : droit Pro retire")
				state.setEntitlement(state.entitlement.value.copy(pro = false, productId = null))
			}
		}
	}

	private companion object {
		const val TAG = "Purchases"
	}
}
