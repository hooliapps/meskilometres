package com.hooliapps.kilometres.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/*
 * Palette : vert profond de l'asphalte mouille, sable des bas-cotes, et un
 * ambre d'avertissement pour ce qui touche a l'estimation. Sur Android 12 et
 * plus, les couleurs dynamiques du systeme prennent le dessus : c'est le
 * style natif attendu, et l'utilisateur l'a choisi.
 */
private val Vert = Color(0xFF1F6F5F)
private val VertSombre = Color(0xFF0E3F36)
private val Sable = Color(0xFFF4EFE6)
private val SableSombre = Color(0xFFE6DCC9)
private val Encre = Color(0xFF1E2421)
private val Ambre = Color(0xFFB7791F)
private val AmbreClair = Color(0xFFFBE9C6)
private val Rouge = Color(0xFFB3261E)

private val LightScheme = lightColorScheme(
	primary = Vert, onPrimary = Color.White, primaryContainer = Color(0xFFCDEFE4), onPrimaryContainer = VertSombre,
	secondary = Color(0xFF4F6B63), onSecondary = Color.White, secondaryContainer = Color(0xFFD5E8E0), onSecondaryContainer = Encre,
	tertiary = Ambre, onTertiary = Color.White, tertiaryContainer = AmbreClair, onTertiaryContainer = Color(0xFF4A3000),
	error = Rouge, onError = Color.White, errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
	background = Sable, onBackground = Encre, surface = Sable, onSurface = Encre,
	surfaceVariant = SableSombre, onSurfaceVariant = Color(0xFF4A4A44), outline = Color(0xFF7A7A72),
	surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAF7F1), surfaceContainer = Color(0xFFF2EDE3),
	surfaceContainerHigh = Color(0xFFECE6DA), surfaceContainerHighest = SableSombre
)

private val DarkScheme = darkColorScheme(
	primary = Color(0xFF8FD3C0), onPrimary = VertSombre, primaryContainer = Color(0xFF1B5547), onPrimaryContainer = Color(0xFFCDEFE4),
	secondary = Color(0xFFB4CCC3), onSecondary = Color(0xFF203630), secondaryContainer = Color(0xFF364D45), onSecondaryContainer = Color(0xFFD5E8E0),
	tertiary = Color(0xFFF0C06A), onTertiary = Color(0xFF402D00), tertiaryContainer = Color(0xFF5C4300), onTertiaryContainer = AmbreClair,
	error = Color(0xFFF2B8B5), onError = Color(0xFF601410), errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
	background = Color(0xFF14110E), onBackground = Color(0xFFEDE7DE), surface = Color(0xFF14110E), onSurface = Color(0xFFEDE7DE),
	surfaceVariant = Color(0xFF3A3630), onSurfaceVariant = Color(0xFFCFC8BC), outline = Color(0xFF8F8A80),
	surfaceContainerLowest = Color(0xFF0E0C09), surfaceContainerLow = Color(0xFF1C1915), surfaceContainer = Color(0xFF211D18),
	surfaceContainerHigh = Color(0xFF2B2722), surfaceContainerHighest = Color(0xFF36312B)
)

@Composable
fun KilometresTheme(content: @Composable () -> Unit) {
	val dark = isSystemInDarkTheme()
	val context = LocalContext.current
	val scheme = when {
		Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		dark -> DarkScheme
		else -> LightScheme
	}
	MaterialTheme(colorScheme = scheme, content = content)
}
