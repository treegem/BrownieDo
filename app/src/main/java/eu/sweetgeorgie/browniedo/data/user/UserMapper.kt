package eu.sweetgeorgie.browniedo.data.user

import eu.sweetgeorgie.browniedo.domain.user.Partner

/**
 * Maps a Firestore document onto the domain model.
 *
 * Returns null for a blank display name — the app would have nothing to show, and the entry was
 * plainly filled in wrong. Same reasoning as for lists and todos: a placeholder would hide it.
 */
fun UserDocument.toPartner(uid: String): Partner? {
    if (displayName.isBlank()) return null

    return Partner(uid = uid, displayName = displayName)
}
