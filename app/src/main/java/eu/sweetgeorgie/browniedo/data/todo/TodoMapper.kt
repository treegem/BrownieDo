package eu.sweetgeorgie.browniedo.data.todo

import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import java.util.Date

/**
 * Maps a Firestore document onto the domain model. The other direction exists too, further down —
 * it is needed by exactly one operation, see [toDocument].
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

/**
 * Maps the domain model back onto a document. Gebraucht wird diese Richtung nur beim Verschieben —
 * die einzige Operation, die ein vollständiges Dokument an einem neuen Ort anlegt, statt Felder
 * eines bestehenden zu ändern, siehe docs/decisions/0024-verschieben-behaelt-zustand.md.
 *
 * `updatedAt` bleibt leer, `createdAt` nicht: `@ServerTimestamp` ersetzt beim Schreiben genau die
 * Felder, die `null` sind, und nur die. Der Server setzt damit einen neuen Änderungszeitpunkt,
 * während der Anlagezeitpunkt unverändert mitreist — genau die Aufteilung, die ADR 0024 verlangt.
 * Dass die App `createdAt` dabei selbst schreibt, schränkt ADR 0006 ein, siehe
 * docs/decisions/0026-verschieben-schreibt-createdat-selbst.md.
 *
 * Die Dokument-id fehlt bewusst: Sie steht nicht im Dokument, sondern im Pfad
 * (docs/decisions/0009-listen-dokument-mit-todo-subcollection.md). Das neue Dokument bekommt eine
 * eigene.
 *
 * **Wer [Todo] ein Feld hinzufügt, muss es hier mitnehmen** — ein vergessenes Feld fiele beim
 * Verschieben still auf seinen Standardwert zurück. `TodoMapperTest` hält das über einen
 * Hin-und-zurück-Vergleich fest, damit es nicht bei der Ermahnung bleibt.
 */
fun Todo.toDocument(): TodoDocument = TodoDocument(
    title = title,
    done = isDone,
    createdAt = Date.from(createdAt),
    // Ausdrücklich null und nicht ausgelassen: Das ist der Punkt, an dem der Server übernimmt.
    updatedAt = null,
    completedBy = completedBy,
    completedAt = completedAt?.let(Date::from),
    // Der Name, nicht die Position im Enum — dieselbe Regel wie in `updateTodo`.
    priority = priority.name
)
