package eu.sweetgeorgie.browniedo.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import eu.sweetgeorgie.browniedo.domain.user.Partner

@Composable
internal fun NewListDialog(
    newList: NewList,
    partner: Partner?,
    onNameChange: (String) -> Unit,
    onSharedChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.todo_list_new_list_headline)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newList.name,
                    onValueChange = onNameChange,
                    label = { Text(text = stringResource(R.string.todo_list_list_name_label)) },
                    singleLine = true
                )
                ListKindOption(
                    label = stringResource(R.string.todo_list_keep_private),
                    selected = !newList.shared,
                    onClick = { onSharedChange(false) }
                )
                ListKindOption(
                    // Der Name macht greifbar, mit wem geteilt wird — „Geteilt" allein sagt es nicht.
                    label = partner?.let {
                        stringResource(R.string.todo_list_share_with, it.displayName)
                    } ?: stringResource(R.string.todo_list_share_impossible),
                    selected = newList.shared,
                    enabled = partner != null,
                    onClick = { onSharedChange(true) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = newList.name.isNotBlank()) {
                Text(text = stringResource(R.string.todo_list_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.todo_list_cancel))
            }
        }
    )
}

@Composable
private fun ListKindOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(
            text = label,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
internal fun RenameListDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.todo_list_rename_list)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(text = stringResource(R.string.todo_list_list_name_label)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) {
                Text(text = stringResource(R.string.todo_list_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.todo_list_cancel))
            }
        }
    )
}

/**
 * Eigener Bestätigungsschritt, anders als beim Löschen einer Aufgabe: Hier gehen alle Einträge mit,
 * auch auf dem Gerät des Partners, und es gibt kein Zurück. Die Anzahl steht bewusst im Text —
 * sie ist die Information, die die Folge greifbar macht.
 */
@Composable
internal fun DeleteListDialog(
    list: TodoList,
    todoCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.todo_list_delete_list_headline)) },
        text = {
            Text(
                text = if (todoCount == 0) {
                    stringResource(R.string.todo_list_delete_list_question_empty, list.name)
                } else {
                    pluralStringResource(
                        R.plurals.todo_list_delete_list_question,
                        todoCount,
                        list.name,
                        todoCount
                    )
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.todo_list_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.todo_list_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditTodoDialog(
    title: String,
    priority: TodoPriority,
    lists: List<TodoList>,
    targetListId: String,
    actions: TodoEditActions
) {
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(text = stringResource(R.string.todo_list_edit_headline)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = actions.onTitleChange,
                    label = { Text(text = stringResource(R.string.todo_list_title_label)) },
                    singleLine = true
                )
                Text(
                    text = stringResource(R.string.todo_list_priority_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                // Drei feste Stufen, genau eine gewählt — dafür ist die Segment-Auswahl gemacht.
                // Radio-Zeilen wie in NewListDialog bräuchten hier drei volle Zeilen.
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TodoPriority.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = entry == priority,
                            onClick = { actions.onPriorityChange(entry) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = TodoPriority.entries.size
                            ),
                            label = { Text(text = stringResource(entry.labelResId())) }
                        )
                    }
                }
                TargetListField(
                    lists = lists,
                    targetListId = targetListId,
                    onTargetListChange = actions.onTargetListChange
                )
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onConfirm, enabled = title.isNotBlank()) {
                Text(text = stringResource(R.string.todo_list_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = actions.onDelete) {
                    Text(
                        text = stringResource(R.string.todo_list_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = actions.onDismiss) {
                    Text(text = stringResource(R.string.todo_list_cancel))
                }
            }
        }
    )
}

/**
 * Die Zielliste ist ein Feld wie Titel und Priorität, kein eigener Auslöser: Erst „Speichern" führt
 * aus, was hier gewählt wurde, siehe docs/decisions/0022-verschieben-im-bearbeiten-dialog.md.
 *
 * Die aktuelle Liste steht mit im Menü und ist vorausgewählt — sonst gäbe es nach einem Fehlgriff
 * keinen Weg zurück zu „bleibt, wo sie ist", außer den Dialog abzubrechen.
 */
@Composable
private fun TargetListField(
    lists: List<TodoList>,
    targetListId: String,
    onTargetListChange: (TodoList) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // Mit nur einer Liste gibt es kein Ziel. Das Feld bleibt trotzdem stehen, statt zu
    // verschwinden: Ein Dialog, der je nach Anzahl der Listen anders aussieht, ist schwerer zu
    // lernen als einer mit einem abgeblendeten Feld.
    val enabled = lists.size > 1
    val target = lists.firstOrNull { it.id == targetListId }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.todo_list_target_list_label),
            style = MaterialTheme.typography.bodyMedium
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // enabled = false nimmt auch die Klick-Semantik weg, TalkBack bietet die
                    // Aktion dann gar nicht erst an.
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = target?.name.orEmpty(),
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_drop_down),
                    contentDescription = stringResource(R.string.todo_list_choose_target_list),
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                lists.forEach { list ->
                    ListMenuItem(
                        list = list,
                        // Hier heißt „ausgewählt": da landet sie beim Speichern.
                        isSelected = list.id == targetListId,
                        onClick = {
                            expanded = false
                            onTargetListChange(list)
                        }
                    )
                }
            }
        }
    }
}
