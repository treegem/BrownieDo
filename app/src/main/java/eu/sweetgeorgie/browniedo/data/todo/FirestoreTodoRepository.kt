package eu.sweetgeorgie.browniedo.data.todo

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import eu.sweetgeorgie.browniedo.data.LISTS_COLLECTION
import eu.sweetgeorgie.browniedo.data.TODOS_COLLECTION
import eu.sweetgeorgie.browniedo.data.todo.TodoField.COMPLETED_AT
import eu.sweetgeorgie.browniedo.data.todo.TodoField.COMPLETED_BY
import eu.sweetgeorgie.browniedo.data.todo.TodoField.DONE
import eu.sweetgeorgie.browniedo.data.todo.TodoField.NOTES
import eu.sweetgeorgie.browniedo.data.todo.TodoField.PRIORITY
import eu.sweetgeorgie.browniedo.data.todo.TodoField.TITLE
import eu.sweetgeorgie.browniedo.data.todo.TodoField.UPDATED_AT
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import eu.sweetgeorgie.browniedo.domain.todo.TodoRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant

/**
 * Reads and writes the entries at `lists/{listId}/todos`, see
 * docs/decisions/0009-listen-dokument-mit-todo-subcollection.md.
 *
 * The list is resolved per call rather than held as state — the reasoning is in [TodoRepository].
 */
class FirestoreTodoRepository(private val firestore: FirebaseFirestore) : TodoRepository {

    private fun todoCollection(listId: String): CollectionReference = firestore
        .collection(LISTS_COLLECTION)
        .document(listId)
        .collection(TODOS_COLLECTION)

