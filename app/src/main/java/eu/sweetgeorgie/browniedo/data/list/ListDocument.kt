package eu.sweetgeorgie.browniedo.data.list

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Firestore representation of a list. Needs default values for every property so that Firestore
 * can deserialize it through the no-argument constructor.
 *
 * The document id is not stored as a field — it only exists on the snapshot, same as for
 * `TodoDocument`. Which fields a list carries is settled in
 * docs/decisions/0009-listen-dokument-mit-todo-subcollection.md.
 */
data class ListDocument(
    var name: String = "",
    /** Uids allowed to see the list. One entry means private, more than one means shared. */
    var members: List<String> = emptyList(),
    @ServerTimestamp var createdAt: Date? = null
)

/**
 * Field names as stored in Firestore. [MEMBERS] is needed literally for the query filter, which
 * cannot go through [ListDocument].
 */
internal object ListField {
    const val MEMBERS = "members"
}
