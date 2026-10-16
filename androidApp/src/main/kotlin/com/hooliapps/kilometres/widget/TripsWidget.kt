package com.hooliapps.kilometres.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.hooliapps.kilometres.Graph
import com.hooliapps.kilometres.MainActivity
import com.hooliapps.kilometres.R
import com.hooliapps.kilometres.core.Day
import com.hooliapps.kilometres.core.Feature
import com.hooliapps.kilometres.ui.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Widget d'ecran d'accueil (Pro) : les kilometres du jour et du mois, et les
 * deux premiers favoris, qui ouvrent la saisie prete a enregistrer. Sans Pro,
 * le widget le dit et ouvre l'application.
 */
class TripsWidget : GlanceAppWidget() {

	override suspend fun provideGlance(context: Context, id: GlanceId) {
		val state = Graph.state
		val data = state.current
		val pro = state.entitlement.value.allows(Feature.WIDGETS)
		val today = Day.today()
		val todayKm = data.trips.filter { it.date == today }.sumOf { it.totalKm }
		val monthKm = data.trips.filter { it.date.year == today.year && it.date.month == today.month }.sumOf { it.totalKm }
		val favorites = data.favorites.take(2)

		provideContent {
			GlanceTheme {
				Column(
					modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(16.dp).padding(12.dp)
						.clickable(actionStartActivity<MainActivity>()),
					verticalAlignment = Alignment.CenterVertically
				) {
					if (!pro) {
						Text(context.getString(R.string.widget_pro_needed), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
					} else {
						Row(GlanceModifier.fillMaxWidth()) {
							Stat(context.getString(R.string.widget_today), context.getString(R.string.km_value, Fmt.km(todayKm)))
							Spacer(GlanceModifier.width(12.dp))
							Stat(context.getString(R.string.widget_month), context.getString(R.string.km_value, Fmt.km(monthKm)))
						}
						if (favorites.isNotEmpty()) {
							Spacer(GlanceModifier.height(8.dp))
							Row(GlanceModifier.fillMaxWidth()) {
								for (f in favorites) {
									val intent = Intent(Intent.ACTION_VIEW, Uri.parse("kilometres://trip?favorite=${f.id}"), context, MainActivity::class.java)
										.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
									Text(
										"⚡ ${f.label}",
										style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Medium),
										modifier = GlanceModifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(12.dp).padding(horizontal = 10.dp, vertical = 6.dp)
											.clickable(androidx.glance.appwidget.action.actionStartActivity(intent))
									)
									Spacer(GlanceModifier.width(6.dp))
								}
							}
						}
					}
				}
			}
		}
	}

	@Composable
	private fun Stat(label: String, value: String) {
		Column {
			Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
			Text(value, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold))
		}
	}

	companion object {
		private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

		fun refresh(context: Context) {
			scope.launch { runCatching { TripsWidget().updateAll(context) } }
		}
	}
}

class TripsWidgetReceiver : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget = TripsWidget()
}