    override fun todos(listId: String): Flow<Result<List<Todo>>> = callbackFlow {
        val registration = todoCollection(listId).addSnapshotListener { snapshot, error ->
            when {
                error != null -> trySend(Result.failure(error))
                snapshot != null -> trySend(Result.success(snapshot.toTodos()))
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * The server fills in the timestamps, so only the title and the starting priority are written
     * here — a control for the priority sits in the edit dialog, not in the input bar.
     *
     * The write is not awaited, see docs/decisions/0011-schreibvorgaenge-nicht-abwarten.md.
     */
    override fun addTodo(listId: String, title: String): Result<Unit> =
        runCatching {
            todoCollection(listId).add(
                TodoDocument(title = title, priority = TodoPriority.MEDIUM.name)
            )
        }.map { }

    /**
     * Writes single fields instead of the whole document, which is the field-level
     * last-write-wins the project settled on: whatever the partner changes at the same time in a
     * *different* field survives. Title and priority are the exception — they travel together, see
     * docs/decisions/0025-titel-und-prioritaet-in-einem-schreibvorgang.md.
     *
     * [UPDATED_AT] has to be set by hand here and in [updateTodo] — the `@ServerTimestamp`
     * annotation on [TodoDocument] only applies when the whole object is written, so a field
     * update would leave the old timestamp in place and break conflict resolution, see
     * docs/decisions/0006-server-zeitstempel-fuer-last-write-wins.md.
     */
    override fun setDone(
        listId: String,
        todoId: String,
        isDone: Boolean,
        completedBy: String?
    ): Result<Unit> =
        runCatching {
            todoCollection(listId).document(todoId).update(
                mapOf(
                    DONE to isDone,
                    COMPLETED_BY to completedBy,
                    // Zusammen mit completedBy gesetzt und zusammen mit ihm wieder geleert. Der
                    // Zeitpunkt kommt vom Server, aus demselben Grund wie updatedAt (ADR 0006).
                    COMPLETED_AT to if (isDone) FieldValue.serverTimestamp() else null,
                    UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
        }.map { }

    override fun updateTodo(
        listId: String,
        todoId: String,
        title: String,
        priority: TodoPriority,
        notes: String?
    ): Result<Unit> =
        runCatching {
            todoCollection(listId).document(todoId).update(
                mapOf(
                    TITLE to title,
                    // Der Name, nicht die Position im Enum: Eine spätere Umsortierung der Stufen
                    // darf gespeicherte Aufgaben nicht umdeuten.
                    PRIORITY to priority.name,
                    // Eine gelöschte Notiz wird zu null geschrieben und **nicht** mit
                    // FieldValue.delete() entfernt: Beides liest sich später gleich, aber so
                    // zerfällt der Bestand nicht in „Feld fehlt" und „Feld ist null". Dass eine
                    // alte Aufgabe das Feld noch gar nicht hat, stört update() nicht — es legt
                    // fehlende Felder an und scheitert nur an einem fehlenden Dokument.
                    NOTES to notes,
                    UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
        }.map { }

    /**
     * The only write here that creates a whole document instead of changing single fields
     * (ADR 0024), and the only one touching two lists.
     *
     * Die Ziel-id vergibt `document()` ohne Argument lokal, genau wie `add()` es intern tut — auch
     * das Verschieben braucht damit keinen Server und funktioniert offline.
     *
     * Ein Batch statt zweier Schreibvorgänge, sonst gäbe es einen Zwischenzustand, in dem die
     * Aufgabe in beiden oder in keiner Liste steht. Die Security Rules werten jede Schreibung eines
     * Batches einzeln aus — die eine gegen `lists/{toListId}`, die andere gegen
     * `lists/{fromListId}`; in beiden steht die eigene uid, sonst wäre die Liste gar nicht erst
     * geladen worden. Anders als beim Löschen einer Liste (ADR 0019) hängt hier nichts an der
     * Reihenfolge: Beide Listen-Dokumente überstehen den Batch unberührt.
     *
     * `commit()` wird nicht abgewartet, siehe
     * docs/decisions/0011-schreibvorgaenge-nicht-abwarten.md.
     */
    override fun moveTodo(fromListId: String, toListId: String, todo: Todo): Result<Unit> =
        runCatching {
            val batch = firestore.batch()
            batch.set(todoCollection(toListId).document(), todo.toDocument())
            batch.delete(todoCollection(fromListId).document(todo.id))
            batch.commit()
        }.map { }

    override fun deleteTodo(listId: String, todoId: String): Result<Unit> =
        runCatching { todoCollection(listId).document(todoId).delete() }.map { }
}

/**
 * Open entries first, finished ones below. Ticking an entry off therefore moves it out of the way
 * immediately, which is what the list is walked down for while shopping.
 *
 * Innerhalb der offenen Aufgaben entscheidet die Priorität und erst danach das Anlagedatum,
 * innerhalb der erledigten der Zeitpunkt des Abhakens — siehe
 * docs/decisions/0023-prioritaet-migration-und-sortierung.md, das ADR 0010 dafür erweitert.
 *
 * Die beiden Gruppen brauchen getrennte Vergleiche und lassen sich nicht zu einer Kette
 * zusammenziehen: Die Priorität soll erledigte Aufgaben ausdrücklich *nicht* umsortieren, eine
 * durchgehende Kette setzte sie dort aber als Gleichstand-Entscheider ein — genau zwischen den
 * alten Einträgen ohne `completedAt`, die alle gleich vergleichen.
 *
 * Kept apart from [toTodos] so it can be tested without a [QuerySnapshot].
 */
internal val TODO_ORDER: Comparator<Todo> = Comparator { first, second ->
    when {
        first.isDone != second.isDone -> if (first.isDone) 1 else -1
        first.isDone -> FINISHED_ORDER.compare(first, second)
        else -> OPEN_ORDER.compare(first, second)
    }
}

/** Das Dringendste oben, bei gleicher Stufe die neueste Aufgabe. */
private val OPEN_ORDER: Comparator<Todo> =
    compareByDescending(Todo::priority).thenByDescending(Todo::createdAt)

/**
 * Zuletzt abgehakt oben, Einträge ohne Erledigungszeitpunkt ans Ende.
 *
 * `nullsLast` sitzt bewusst **um** die umgekehrte Ordnung herum und nicht umgekehrt: Bei
 * `compareByDescending(nullsLast(...))` würde die Umkehrung auch die Null-Behandlung mitdrehen —
 * `nullsLast` stellt null als „größer als alles" ein, absteigend sortiert stünde es damit ganz
 * oben. Die alten Einträge ohne `completedAt` sähen dann aus, als wären sie gerade eben abgehakt
 * worden. So herum kehrt `reverseOrder()` nur die echten Zeitpunkte um, und `nullsLast` hängt die
 * fehlenden hinten an. Der letzte Schritt entscheidet nur noch zwischen zwei alten Einträgen und
 * lässt ihnen die Reihenfolge, die sie vor Phase 9 hatten.
 */
private val FINISHED_ORDER: Comparator<Todo> =
    compareBy(nullsLast(reverseOrder<Instant>()), Todo::completedAt)
        .thenByDescending(Todo::createdAt)

/**
 * Documents that cannot be mapped are dropped instead of reported: they were not written by this
 * app, so neither of the two users can cause or fix that.
 *
 * Sorting happens here rather than through `orderBy`, see
 * docs/decisions/0010-sortierung-im-client-statt-orderby.md.
 */
private fun QuerySnapshot.toTodos(): List<Todo> =
    documents.mapNotNull { document ->
        document.toObject(TodoDocument::class.java, ESTIMATE)?.toTodo(document.id)
    }.sortedWith(TODO_ORDER)
