package eu.sweetgeorgie.browniedo.ui.todo

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import eu.sweetgeorgie.browniedo.domain.user.Partner

/**
 * Für die einzeiligen Felder der Dialoge. Großschreibung wie in der Eingabeleiste, damit dieselbe
 * Aufgabe beim Bearbeiten nicht anders behandelt wird als beim Anlegen — **`Sentences` und nicht
 * `Words`**: Im Deutschen würde `Words` auch Präpositionen groß schreiben („Zeug Für Oma").
 *
 * `Done` steht bewusst **ohne** `keyboardActions` da: Die Taste schließt damit nur die Tastatur und
 * speichert nicht. Den Dialog beendet allein die Knopfzeile, siehe
 * docs/decisions/0032-gefuellte-bestaetigung-und-loeschen-im-inhalt.md. Angegeben wird die Aktion
 * trotzdem, weil Tastaturen bei `Unspecified` unterschiedliche Tasten zeigen.
 */
private val DIALOG_TEXT_KEYBOARD_OPTIONS = KeyboardOptions(
    capitalization = KeyboardCapitalization.Sentences,
    imeAction = ImeAction.Done
)

/**
 * Für die Notiz. Wie [DIALOG_TEXT_KEYBOARD_OPTIONS], aber **ohne `imeAction`**: In einem
 * mehrzeiligen Feld verdrängt eine erzwungene Aktion die Zeilenumbruch-Taste — und genau die braucht
 * eine Notiz.
 */
private val DIALOG_MULTILINE_KEYBOARD_OPTIONS =
    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

/**
 * Der Schriftstil für die Eingabe in einem Dialogfeld.
 *
 * Muss angegeben werden: `OutlinedTextField` erbt seinen `textStyle` von `LocalTextStyle`, und der
 * `text`-Slot von `AlertDialog` setzt dort `bodyMedium` (`DialogTokens.SupportingTextFont`). Ohne
 * diese Angabe tippt man in den Dialogen also in 14 sp, während dasselbe Feld in der Eingabeleiste
 * 16 sp hat — und im leeren Feld wäre die Beschriftung größer als der Text, den man dann tippt.
 */
private val dialogTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge

