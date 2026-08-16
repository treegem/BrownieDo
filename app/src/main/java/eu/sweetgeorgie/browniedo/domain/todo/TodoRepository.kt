package eu.sweetgeorgie.browniedo.domain.todo

import kotlinx.coroutines.flow.Flow

/**
 * Every method takes the list it works on instead of the repository remembering a current one.
 *
 * That is a deliberate choice: BrownieDo is built around two people working at the same time, and a
 * repository holding the selected list would make "which list am I writing to?" implicit. A write
 * still in flight while the user switches lists would land in the wrong one. The explicit parameter
 * makes that impossible.
 */
interface TodoRepository {
    /**
     * Entries of [listId]: open ones first with the most urgent on top, finished ones below with
     * the most recently ticked off first, see
     * docs/decisions/0023-prioritaet-migration-und-sortierung.md. Emits again on every remote or
     * local change.
     *
     * A failure means the list could not be observed — the previously emitted entries stay valid
     * until the next successful emission.
     */
    fun todos(listId: String): Flow<Result<List<Todo>>>

    /**
     * Adds an entry to [listId]. The result only reports whether the write was accepted locally —
     * Firestore delivers it to the server on its own, see
     * docs/decisions/0011-schreibvorgaenge-nicht-abwarten.md.
     */
    fun addTodo(listId: String, title: String): Result<Unit>

    /**
     * Marks an entry as done or open again. [completedBy] is the uid of the partner who ticked it
     * off and is `null` whenever [isDone] is `false`. The time it was ticked off is recorded
     * alongside it and comes from the server, not from the device.
     */
    fun setDone(listId: String, todoId: String, isDone: Boolean, completedBy: String?): Result<Unit>

    /**
     * Writes what the edit dialog owns in a single update, so that one save is one write. What that
     * means for concurrent edits is in
     * docs/decisions/0025-titel-und-prioritaet-in-einem-schreibvorgang.md.
     *
     * Die Felder kommen als [TodoUpdate] statt einzeln — mit der Menge wären es sechs Argumente
     * geworden, und benannte Argumente tragen das nicht mehr.
     */
    fun updateTodo(listId: String, todoId: String, update: TodoUpdate): Result<Unit>

    /**
     * Moves an entry from [fromListId] to [toListId]. Firestore has no move: the document is
     * created anew in the target list and removed from the source, both in one batch so the entry
     * never exists twice and never not at all.
     *
     * [todo] travels whole — every field describing the entry survives, only the document id and
     * `updatedAt` are new, see docs/decisions/0024-verschieben-behaelt-zustand.md. The caller
     * passes title and priority in the state the edit dialog holds: moving and editing are one
     * write, see docs/decisions/0025-titel-und-prioritaet-in-einem-schreibvorgang.md.
     *
     * Taking a whole [Todo] rather than the fields is the exception on this interface, and it
     * follows from ADR 0024: what gets written is the entry itself, not a change to one of its
     * fields.
     *
     * Unlike `ListRepository.deleteList` this does not wait for the server — there is nothing to
     * look up first, so docs/decisions/0011-schreibvorgaenge-nicht-abwarten.md applies unchanged
     * and moving works offline.
     */
    fun moveTodo(fromListId: String, toListId: String, todo: Todo): Result<Unit>

    fun deleteTodo(listId: String, todoId: String): Result<Unit>

    /**
     * Löscht mehrere Aufgaben einer Liste in einem Zug — das „Erledigte löschen" des Überlauf-Menüs,
     * siehe docs/decisions/0040-erledigte-loeschen-mit-rueckfrage.md.
     *
     * **Nicht suspend, anders als `ListRepository.deleteList`:** Dort muss erst nachgeschlagen
     * werden, welche Aufgaben es überhaupt gibt. Hier kommen die ids aus dem Snapshot, den der
     * Bildschirm ohnehin schon hat — damit bleibt der Aufruf bei
     * docs/decisions/0011-schreibvorgaenge-nicht-abwarten.md und funktioniert offline.
     *
     * Ein leerer Aufruf schreibt nichts.
     */
    fun deleteTodos(listId: String, todoIds: List<String>): Result<Unit>

    /**
     * Legt eine gelöschte Aufgabe wieder an — das „Rückgängig" der Snackbar nach dem Löschen, siehe
     * docs/decisions/0031-rueckgaengig-statt-rueckfrage-beim-loeschen.md.
     *
     * **Unter ihrer alten Dokument-id**, nicht unter einer neuen: Nur so ist „rückgängig" die
     * Wahrheit und nicht ein neuer Eintrag, der dem alten gleicht. Möglich ist das, weil die id aus
     * [todo] kommt und der Pfad damit bekannt ist — anders als beim Verschieben, wo das Ziel
     * bauartbedingt eine neue id braucht (ADR 0024).
     *
     * [todo] ist der letzte Stand aus dem Snapshot, nicht der Inhalt eines offenen Dialogs. Alle
     * fachlichen Felder kehren unverändert zurück, `updatedAt` kommt neu vom Server — dieselbe
     * Aufteilung wie beim Verschieben (ADR 0026).
     *
     * Wie [addTodo] nicht abgewartet, das Rückgängig funktioniert also offline
     * (docs/decisions/0011-schreibvorgaenge-nicht-abwarten.md).
     */
    fun restoreTodo(listId: String, todo: Todo): Result<Unit>

    /**
     * Ordnet eine Aufgabe von Hand in ihre Prioritätsgruppe ein, siehe
     * docs/decisions/0039-manuelle-sortierung-ueber-createdat-als-anker.md.
     *
     * [sortOrder] ist der **bereits gerechnete** Wert — gerechnet wird in der Domäne
     * (`TodoSortOrder.kt`), das Repository schreibt nur, was es bekommt. Dieselbe Aufteilung wie bei
     * der Menge (ADR 0037).
     *
     * Feldweise wie [setDone] und nicht als ganzes Dokument: Was der Partner gleichzeitig an Titel
     * oder Notiz ändert, überlebt. Nicht abgewartet
     * (docs/decisions/0011-schreibvorgaenge-nicht-abwarten.md), Sortieren funktioniert also offline.
     */
    fun setSortOrder(listId: String, todoId: String, sortOrder: Double): Result<Unit>

    /**
     * Vergibt die Plätze einer ganzen Prioritätsgruppe neu — **die Reparatur, nicht der Normalfall.**
     *
     * Gebraucht wird sie nur, wenn sich ein Platz nicht mehr als Zahl zwischen zwei Nachbarn
     * ausdrücken lässt: zwei Nachbarn mit demselben Anker, oder eine Lücke, die durch wiederholtes
     * Hineinziehen unter die Auflösung von `Double` geschrumpft ist. Beides erkennt
     * `sortOrderBetween` daran, dass es null liefert.
     *
     * Ein Batch, damit die Gruppe nie halb umnummeriert dasteht — aber feldweise `update()` statt
     * `set()`, damit auch hier überlebt, was der Partner gleichzeitig am Titel ändert. Eine
     * Prioritätsgruppe ist menschengroß, das 500er-Limit eines Batches ist keine Schranke, die man
     * hier erreicht.
     */
    fun renumberTodos(listId: String, sortOrders: Map<String, Double>): Result<Unit>
}
