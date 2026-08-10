package eu.sweetgeorgie.browniedo.data.user

/**
 * Firestore representation of one of the two people using the app. Needs a default value so that
 * Firestore can deserialize it through the no-argument constructor.
 *
 * The document id *is* the uid — that is why it carries no uid field, same as the todo and list
 * documents carry no id. Maintained by hand in the console, see
 * docs/decisions/0020-partner-aus-users-collection.md.
 */
data class UserDocument(
    var displayName: String = ""
)
