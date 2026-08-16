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
    var notes: String? = null,
    /**
     * Die Menge eines Vorlagen-Eintrags, siehe
     * docs/decisions/0037-menge-am-eintrag-statt-zahl-im-titel.md. Nullable mit Standardwert `null`,
     * und beides trägt wie bei [notes] die Migration: Aufgaben von vor Phase 14b haben das Feld
     * nicht, und „keine Menge" ist die richtige Antwort statt eines Ersatzwerts.
     *
     * `Double` und nicht `Long`, damit Halbe möglich sind („0,5 Rolle pro Tag"). Wird das Feld von
     * Hand in der Console als Ganzzahl eingetippt, liegt in Firestore ein `Long` — dessen Mapper
     * wandelt das für ein `Double`-Feld um.
     */
    var quantity: Double? = null,
    /**
     * Der von Hand vergebene Platz innerhalb der Prioritätsstufe, siehe
     * docs/decisions/0039-manuelle-sortierung-ueber-createdat-als-anker.md. Nullable mit Standardwert
     * `null` wie [notes] und [quantity], und wieder trägt beides die Migration: Aufgaben von vor
     * Phase 15 haben das Feld nicht, und „nie von Hand einsortiert" ist die richtige Antwort.
     *
     * Die Werte liegen im Zahlenraum der Millisekunden seit 1970, weil genau das der Rückfallwert
     * ist, wenn das Feld fehlt. In der Console sieht ein Wert deshalb aus wie ein Zeitstempel — das
     * ist Absicht und keine Verwechslung.
     */
    var sortOrder: Double? = null
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
    const val QUANTITY = "quantity"
    const val SORT_ORDER = "sortOrder"
}
