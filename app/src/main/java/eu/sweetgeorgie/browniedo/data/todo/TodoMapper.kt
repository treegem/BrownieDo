package eu.sweetgeorgie.browniedo.data.todo

import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority

/**
 * Maps a Firestore document onto the domain model.
 *
 * Returns null when the document is unusable — an empty title or a missing timestamp means the
 * document was written by something other than this app. The repository decides how to report
 * that; silently mapping it to a placeholder would hide the problem.
 *
 * Ein fehlender Erledigungszeitpunkt gehört ausdrücklich **nicht** dazu: Aufgaben, die vor Phase 9
 * abgehakt wurden, haben ihn nicht und müssen trotzdem erhalten bleiben, siehe
 * docs/decisions/0023-prioritaet-migration-und-sortierung.md.
 */
fun TodoDocument.toTodo(id: String): Todo? {
    val createdAtInstant = createdAt?.toInstant() ?: return null
    val updatedAtInstant = updatedAt?.toInstant() ?: return null
    if (title.isBlank()) return null

    return Todo(
        id = id,
        title = title,
        isDone = done,
        priority = priority.toTodoPriority(),
        createdAt = createdAtInstant,
        updatedAt = updatedAtInstant,
        completedBy = completedBy.takeIf { done },
        completedAt = completedAt?.toInstant().takeIf { done }
    )
}

/**
 * Fehlt die Priorität oder trägt sie einen Wert, den diese App-Version nicht kennt, gilt „mittel".
 *
 * Der Rückfall deckt beide Richtungen ab: alte Dokumente ohne das Feld und Dokumente, die ein Gerät
 * mit *neuerer* App-Version geschrieben hat. Firestores eigener Enum-Umbau könnte nur die erste
 * Richtung — bei einem unbekannten Wert wirft er, statt das Dokument wie hier üblich zu verwerfen.
 */
private fun String?.toTodoPriority(): TodoPriority =
    TodoPriority.entries.firstOrNull { it.name == this } ?: TodoPriority.MEDIUM
