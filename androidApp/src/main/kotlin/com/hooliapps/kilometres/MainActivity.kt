package com.hooliapps.kilometres

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.hooliapps.kilometres.ads.AdsManager
import com.hooliapps.kilometres.ui.AppViewModel
import com.hooliapps.kilometres.ui.Root
import com.hooliapps.kilometres.ui.Screen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

	private val vm: AppViewModel by lazy { AppViewModel(Graph.state) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		Graph.purchases.connect()
		handleIntent(intent)
		setContent { Root(vm) }
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		handleIntent(intent)
	}

	/** kilometres://trip ouvre la saisie : c'est ce que le widget et la montre envoient. */
	private fun handleIntent(intent: Intent?) {
		val data = intent?.data ?: return
		if (data.scheme == "kilometres" && data.host == "trip") {
			val favoriteId = data.getQueryParameter("favorite")
			vm.open(Screen.TripForm(favoriteId = favoriteId))
		}
	}

	override fun onResume() {
		super.onResume()
		// La chaine publicitaire repart a chaque retour au premier plan et ne
		// refait rien si tout est deja regle. Un Pro n'en a jamais.
		lifecycleScope.launch { AdsManager.start(this@MainActivity, isPro = Graph.state.isPro) }
	}
}
