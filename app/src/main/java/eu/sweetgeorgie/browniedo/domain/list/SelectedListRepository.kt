package eu.sweetgeorgie.browniedo.domain.list

import kotlinx.coroutines.flow.Flow

interface SelectedListRepository {
    /**
     * Id of the list last picked on this device, or null before anything was picked.
     *
     * The id is stored raw, without checking whether that list still exists — whoever collects this
     * has the current lists at hand and can fall back, see
     * docs/decisions/0018-datastore-fuer-die-zuletzt-gewaehlte-liste.md.
     */
    val selectedListId: Flow<String?>

    suspend fun select(listId: String)
}
