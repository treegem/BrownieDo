package eu.sweetgeorgie.browniedo.domain.user

import kotlinx.coroutines.flow.Flow

interface PartnerRepository {
    /**
     * The other person, or null while nobody is signed in and whenever no second entry exists in
     * `users`. Only with a known partner can a shared list be created at all.
     *
     * Failures are not reported: a missing partner and an unreadable one lead to the same place —
     * shared lists cannot be offered. The lists themselves keep working either way.
     */
    val partner: Flow<Partner?>
}
