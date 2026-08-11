package eu.sweetgeorgie.browniedo.ui.todo

import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority

/*
 * Die Abbildung einer Prioritätsstufe auf ihre Ressourcen. Sie steht hier und nicht am Enum, weil
 * `R` die UI-Schicht nicht verlassen darf — die Logik-Schicht soll ohne Android-Framework
 * wiederverwendbar bleiben, siehe „Projektspezifische Vorgaben" in ROADMAP.md.
 *
 * Eine eigene Datei, weil beide Nutzer sie brauchen: [labelResId] die Zeile (als
 * `contentDescription`) *und* der Segment-Wähler im Bearbeiten-Dialog. Kein `*Mapper`-Suffix — die
 * Abbildung ist ein `when` und nichts weiter.
 */

internal fun TodoPriority.labelResId(): Int = when (this) {
    TodoPriority.LOW -> R.string.todo_list_priority_low
    TodoPriority.MEDIUM -> R.string.todo_list_priority_medium
    TodoPriority.HIGH -> R.string.todo_list_priority_high
}

/**
 * Nur Abweichungen bekommen ein Symbol: „mittel" ist der Normalfall und stünde sonst in jeder
 * Zeile, ohne etwas auszusagen. Die beiden Pfeile unterscheiden sich in der Form, nicht nur in der
 * Farbe — das verlangt docs/decisions/0021-eigene-farbpalette-statt-dynamic-color.md.
 */
internal fun TodoPriority.markerIconResId(): Int? = when (this) {
    TodoPriority.HIGH -> R.drawable.ic_arrow_upward
    TodoPriority.MEDIUM -> null
    TodoPriority.LOW -> R.drawable.ic_arrow_downward
}
