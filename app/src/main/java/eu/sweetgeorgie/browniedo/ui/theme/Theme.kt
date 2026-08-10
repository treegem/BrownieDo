package eu.sweetgeorgie.browniedo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Beide Schemata setzen **alle** Rollen, nicht nur `primary`, `secondary` und `tertiary`. Was man
 * auslässt, füllt Material mit seinem Standard-Lila — auch `surfaceContainer`, `onSurfaceVariant`
 * und die Fehlerfarben, die diese App tatsächlich benutzt.
 *
 * Warum es kein Dynamic Color mehr gibt und wie die Töne gewählt sind, steht in
 * docs/decisions/0021-eigene-farbpalette-statt-dynamic-color.md.
 *
 * `internal` statt `private`, damit `ColorSchemeContrastTest` die Kontraste nachrechnen
 * kann, ohne die Oberfläche zu starten.
 */
internal val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Brown80,
    onSecondary = Brown20,
    secondaryContainer = Brown30,
    onSecondaryContainer = Brown90,
    tertiary = Rose80,
    onTertiary = Rose20,
    tertiaryContainer = Rose30,
    onTertiaryContainer = Rose90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Neutral6,
    onBackground = Neutral90,
    surface = Neutral6,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    surfaceContainerLowest = Neutral4,
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral12,
    surfaceContainerHigh = Neutral17,
    surfaceContainerHighest = Neutral22,
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    inversePrimary = Green40,
    scrim = Neutral0
)

internal val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Neutral100,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Brown40,
    onSecondary = Neutral100,
    secondaryContainer = Brown90,
    onSecondaryContainer = Brown10,
    tertiary = Rose40,
    onTertiary = Neutral100,
    tertiaryContainer = Rose90,
    onTertiaryContainer = Rose10,
    error = Red40,
    onError = Neutral100,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Neutral98,
    onBackground = Neutral10,
    surface = Neutral98,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    surfaceContainerLowest = Neutral100,
    surfaceContainerLow = Neutral96,
    surfaceContainer = Neutral94,
    surfaceContainerHigh = Neutral92,
    surfaceContainerHighest = Neutral90,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    inversePrimary = Green80,
    scrim = Neutral0
)

@Composable
fun BrownieDoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
