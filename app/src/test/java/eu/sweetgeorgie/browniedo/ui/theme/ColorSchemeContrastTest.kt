package eu.sweetgeorgie.browniedo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Hält fest, dass die Palette lesbar bleibt.
 *
 * Anlass war ein gemessener Kontrast von 2,12 : 1 für einen **aktiven** Dialog-Knopf — kaum mehr
 * als die 1,91 : 1 desselben Knopfes im deaktivierten Zustand. Genau deshalb wirkte er abgeschaltet.
 * Ohne diesen Test müsste man das nach jeder Farbänderung erneut auf einem Gerät nachmessen; siehe
 * docs/decisions/0021-eigene-farbpalette-statt-dynamic-color.md.
 *
 * Die Luminanz wird hier von Hand gerechnet. `Color.toArgb()` und `androidx.core`-Hilfsmittel
 * greifen in die Android-Plattform und stünden in einem reinen JVM-Test nicht zur Verfügung; die
 * `Color`-Wertklasse selbst und die Schema-Factories sind dagegen reines Kotlin.
 */
class ColorSchemeContrastTest {

    @Test
    fun `the dark scheme stays readable`() = assertReadable(DarkColors, "dunkel")

    @Test
    fun `the light scheme stays readable`() = assertReadable(LightColors, "hell")

    /**
     * Bestätigen und Löschen dürfen nie zu ähnlich werden. Bei Grün gegen Rot ist der Abstand
     * reichlich — der Test hält die Entscheidung fest, damit niemand die beiden später aneinander
     * annähert, etwa indem `primary` auf das Braun des Icons umgestellt wird.
     */
    @Test
    fun `the primary and the error colour stay clearly apart`() {
        listOf("dunkel" to DarkColors, "hell" to LightColors).forEach { (name, scheme) ->
            val distance = deltaE(scheme.primary, scheme.error)
            assertTrue(
                "Im $name Schema liegen primary und error nur ΔE $distance auseinander, " +
                    "mindestens $MIN_DELTA_E sind nötig.",
                distance >= MIN_DELTA_E
            )
        }
    }

    private fun assertReadable(scheme: ColorScheme, name: String) {
        // Jedes Paar ist eine Stelle, an der die App wirklich Farbe auf Farbe setzt — siehe die
        // Aufstellung im ADR. Reihenfolge: Vordergrund, Hintergrund.
        val pairs = listOf(
            // Der ursprüngliche Fehler: Dialog-Knöpfe.
            Triple("primary", scheme.primary, scheme.surfaceContainerHigh),
            // Die aktive Liste im Auswahlmenü.
            Triple("primary", scheme.primary, scheme.surfaceContainer),
            // Ladekreis und fokussiertes Eingabefeld.
            Triple("primary", scheme.primary, scheme.surface),
            // Anmelde-Knopf, der Hinzufügen-Knopf in der Eingabeleiste und seit ADR 0032 die
            // gefüllten Bestätigungsknöpfe aller Dialoge — die brauchen deshalb kein eigenes Paar.
            Triple("onPrimary", scheme.onPrimary, scheme.primary),
            Triple("onSurface", scheme.onSurface, scheme.surface),
            // Trägt bei erledigten Aufgaben Bedeutung, nicht nur Schmuck.
            Triple("onSurfaceVariant", scheme.onSurfaceVariant, scheme.surface),
            // Der Löschen-Knopf im Bearbeiten-Dialog: Text in Fehlerfarbe im Inhalt, nicht gefüllt.
            Triple("error", scheme.error, scheme.surfaceContainerHigh),
            // Der gefüllte Löschen-Knopf im Dialog „Liste löschen?" (ADR 0032).
            Triple("onError", scheme.onError, scheme.error),
            // Die Markierung „hoch" am Ende einer offenen Zeile.
            Triple("error", scheme.error, scheme.surface),
            // Die gewählte Stufe in der Segment-Auswahl des Bearbeiten-Dialogs.
            Triple(
                "onSecondaryContainer",
                scheme.onSecondaryContainer,
                scheme.secondaryContainer
            ),
            // Der Hintergrund der Wischgeste.
            Triple("onErrorContainer", scheme.onErrorContainer, scheme.errorContainer)
        )

        pairs.forEach { (role, foreground, background) ->
            val contrast = contrastRatio(foreground, background)
            assertTrue(
                "Im $name Schema erreicht $role nur $contrast : 1, mindestens $MIN_CONTRAST sind " +
                    "nötig. Die Farbwerte sind falsch, nicht dieser Test.",
                contrast >= MIN_CONTRAST
            )
        }
    }

    private companion object {
        /** WCAG AA für normalen Text. */
        const val MIN_CONTRAST = 4.5

        /**
         * ΔE 2,3 ist gerade noch unterscheidbar, 10 deutlich. 25 heißt: keine Verwechslung im
         * Vorbeischauen. Die Palette liegt mit 75 (dunkel) und 100 (hell) weit darüber.
         */
        const val MIN_DELTA_E = 25.0
    }
}

private fun contrastRatio(foreground: Color, background: Color): Double {
    val a = relativeLuminance(foreground)
    val b = relativeLuminance(background)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

/** WCAG 2.1, relative Luminanz aus den linearisierten Kanälen. */
private fun relativeLuminance(color: Color): Double =
    0.2126 * linearize(color.red) + 0.7152 * linearize(color.green) + 0.0722 * linearize(color.blue)

private fun linearize(channel: Float): Double {
    val value = channel.toDouble()
    return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}

/** Abstand im CIELAB-Raum (ΔE76) — grob, aber für „sind das zwei Farben?" völlig ausreichend. */
private fun deltaE(first: Color, second: Color): Double {
    val (l1, a1, b1) = toLab(first)
    val (l2, a2, b2) = toLab(second)
    return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
}

private fun toLab(color: Color): Triple<Double, Double, Double> {
    val r = linearize(color.red)
    val g = linearize(color.green)
    val b = linearize(color.blue)
    // sRGB nach XYZ (D65), danach XYZ nach L*a*b*.
    val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
    val fx = pivot(x)
    val fy = pivot(y)
    val fz = pivot(z)
    return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
}

private fun pivot(value: Double): Double =
    if (value > 216.0 / 24389.0) cbrt(value) else (841.0 / 108.0) * value + 4.0 / 29.0
