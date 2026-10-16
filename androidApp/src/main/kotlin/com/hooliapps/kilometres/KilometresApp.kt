package com.hooliapps.kilometres

import android.app.Application
import com.hooliapps.kilometres.ads.AdsManager
import com.hooliapps.kilometres.billing.Purchases
import com.hooliapps.kilometres.cloud.SystemBackup
import com.hooliapps.kilometres.core.PlatformFiles
import com.hooliapps.kilometres.core.store.AppState
import com.hooliapps.kilometres.core.store.FileDataStore
import com.hooliapps.kilometres.widget.TripsWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Point d'entree du processus : construit le graphe d'objets une fois, et
 * branche ce qui doit reagir a chaque modification des donnees (sauvegarde
 * systeme, widget, montre).
 */
class KilometresApp : Application() {

	override fun onCreate() {
		super.onCreate()
		Graph.init(this)
	}
}

/**
 * Les objets a vie de processus, accessibles de partout sans injection :
 * l'application est petite, et un singleton explicite se lit mieux qu'un
 * cadre d'injection pour quatre objets.
 */
object Graph {
	lateinit var app: Application
		private set
	lateinit var state: AppState
		private set
	lateinit var purchases: Purchases
		private set

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	fun init(application: Application) {
		app = application
		state = AppState(FileDataStore(PlatformFiles(application.filesDir.absolutePath))).also { it.load() }
		purchases = Purchases(application, state)

		// Apres chaque modification : le systeme planifie sa sauvegarde, le
		// widget se redessine, la montre recoit les favoris a jour.
		state.data.drop(1).onEach {
			SystemBackup.requestBackup(application)
			TripsWidget.refresh(application)
			com.hooliapps.kilometres.wear.WearSync.publish(application, state)
		}.launchIn(scope)
		state.entitlement.drop(1).onEach {
			SystemBackup.requestBackup(application)
			TripsWidget.refresh(application)
			com.hooliapps.kilometres.wear.WearSync.publish(application, state)
			// Un Pro n'a plus de publicite : on coupe sur-le-champ.
			if (it.isActive) AdsManager.shutdownForPro()
		}.launchIn(scope)
	}
}
