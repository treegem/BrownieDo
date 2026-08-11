package eu.sweetgeorgie.browniedo.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.domain.list.TodoList

// TopAppBar selbst ist stabil, aber seine Vorgabewerte stammen aus der noch experimentellen
// TopAppBarDefaults-API.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodoListTopBar(
    lists: List<TodoList>,
    selectedList: TodoList?,
    actions: TodoListTopBarActions
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var listMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            // Der Titel ist die Listen-Auswahl: Unten rechts ist durch die Eingabeleiste belegt,
            // Primäraktionen gehören deshalb in die TopAppBar — siehe ADR 0013.
            Box {
                Row(
                    // Auch ohne Liste antippbar: „Neue Liste" steckt in diesem Menü, sonst käme man
                    // an die erste Liste nie heran.
                    modifier = Modifier.clickable { listMenuExpanded = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = selectedList?.name ?: stringResource(R.string.app_name))
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_drop_down),
                        contentDescription = stringResource(R.string.todo_list_choose_list)
                    )
                }
                DropdownMenu(
                    expanded = listMenuExpanded,
                    onDismissRequest = { listMenuExpanded = false }
                ) {
                    lists.forEach { list ->
                        ListMenuItem(
                            list = list,
                            isSelected = list.id == selectedList?.id,
                            onClick = {
                                listMenuExpanded = false
                                actions.onListSelected(list)
                            }
                        )
                    }
                    if (lists.isNotEmpty()) HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.todo_list_new_list)) },
                        onClick = {
                            listMenuExpanded = false
                            actions.onNewListClick()
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.todo_list_more_actions)
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                // Umbenennen und Löschen beziehen sich immer auf die offene Liste — ohne eine gibt
                // es nichts zu tun.
                if (selectedList != null) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.todo_list_rename_list)) },
                        onClick = {
                            menuExpanded = false
                            actions.onRenameListClick()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.todo_list_delete_list),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            actions.onDeleteListClick()
                        }
                    )
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.todo_list_sign_out)) },
                    onClick = {
                        menuExpanded = false
                        actions.onSignOutClick()
                    }
                )
            }
        }
    )
}

/**
 * Ein Listen-Eintrag mit Symbol für geteilt/privat.
 *
 * Steht hier statt in den Dialogen, weil die TopAppBar der ursprüngliche Ort ist: Das Zielliste-Feld
 * im Bearbeiten-Dialog verwendet ihn nach
 * docs/decisions/0022-verschieben-im-bearbeiten-dialog.md ausdrücklich wieder, statt ein zweites
 * Menü zu bauen.
 */
@Composable
internal fun ListMenuItem(list: TodoList, isSelected: Boolean, onClick: () -> Unit) {
    val kindLabel = stringResource(
        if (list.isShared) R.string.todo_list_shared_list else R.string.todo_list_private_list
    )

    DropdownMenuItem(
        text = { Text(text = list.name) },
        onClick = onClick,
        leadingIcon = {
            Icon(
                painter = painterResource(
                    if (list.isShared) R.drawable.ic_group else R.drawable.ic_person
                ),
                contentDescription = kindLabel
            )
        },
        // Die aktuelle Liste hebt sich über die Farbe ab statt über ein zweites Symbol — rechts
        // steht sonst nichts, und ein Häkchen neben dem Listen-Symbol wäre eine Reihe zu viel.
        colors = MenuDefaults.itemColors(
            textColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            leadingIconColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    )
}
