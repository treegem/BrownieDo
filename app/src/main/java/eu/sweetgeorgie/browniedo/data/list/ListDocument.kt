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
    /**
     * Ob dieses Dokument eine Vorlage ist, siehe
     * docs/decisions/0034-vorlagen-sind-listen-mit-einem-flag.md.
     *
     * Nicht-nullable mit Vorgabe `false`, und das trägt die Migration: Listen von vor Phase 14 haben
     * das Feld nicht, und Firestores `toObject` lässt ein fehlendes Feld genau auf diesem Wert
     * stehen. Kein Nachziehen in der Console nötig — „fehlt" heißt hier richtigerweise „keine
     * Vorlage".
     */
    var isTemplate: Boolean = false,
    @ServerTimestamp var createdAt: Date? = null
)

/**
 * Field names as stored in Firestore. [MEMBERS] is needed literally for the query filter and [NAME]
 * for renaming, neither of which can go through [ListDocument].
 */
internal object ListField {
    const val NAME = "name"
    const val MEMBERS = "members"
}
