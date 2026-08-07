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
    var completedBy: String? = null
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
}
