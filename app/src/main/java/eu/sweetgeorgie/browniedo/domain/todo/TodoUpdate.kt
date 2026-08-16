package eu.sweetgeorgie.browniedo.domain.todo

/**
 * Was ein Speichern im Bearbeiten-Dialog an einer Aufgabe ändert — alle Felder zusammen, weil sie in
 * einem Schreibvorgang rausgehen, siehe
 * docs/decisions/0025-titel-und-prioritaet-in-einem-schreibvorgang.md.
 *
 * Entstanden, als die Menge das sechste Argument von `TodoRepository.updateTodo` geworden wäre, zwei
 * davon `String` und zwei nullable. Benannte Argumente tragen das nicht mehr.
 *
 * **Nicht zu verwechseln mit `TodoEdit` in der UI-Schicht:** Das dort ist der Tippstand des Dialogs —
 * Textpuffer, auch für Zahlen. Hier steht das Ergebnis, in den Typen der Domäne.
 *
 * [notes] und [quantity] sind `null`, wenn es sie nicht gibt; das löscht ein vorhandenes Feld.
 */
data class TodoUpdate(
    val title: String,
    val priority: TodoPriority,
    val notes: String?,
    val quantity: Double?
)
