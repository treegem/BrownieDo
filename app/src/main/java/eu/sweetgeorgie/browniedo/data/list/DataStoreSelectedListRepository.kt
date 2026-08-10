package eu.sweetgeorgie.browniedo.data.list

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import eu.sweetgeorgie.browniedo.domain.list.SelectedListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Remembers the picked list on this device, see
 * docs/decisions/0018-datastore-fuer-die-zuletzt-gewaehlte-liste.md.
 *
 * Deliberately per device rather than per user: the choice is a view setting, not shared data, and
 * keeping it local avoids a Firestore collection the security rules would have to cover.
 */
class DataStoreSelectedListRepository(private val dataStore: DataStore<Preferences>) :
    SelectedListRepository {

    override val selectedListId: Flow<String?> =
        dataStore.data.map { preferences -> preferences[SELECTED_LIST_ID] }

    override suspend fun select(listId: String) {
        dataStore.edit { preferences -> preferences[SELECTED_LIST_ID] = listId }
    }

    private companion object {
        val SELECTED_LIST_ID = stringPreferencesKey("selected_list_id")
    }
}

/**
 * The delegate has to be declared once per file at top level — creating a second DataStore for the
 * same file would throw at runtime.
 */
private val Context.selectedListDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "selected_list"
)

/** Reaches the single DataStore instance from the dependency graph. */
fun selectedListDataStoreOf(context: Context): DataStore<Preferences> = context.selectedListDataStore
