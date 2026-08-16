package eu.sweetgeorgie.browniedo.domain.todo

import java.math.BigDecimal
import java.math.RoundingMode

/*
 * Lesen, Rechnen und Schreiben von Mengen — die drei Regeln aus
 * docs/decisions/0037-menge-am-eintrag-statt-zahl-im-titel.md, jede genau einmal.
 *
 * Reine Funktionen ohne Android-Typ und ohne Firestore: Das ViewModel skaliert, bevor es das
 * Repository ruft, und das Repository schreibt nur, was es bekommt.
 */

/**
 * Liest eine getippte Menge oder einen getippten Faktor.
 *
 * **Komma wie Punkt**, denn die deutsche Tastatur liefert das Komma und `toDouble()` versteht nur den
 * Punkt. Null bei allem, was keine brauchbare positive Zahl ist — leer, Buchstaben, Null, negativ,
 * unendlich. Menge und Faktor benutzen dieselbe Funktion, weil es dieselbe Regel ist.
 *
 * Für die Menge ist null zugleich eine gültige Antwort: „keine Menge" heißt „skaliert nicht". Ob ein
 * null aus einem leeren oder aus einem unbrauchbaren Feld kommt, unterscheidet der Aufrufer am Text.
 */
fun String.toPositiveDecimalOrNull(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

/**
 * Schreibt eine Menge so, wie sie in einem Titel stehen soll: **exakt, Nachkommastellen nur wenn
 * nötig** — „3" statt „3,0", aber „1,5" bleibt 1,5. Mit deutschem Komma.
 *
 * Gerechnet wird über [BigDecimal], und das ist kein Zierat: `0.1 * 3` ist in `Double`
 * 0,30000000000000004, und das darf nicht in einem Aufgabentitel landen. Das Runden auf
 * [QUANTITY_SCALE] Stellen fängt genau diese Artefakte ab.
 *
 * **Von Hand mit Komma statt über `NumberFormat`:** Sonst hinge die Ausgabe an der Locale des
 * Geräts, und ein auf Englisch gestelltes Handy schriebe „1.5" in eine deutschsprachige App.
 */
fun formatQuantity(value: Double): String = BigDecimal.valueOf(value)
    .setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)
    // Macht aus 3,00 wieder 3 — `toPlainString` verhindert dabei die wissenschaftliche Schreibweise,
    // die `stripTrailingZeros` sonst hinterlässt.
    .stripTrailingZeros()
    .toPlainString()
    .replace('.', ',')

/**
 * Was beim Instanziieren aus einem Vorlagen-Eintrag wird: Menge mal Faktor, das Ergebnis als Präfix
 * vor den Titel („T-Shirt" mit Menge 1 und Faktor 3 wird zu „3 T-Shirt").
 *
 * **Ein Eintrag ohne Menge kommt unverändert zurück** — das ist der ganze Schalter, und der Grund,
 * warum drei Tage nicht drei Shampoo bedeuten.
 *
 * Die Menge selbst reist **nicht** mit: Was entsteht, ist eine gewöhnliche Liste ohne Sonderregeln,
 * und „aus 3 mach 2" ist dort eine Textänderung. Deshalb trägt nur die Vorlage das Feld.
 */
fun Todo.scaledBy(factor: Double): Todo {
    val quantity = quantity ?: return this
    return copy(title = "${formatQuantity(quantity * factor)} $title", quantity = null)
}

/**
 * Auf so viele Nachkommastellen wird gerundet. Zwei reichen für alles, was man in eine Packliste
 * schreibt, und schneiden die Fließkomma-Artefakte sicher ab.
 */
private const val QUANTITY_SCALE = 2
