package eu.sweetgeorgie.browniedo.ui.todo

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.domain.list.TodoList

// TopAppBar selbst ist stabil, aber seine Vorgabewerte stammen aus der noch experimentellen
// TopAppBarDefaults-API.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodoListTopBar(
    lists: List<TodoList>,
    templates: List<TodoList>,
    selectedList: TodoList?,
    hasFinishedTodos: Boolean,
    actions: TodoListTopBarActions
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var listMenuExpanded by remember { mutableStateOf(false) }
    val isTemplateOpen = selectedList?.isTemplate == true
    // Überschriften gibt es erst, wenn es zwei Abschnitte zu unterscheiden gibt. Wer nie eine
    // Vorlage anlegt, sieht dasselbe Menü wie vor Phase 14.
    val hasSections = templates.isNotEmpty()

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
                    // Ohne die Markierung sähe eine offene Vorlage aus wie eine Liste, in der sich
                    // nur nichts abhaken lässt.
                    if (isTemplateOpen) {
                        Icon(
                            painter = painterResource(R.drawable.ic_template),
                            contentDescription = stringResource(R.string.todo_list_template),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(20.dp)
                        )
                    }
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
                    // Eine Überschrift ohne Einträge darunter wäre schlimmer als keine: Wer nur
                    // Vorlagen hat, bekommt kein leeres „Listen" zu sehen.
                    if (hasSections && lists.isNotEmpty()) {
                        SectionLabel(R.string.todo_list_lists_section)
                    }
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
                    if (hasSections) {
                        if (lists.isNotEmpty()) HorizontalDivider()
                        SectionLabel(R.string.todo_list_templates_section)
                        templates.forEach { template ->
                            ListMenuItem(
                                list = template,
                                isSelected = template.id == selectedList?.id,
                                onClick = {
                                    listMenuExpanded = false
                                    actions.onListSelected(template)
                                }
                            )
                        }
                    }
                    if (lists.isNotEmpty() || hasSections) HorizontalDivider()
                    AddMenuItem(
                        labelResId = R.string.todo_list_new_list,
                        onClick = {
                            listMenuExpanded = false
                            actions.onNewListClick()
                        }
                    )
                    AddMenuItem(
                        labelResId = R.string.todo_list_new_template,
                        onClick = {
                            listMenuExpanded = false
                            actions.onNewTemplateClick()
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
                // es nichts zu tun. Beide gelten für Vorlagen genauso, nur heißen sie dort anders:
                // Eine Vorlage ist eine Liste, aber niemand nennt sie so (ADR 0034).
                if (selectedList != null) {
                    // Steht über Umbenennen und Löschen und **ohne** Fehlerfarbe: Das ist Aufräumen,
                    // nicht Zerstören — und es ist die Aktion, wegen der man das Menü nach einer
                    // Woche überhaupt öffnet (ADR 0040). Ohne Erledigtes gibt es sie nicht, in einer
                    // Vorlage nie: Dort wird nicht abgehakt (ADR 0034).
                    if (!isTemplateOpen && hasFinishedTodos) {
                        DropdownMenuItem(
                            text = {
                                Text(text = stringResource(R.string.todo_list_delete_finished))
                            },
                            onClick = {
                                menuExpanded = false
                                actions.onDeleteFinishedClick()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(
                                    if (isTemplateOpen) {
                                        R.string.todo_list_rename_template
                                    } else {
                                        R.string.todo_list_rename_list
                                    }
                                )
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            actions.onRenameListClick()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(
                                    if (isTemplateOpen) {
                                        R.string.todo_list_delete_template
                                    } else {
                                        R.string.todo_list_delete_list
                                    }
                                ),
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
 * Überschrift eines Menü-Abschnitts. Kein [DropdownMenuItem] — die Zeile ist Beschriftung und nicht
 * antippbar, und ein deaktivierter Eintrag sähe aus wie eine Aktion, die gerade nicht geht.
 */
@Composable
private fun SectionLabel(@StringRes labelResId: Int) {
    Text(
        text = stringResource(labelResId),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** „Neue Liste …" und „Neue Vorlage …" — gleicher Bau, gleiches Symbol, nur die Beschriftung wechselt. */
@Composable
private fun AddMenuItem(@StringRes labelResId: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = stringResource(labelResId)) },
        onClick = onClick,
        leadingIcon = {
            Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
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
