package eu.sweetgeorgie.browniedo.data.todo

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Firestore representation of a todo. Needs default values for every property so that Firestore
 * can deserialize it through the no-argument constructor.
 *
 * The timestamps are nullable because [ServerTimestamp] leaves them empty until the server has
 * accepted the write. Reading with `ServerTimestampBehavior.ESTIMATE` fills them with a local
 * estimate, so entries created offline stay usable until they sync.
 *
 * The document id is not stored as a field — it only exists on the snapshot. The same holds for
 * the list a todo belongs to: it is given by the path `lists/{listId}/todos/{todoId}`, see
 * docs/decisions/0009-listen-dokument-mit-todo-subcollection.md.
 */
data class TodoDocument(
    var title: String = "",
    var done: Boolean = false,
    @ServerTimestamp var createdAt: Date? = null,
    @ServerTimestamp var updatedAt: Date? = null,
    var completedBy: String? = null,
    /**
     * Bewusst **ohne** [ServerTimestamp]: Die Annotation füllt ein leeres Feld beim Schreiben des
     * ganzen Objekts, und `addTodo` schreibt das ganze Objekt. Jede frisch angelegte, offene
     * Aufgabe käme damit mit einem Erledigungszeitpunkt auf die Welt. Gesetzt wird das Feld
     * stattdessen gezielt in `setDone`.
     */
    var completedAt: Date? = null,
    /**
     * Name einer `TodoPriority`, siehe docs/decisions/0023-prioritaet-migration-und-sortierung.md.
     *
     * Absichtlich ein String und kein Enum-Typ: Firestore wirft beim Abbilden eines unbekannten
     * Enum-Werts, und zwar innerhalb des Snapshot-Listeners — der Fehler käme nicht als `Result`
     * heraus, sondern risse die ganze Aktualisierung mit. Ein Gerät mit neuerer App-Version, das
     * eine vierte Stufe schreibt, würde das ältere damit lahmlegen. Nullable, weil Aufgaben von
     * vor Phase 9 das Feld nicht haben und in der Console von Hand editiert wird.
     */
    var priority: String? = null,
    /**
     * Die Notiz zur Aufgabe. Nullable und mit Standardwert `null`, und beides trägt die Migration:
     * Aufgaben von vor Phase 12 haben das Feld nicht, und Firestores `toObject` lässt ein fehlendes
     * Feld genau auf diesem Standardwert stehen. Kein Nachziehen in der Console nötig — anders als
     * bei der Priorität braucht es hier auch keinen Rückfallwert, weil „keine Notiz" die richtige
     * Antwort ist und nicht ein Ersatz für eine fehlende.
     */
    var notes: String? = null
)

/**
 * Field names as stored in Firestore. A field-level update cannot go through [TodoDocument], so
 * the names are needed literally — and must stay in sync with its properties.
 */
internal object TodoField {
    const val TITLE = "title"
    const val DONE = "done"
    const val UPDATED_AT = "updatedAt"
    const val COMPLETED_BY = "completedBy"
    const val COMPLETED_AT = "completedAt"
    const val PRIORITY = "priority"
    const val NOTES = "notes"
}
