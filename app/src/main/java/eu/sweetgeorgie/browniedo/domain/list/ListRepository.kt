package eu.sweetgeorgie.browniedo.domain.list

import eu.sweetgeorgie.browniedo.domain.todo.Todo
import kotlinx.coroutines.flow.Flow

interface ListRepository {
    /**
     * Lists the signed-in user belongs to, ordered by name. Emits again on every remote or local
     * change, and an empty list while nobody is signed in.
     *
     * Vorlagen kommen hier mit — sie sind Listen mit gesetztem [TodoList.isTemplate], siehe
     * docs/decisions/0034-vorlagen-sind-listen-mit-einem-flag.md. Wer sie getrennt braucht, trennt
     * sie selbst; eine zweite Query wäre dasselbe Ergebnis für den doppelten Preis.
     *
     * A failure means the lists could not be observed — the previously emitted ones stay valid
     * until the next successful emission, same as for todos.
     */
    val lists: Flow<Result<List<TodoList>>>

    /**
     * Creates a list. [shared] decides whether the partner is put on it as well; who that is stays
     * inside the data layer, see docs/decisions/0020-partner-aus-users-collection.md.
     *
     * [isTemplate] entscheidet nur, in welchem Abschnitt der Auswahl die Liste erscheint und was man
     * mit ihr anfangen kann — geschrieben wird in beiden Fällen dasselbe Dokument.
     *
     * Fails when [shared] is true and no partner is known — the caller is expected to not offer the
     * option in that case, this is the second line of defence.
     *
     * Unlike the todo writes this suspends: the partner has to be looked up first, see
     * docs/decisions/0019-schreibrechte-auf-listen-dokumente.md.
     */
    suspend fun createList(name: String, shared: Boolean, isTemplate: Boolean): Result<Unit>

    /**
     * Legt eine **Arbeitsliste** mit [todos] als Inhalt an und gibt deren id zurück, damit der
     * Aufrufer sie öffnen kann. Der Weg von einer Vorlage zu einer konkreten Liste, siehe
     * docs/decisions/0034-vorlagen-sind-listen-mit-einem-flag.md.
     *
     * Die Aufgaben werden übernommen, wie sie hereinkommen — welche Felder eine Instanz von ihrer
     * Vorlage erbt, entscheidet der Aufrufer und nicht die Datenschicht.
     *
     * **Das Listen-Dokument entsteht vor seinen Aufgaben, in einem eigenen Schreibvorgang.** Beides
     * zusammen in einem `WriteBatch` wäre atomar, scheitert aber an den Security Rules: Die Regel
     * auf `todos` schlägt die Mitglieder der Liste nach, und ein `get()` in den Rules sieht den
     * Stand *vor* dem Commit — die Liste existierte dort noch nicht. Ausführlich in
     * docs/decisions/0035-instanziieren-schreibt-die-liste-vor-ihren-aufgaben.md, samt der Frage,
     * was ein Fehlschlag zwischen beiden Schreibvorgängen hinterlässt.
     *
     * Suspendiert allein wegen des Partner-Nachschlags wie [createList]; auf den Server wartet der
     * Vorgang nicht, das Anlegen funktioniert also offline
     * (docs/decisions/0011-schreibvorgaenge-nicht-abwarten.md).
     */
    suspend fun createListFromTemplate(
        name: String,
        shared: Boolean,
        todos: List<Todo>
    ): Result<String>

    /**
     * Renames a list. Who is on it cannot be changed — `firestore.rules` rejects that outright.
     */
    fun renameList(listId: String, name: String): Result<Unit>

    /**
     * Deletes a list **and every todo in it**. Firestore does not cascade, so the entries have to go
     * explicitly; the order matters and is guarded, see
     * docs/decisions/0019-schreibrechte-auf-listen-dokumente.md.
     */
    suspend fun deleteList(listId: String): Result<Unit>
}