/**
 * Der Dialog für alle drei Wege, auf denen ein Dokument in `lists` entsteht — neue Liste, neue
 * Vorlage, Liste aus einer Vorlage. Sie haben dieselben zwei Eingaben, es wechselt nur die
 * Überschrift, siehe docs/decisions/0034-vorlagen-sind-listen-mit-einem-flag.md.
 */
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
        title = { Text(text = stringResource(newList.kind.headlineResId())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newList.name,
                    onValueChange = onNameChange,
                    label = { Text(text = stringResource(R.string.todo_list_list_name_label)) },
                    singleLine = true,
                    keyboardOptions = DIALOG_TEXT_KEYBOARD_OPTIONS,
                    textStyle = dialogTextStyle,
                    modifier = Modifier.fillMaxWidth()
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
            Button(onClick = onConfirm, enabled = newList.name.isNotBlank()) {
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

@StringRes
private fun NewListKind.headlineResId(): Int = when (this) {
    NewListKind.LIST -> R.string.todo_list_new_list_headline
    NewListKind.TEMPLATE -> R.string.todo_list_new_template_headline
    NewListKind.FROM_TEMPLATE -> R.string.todo_list_create_list_from_template_headline
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
        // Die Farbe bleibt von Hand gesetzt, obwohl der text-Slot des Dialogs schon eine liefert:
        // Das hier ist Wert-Text und soll sich aus der Stützfarbe herausheben — und ein nackter
        // `Text` hat keinen enabled-Zustand, der das von allein könnte. Eine `style`-Angabe wäre
        // dagegen redundant, der Slot setzt bereits `bodyMedium`.
        Text(
            text = label,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
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
                singleLine = true,
                keyboardOptions = DIALOG_TEXT_KEYBOARD_OPTIONS,
                textStyle = dialogTextStyle,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = name.isNotBlank()) {
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
        title = {
            Text(
                text = stringResource(
                    if (list.isTemplate) {
                        R.string.todo_list_delete_template_headline
                    } else {
                        R.string.todo_list_delete_list_headline
                    }
                )
            )
        },
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
            // Die einzige gefüllte Bestätigung in Fehlerfarbe: Hier *ist* das Löschen die Hauptaktion
            // des Dialogs, und es gibt kein Rückgängig wie bei einer Aufgabe (ADR 0031 gilt nur für
            // die). Die Bremse ist der Dialog selbst, nicht ein leiser Knopf — siehe ADR 0032.
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(text = stringResource(R.string.todo_list_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.todo_list_cancel))
            }
        }
    )
}

// Für die Segment-Auswahl der Priorität; das Zielliste-Feld braucht denselben Opt-in noch einmal für
// `ExposedDropdownMenuBox` und trägt ihn selbst.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditTodoDialog(
    title: String,
    notes: String,
    priority: TodoPriority,
    lists: List<TodoList>,
    targetListId: String,
    isTemplateEntry: Boolean,
    actions: TodoEditActions
) {
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(text = stringResource(R.string.todo_list_edit_headline)) },
        text = {
            // Der Dialog trägt viel. Der text-Slot von AlertDialog begrenzt seine Höhe zwar,
            // scrollt aber nicht von allein — mit offener Tastatur wäre der untere Teil sonst
            // abgeschnitten.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = actions.onTitleChange,
                    label = { Text(text = stringResource(R.string.todo_list_title_label)) },
                    singleLine = true,
                    keyboardOptions = DIALOG_TEXT_KEYBOARD_OPTIONS,
                    textStyle = dialogTextStyle,
                    modifier = Modifier.fillMaxWidth()
                )
                // Mehrzeilig, weil ein Backlog-Eintrag nach Wochen mehr braucht als eine Zeile —
                // und mit Obergrenze, damit eine lange Notiz den Dialog nicht auffrisst.
                OutlinedTextField(
                    value = notes,
                    onValueChange = actions.onNotesChange,
                    label = { Text(text = stringResource(R.string.todo_list_notes_label)) },
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = DIALOG_MULTILINE_KEYBOARD_OPTIONS,
                    textStyle = dialogTextStyle,
                    modifier = Modifier.fillMaxWidth()
                )
                // Beschriftung und Steuerung sind ein Element: 4 dp innerhalb der Gruppe, die 8 dp
                // des äußeren Column zwischen den Gruppen. Dieselbe Geometrie benutzt Material für
                // eine Beschriftung über einem Feld (AboveLabelBottomPadding).
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.todo_list_priority_label),
                        // Wie die verkleinerte Beschriftung eines Textfelds, damit die
                        // Segment-Auswahl beschriftet wirkt wie Titel und Notiz. **Bewusst ohne
                        // Farbe:** Der text-Slot von AlertDialog liefert onSurfaceVariant, und das
                        // ist genau die Label-Farbe eines Textfelds — eine eigene Angabe wäre eine
                        // zweite Quelle für denselben Wert.
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
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
                }
                TargetListField(
                    lists = lists,
                    targetListId = targetListId,
                    onTargetListChange = actions.onTargetListChange
                )
                // Die Aktionen, die nichts speichern, stehen unter den Feldern: Die Knopfzeile
                // trägt nur, was den Dialog beendet (ADR 0032). Beim Termin kommt der Titel aus dem
                // Feld — was auf dem Bildschirm steht, geht in den Kalender.
                //
                // In einer Vorlage entfällt er: Ein Vorlagen-Eintrag hat keinen konkreten Tag, und
                // ein geratenes Datum hat ADR 0027 ausdrücklich verworfen. Damit trägt auch kein
                // Modus dieses Dialogs mehr Eingaben als vor Phase 14 (ADR 0033, Auslöser 1).
                if (!isTemplateEntry) {
                    DialogAction(
                        iconResId = R.drawable.ic_event,
                        label = stringResource(R.string.todo_list_create_calendar_event),
                        contentColor = MaterialTheme.colorScheme.primary,
                        onClick = { actions.onCalendarEventClick(title) }
                    )
                }
                // Zuletzt und in Fehlerfarbe, aber nicht gefüllt: Ein zweiter gefüllter Knopf würde
                // mit „Speichern" um die Hauptaktion streiten. Hier statt in der Knopfzeile, damit
                // ein Fehlgriff neben „Speichern" nicht mehr möglich ist — der Knopf selbst bleibt,
                // weil ADR 0016 den Dialog als den mit TalkBack bedienbaren Löschweg verlangt.
                DialogAction(
                    iconResId = R.drawable.ic_delete,
                    label = stringResource(R.string.todo_list_delete),
                    contentColor = MaterialTheme.colorScheme.error,
                    onClick = actions.onDelete
                )
            }
        },
        confirmButton = {
            Button(onClick = actions.onConfirm, enabled = title.isNotBlank()) {
                Text(text = stringResource(R.string.todo_list_save))
            }
        },
        // Ein einzelner Knopf, kein eigenes Row: AlertDialog legt beide Slots in eine gemeinsame
        // AlertDialogFlowRow, und ein Row wäre dort ein unteilbares Element — passte es mit
        // „Speichern" nicht in eine Zeile, bräche der Umbruch vor „Speichern" (ADR 0032).
        dismissButton = {
            TextButton(onClick = actions.onDismiss) {
                Text(text = stringResource(R.string.todo_list_cancel))
            }
        }
    )
}

