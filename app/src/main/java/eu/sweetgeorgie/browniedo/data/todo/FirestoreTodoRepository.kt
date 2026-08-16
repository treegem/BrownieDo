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
import eu.sweetgeorgie.browniedo.data.todo.TodoField.QUANTITY
import eu.sweetgeorgie.browniedo.data.todo.TodoField.SORT_ORDER
import eu.sweetgeorgie.browniedo.data.todo.TodoField.TITLE
import eu.sweetgeorgie.browniedo.data.todo.TodoField.UPDATED_AT
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import eu.sweetgeorgie.browniedo.domain.todo.TodoRepository
import eu.sweetgeorgie.browniedo.domain.todo.TodoUpdate
import eu.sweetgeorgie.browniedo.domain.todo.effectiveOrder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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

    /**
     * **Ein abgewiesener Listener wird ein paar Mal neu aufgebaut, bevor der Fehler herauskommt**,
     * siehe docs/decisions/0036-neue-liste-wird-geoeffnet-und-listener-wiederholt.md.
     *
     * Der Grund ist ein Wettlauf beim Anlegen: Die Leseregel auf `todos` schlägt die Mitglieder des
     * Listen-Dokuments nach, und direkt nach dem Anlegen kennt der Server dieses Dokument
     * womöglich noch nicht — der Listener wird dann mit `PERMISSION_DENIED` abgewiesen, obwohl
     * gleich alles in Ordnung ist. Ein Versuch später ist es das auch.
     *
     * Betrifft nur den Online-Fall: Ohne Verbindung beantwortet Firestore den Listener aus dem
     * lokalen Cache und fragt gar nicht erst.
     */
    override fun todos(listId: String): Flow<Result<List<Todo>>> = todoSnapshots(listId)
        .map { Result.success(it) }
        .retryWhen { _, attempt ->
            if (attempt >= LISTEN_RETRIES) return@retryWhen false
            // Steigend, damit der zweite Versuch nicht in dieselbe Lücke fällt wie der erste.
            // `attempt` ist durch die Zeile darüber auf [LISTEN_RETRIES] begrenzt, `toInt` also
            // gefahrlos — `Duration` multipliziert nur mit Int oder Double.
            delay(LISTEN_RETRY_DELAY * (attempt + 1).toInt())
            true
        }
        // Erst wenn die Versuche aufgebraucht sind, ist es wirklich ein Fehler. Ab hier gilt
        // unverändert, was `TodoListViewModel.onErrorShown` festhält: Der Listener ist endgültig ab,
        // die Liste aktualisiert sich nicht mehr, und die Meldung bleibt deshalb stehen.
        .catch { emit(Result.failure(it)) }

    /**
     * Der nackte Listener. Meldet einen Fehler als Abbruch des Flows statt als Wert, damit [todos]
     * ihn mit `retryWhen` wiederholen kann — ein `Result.failure` als Emission wäre für den Operator
     * ein ganz normaler Wert.
     */
    private fun todoSnapshots(listId: String): Flow<List<Todo>> = callbackFlow {
        val registration = todoCollection(listId).addSnapshotListener { snapshot, error ->
            when {
                error != null -> close(error)
                snapshot != null -> trySend(snapshot.toTodos())
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

    override fun updateTodo(listId: String, todoId: String, update: TodoUpdate): Result<Unit> =
        runCatching {
            todoCollection(listId).document(todoId).update(
                mapOf(
                    TITLE to update.title,
                    // Der Name, nicht die Position im Enum: Eine spätere Umsortierung der Stufen
                    // darf gespeicherte Aufgaben nicht umdeuten.
                    PRIORITY to update.priority.name,
                    // Eine gelöschte Notiz wird zu null geschrieben und **nicht** mit
                    // FieldValue.delete() entfernt: Beides liest sich später gleich, aber so
                    // zerfällt der Bestand nicht in „Feld fehlt" und „Feld ist null". Dass eine
                    // alte Aufgabe das Feld noch gar nicht hat, stört update() nicht — es legt
                    // fehlende Felder an und scheitert nur an einem fehlenden Dokument.
                    NOTES to update.notes,
                    // Dieselbe Regel für die Menge: geleert heißt null, nicht „Feld weg".
                    QUANTITY to update.quantity,
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

    /**
     * `set()` auf denselben Pfad, den [deleteTodo] gerade geleert hat — die alte id kommt aus
     * [Todo.id]. Firestore stört es nicht, dass das Dokument dort eben noch stand: Offline reihen
     * sich Löschen und Anlegen in derselben lokalen Warteschlange, und der Endzustand ist ein
     * vorhandenes Dokument.
     *
     * Das ganze Dokument statt einzelner Felder — es gibt keine, die man ändern könnte. Damit greift
     * `@ServerTimestamp` wie beim Verschieben: `updatedAt` ist in [toDocument] null und wird vom
     * Server gesetzt, `createdAt` reist mit (ADR 0026).
     */
    override fun restoreTodo(listId: String, todo: Todo): Result<Unit> =
        runCatching { todoCollection(listId).document(todo.id).set(todo.toDocument()) }.map { }

    override fun setSortOrder(listId: String, todoId: String, sortOrder: Double): Result<Unit> =
        runCatching {
            todoCollection(listId).document(todoId).update(
                // Feldweise wie in setDone, und aus demselben Grund muss UPDATED_AT von Hand
                // mitgesetzt werden: @ServerTimestamp greift nur beim Schreiben ganzer Objekte
                // (ADR 0006).
                mapOf(SORT_ORDER to sortOrder, UPDATED_AT to FieldValue.serverTimestamp())
            )
        }.map { }

    override fun renumberTodos(listId: String, sortOrders: Map<String, Double>): Result<Unit> =
        runCatching {
            val batch = firestore.batch()
            sortOrders.forEach { (todoId, sortOrder) ->
                batch.update(
                    todoCollection(listId).document(todoId),
                    mapOf(SORT_ORDER to sortOrder, UPDATED_AT to FieldValue.serverTimestamp())
                )
            }
            batch.commit()
        }.map { }

    private companion object {
        /**
         * Wie oft ein abgewiesener Listener neu aufgebaut wird. Zwei reichen: Es geht um das Fenster
         * zwischen dem lokalen Anlegen einer Liste und ihrer Bestätigung durch den Server, nicht um
         * eine kaputte Verbindung — die bedient Firestore selbst aus dem Cache.
         */
        const val LISTEN_RETRIES = 2

        /**
         * Grundabstand zwischen zwei Versuchen; der zweite wartet doppelt so lang.
         *
         * Als [Duration] und nicht als `Long`: So steht die Einheit im Typ statt im Namen, und
         * `delay` braucht seine veraltete Millisekunden-Überladung nicht.
         */
        val LISTEN_RETRY_DELAY: Duration = 700.milliseconds
    }
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

/**
 * Das Dringendste oben, bei gleicher Stufe die von Hand gewählte Reihenfolge.
 *
 * `effectiveOrder` fällt auf den Anlagezeitpunkt zurück, solange niemand von Hand sortiert hat — der
 * Vergleich ist dann **paarweise derselbe** wie der frühere `thenByDescending(Todo::createdAt)`, weil
 * die Millisekunden monoton im Zeitpunkt sind. Das `thenByDescending(Todo::createdAt)` dahinter
 * entscheidet weiterhin bei Gleichstand und ist damit kein Rest, sondern die alte letzte Instanz.
 *
 * Die Priorität steht davor und bleibt unangetastet: Von Hand sortiert wird **innerhalb** einer
 * Stufe, nie über sie hinweg, siehe
 * docs/decisions/0039-manuelle-sortierung-ueber-createdat-als-anker.md.
 */
private val OPEN_ORDER: Comparator<Todo> = compareByDescending(Todo::priority)
    .thenByDescending(Todo::effectiveOrder)
    .thenByDescending(Todo::createdAt)

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
 *
 * **Von Hand sortiert wird hier ausdrücklich nicht** (ADR 0039): Der erledigte Block ist ein
 * Protokoll nach Erledigungszeitpunkt, keine Werkliste, die man umordnen wollte. Ein `sortOrder`
 * bleibt an einer abgehakten Aufgabe zwar stehen, wirkt hier aber nicht — und gilt wieder, sobald
 * sie erneut geöffnet wird.
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
