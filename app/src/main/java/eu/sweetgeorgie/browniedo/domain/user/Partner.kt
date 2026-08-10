package eu.sweetgeorgie.browniedo.domain.user

/**
 * The other person using the app.
 *
 * BrownieDo is built for exactly two people, so "the partner" is simply the one entry in `users`
 * that is not the signed-in one — see
 * docs/decisions/0020-partner-aus-users-collection.md.
 */
data class Partner(
    val uid: String,
    val displayName: String
)
