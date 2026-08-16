package eu.sweetgeorgie.browniedo.domain.todo

/*
 * Manuelles Sortieren innerhalb einer Prioritätsstufe — die Regeln aus
 * docs/decisions/0039-manuelle-sortierung-ueber-createdat-als-anker.md, jede genau einmal.
 *
 * Reine Funktionen ohne Android-Typ und ohne Firestore, wie in TodoQuantity.kt: Das ViewModel rechnet
 * den Wert aus, bevor es das Repository ruft, und das Repository schreibt nur, was es bekommt.
 */

/**
 * Der Platz einer Aufgabe in ihrer Prioritätsgruppe, als Zahl. **Größer heißt weiter oben** — dieselbe
 * Richtung wie der Anlagezeitpunkt, den der Wert verallgemeinert.
 *
 * Der Rückfall auf `createdAt` ist der Kern von ADR 0039 und leistet drei Dinge auf einmal:
 *
 * - Solange niemand von Hand sortiert hat, ist die Reihenfolge **paarweise dieselbe wie vor Phase 15**.
 *   `toEpochMilli()` ist monoton im Zeitpunkt, der Vergleich fällt also genauso aus wie der bisherige
 *   nach `createdAt`; bei Gleichstand entscheidet er weiterhin selbst.
 * - Eine frisch angelegte Aufgabe steht **ohne jeden Schreibvorgang** oben, weil ihre Millisekunden
 *   die größten sind. Deshalb muss `addTodo` keinen Wert vergeben.
 * - **Jede** Aufgabe hat immer einen Anker. Ohne ihn ließe sich in einer Gruppe, in der noch niemand
 *   gezogen hat, gar nichts einsortieren: Es gäbe keine zwei Zahlen, zwischen die etwas passt.
 */
val Todo.effectiveOrder: Double get() = sortOrder ?: createdAt.toEpochMilli().toDouble()

/**
 * Der Wert für eine Aufgabe, die zwischen [above] und [below] abgelegt wird.
 *
 * Beide sind die Nachbarn **nach** dem Ablegen, also aus der Liste **ohne** die gezogene Aufgabe. Wer
 * sie aus der Liste von vorher nimmt, verschiebt jeden Zug nach unten um genau eine Stelle — der
 * Abseits-um-eins, den jede Ziehimplementierung einmal einbaut.
 *
 * Null heißt: **Der Platz lässt sich nicht als Zahl ausdrücken.** Ein Prädikat trifft dabei zwei
 * Fälle — zwei Nachbarn mit demselben Anker (zwei nie sortierte Aufgaben aus derselben Millisekunde,
 * möglich, wenn eine Offline-Warteschlange durchläuft) und eine Lücke, die durch wiederholtes
 * Hineinziehen unter die Auflösung von `Double` geschrumpft ist. Der Aufrufer muss dann die Gruppe
 * neu nummerieren. Ohne beide Nachbarn gibt es schlicht nichts zu schreiben; auch das ist null und
 * kein Fehler.
 */
fun sortOrderBetween(above: Todo?, below: Todo?): Double? = when {
    above != null && below != null -> {
        val middle = (above.effectiveOrder + below.effectiveOrder) / 2
        // Die strenge Ungleichung ist die ganze Absicherung: Sie hält genau dann, wenn zwischen den
        // beiden Ankern überhaupt noch eine darstellbare Zahl liegt.
        middle.takeIf { it > below.effectiveOrder && it < above.effectiveOrder }
    }
    // Kein Nachbar darüber: Die Aufgabe kommt an den Anfang ihrer Gruppe, also über den einzigen.
    below != null -> below.effectiveOrder + SORT_ORDER_GAP
    // Und umgekehrt ans Ende.
    above != null -> above.effectiveOrder - SORT_ORDER_GAP
    else -> null
}

/**
 * Frische Werte für eine ganze Prioritätsgruppe, in der übergebenen Reihenfolge — die Reparatur für
 * den Fall, dass [sortOrderBetween] null liefert.
 *
 * [ordered] steht bereits so, wie es stehen soll (die gezogene Aufgabe an ihrem neuen Platz). Der
 * größte Wert kommt an den Anfang, weil größer weiter oben heißt. Gerechnet wird vom Anker der
 * **ersten** Aufgabe abwärts, damit die Gruppe dort bleibt, wo sie im Zahlenraum ohnehin schon lag,
 * statt gegenüber den anderen Stufen zu springen.
 */
fun renumberedSortOrders(ordered: List<Todo>): Map<String, Double> {
    val top = ordered.firstOrNull()?.effectiveOrder ?: return emptyMap()
    return ordered.withIndex().associate { (index, todo) -> todo.id to top - index * SORT_ORDER_GAP }
}

/**
 * Der Abstand zum einzigen Nachbarn am Anfang und am Ende einer Gruppe, in Millisekunden — derselben
 * Einheit wie die Rückfallwerte.
 *
 * Eine Sekunde, und die Zahl entscheidet mehr, als sie aussieht: Sie legt fest, **wie lange eine nach
 * oben gezogene Aufgabe eine neu angelegte überholt.** Eine Sekunde heißt „die nächste getippte
 * Aufgabe steht wieder oben" — genau die Regel, die die Eingabeleiste ohnehin hat. Ein Abstand von
 * einer Stunde kehrte das für eine Stunde still um.
 */
private const val SORT_ORDER_GAP = 1_000.0