/**
 * Eine Aktion im Inhalt eines Dialogs: Symbol, Beschriftung, und sie führt sofort aus, statt den
 * Dialog zu bestätigen oder zu verwerfen. Der Bearbeiten-Dialog trägt Löschen immer und den Termin
 * nur außerhalb einer Vorlage, siehe
 * docs/decisions/0032-gefuellte-bestaetigung-und-loeschen-im-inhalt.md.
 *
 * [contentColor] hat bewusst keinen Standardwert: Die Farbe ist hier die Aussage — grün für harmlos,
 * Fehlerfarbe für destruktiv —, und ein Standard würde beim Hinzufügen der nächsten Aktion
 * unbemerkt die falsche wählen.
 */
@Composable
private fun DialogAction(
    @DrawableRes iconResId: Int,
    label: String,
    contentColor: Color,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor)
    ) {
        Icon(
            painter = painterResource(iconResId),
            // Die Beschriftung steht daneben; eine zweite Beschreibung würde TalkBack dieselbe
            // Aktion zweimal vorlesen.
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Text(text = label, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
}

/**
 * Die Zielliste ist ein Feld wie Titel und Notiz, kein eigener Auslöser: Erst „Speichern" führt aus,
 * was hier gewählt wurde, siehe docs/decisions/0022-verschieben-im-bearbeiten-dialog.md. Seit
 * docs/decisions/0033-bearbeiten-bleibt-ein-dialog.md ist es auch buchstäblich ein Feld — ein
 * schreibgeschütztes `OutlinedTextField` in einer `ExposedDropdownMenuBox`, also die Material-Komponente
 * für „eines aus N", statt eines selbstgebauten `Row` mit `clickable`.
 *
 * Die aktuelle Liste steht mit im Menü und ist vorausgewählt — sonst gäbe es nach einem Fehlgriff
 * keinen Weg zurück zu „bleibt, wo sie ist", außer den Dialog abzubrechen.
 *
 * **Kein `leadingIcon` mit dem Symbol für geteilt/privat:** Dessen `contentDescription` verschmilzt in
 * die Semantik des Felds, und `theListCannotBePickedWhileItIsTheOnlyOne` prüft genau, dass es die bei
 * nur einer Liste *nicht* gibt.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = target?.name.orEmpty(),
            // Der Wert kommt aus der Auswahl, nicht aus der Tastatur.
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(text = stringResource(R.string.todo_list_target_list_label)) },
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_drop_down),
                    // Ohne Beschreibung: Die Steuerung ist das Feld selbst, nicht der Pfeil.
                    // `menuAnchor` gibt ihm Rolle und Klick-Semantik, TalkBack liest damit
                    // „Liste, <Name>, Dropdown-Liste" — eine Beschreibung am Pfeil käme doppelt.
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f)
                )
            },
            textStyle = dialogTextStyle,
            modifier = Modifier
                // `enabled = false` hängt hier gar keine Menü-Semantik an (im Material-Quelltext
                // `if (!enabled) Modifier`) — Bedienhilfen sehen die Aufklapp-Aktion also nicht
                // einmal. Der `onClick` des Felds bleibt, ist aber als `disabled` markiert; deshalb
                // prüft der Test `assertIsNotEnabled` und nicht `assertHasNoClickAction`.
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
