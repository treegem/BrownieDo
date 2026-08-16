package eu.sweetgeorgie.browniedo.ui.todo

import androidx.compose.runtime.Immutable
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority

/*
 * Die Rückrufe von [TodoListScreen], gebündelt nach ihrem Ort in der Oberfläche, siehe
 * docs/decisions/0028-rueckrufe-in-actions-haltern.md. Jedes private Composable des Bildschirms
 * bekommt damit genau einen Halter.
 *
 * Alle vier sind `@Immutable data class`, und das ist keine Kosmetik: Compose kann einen Parameter
 * nur überspringen, wenn er sich vergleichen lässt. Die von `data class` erzeugte `equals` vergleicht
 * die Rückrufe strukturell — und Kotlins gebundene Methodenreferenzen (`viewModel::onX`) sind
 * strukturell gleich, wenn Empfänger und Methode gleich sind. Zwei gleich gebaute Halter sind
 * deshalb gleich. Ohne `data class` bliebe nur Identitätsgleichheit, und der Bildschirm würde bei
 * jeder Rekomposition komplett neu zeichnen.
 *
 * **Keine Standardwerte.** Ein `= {}` wäre für Vorschau und Test bequem, ließe aber einen in
 * `MainActivity` vergessenen Rückruf still zu „tut nichts" werden. Vorschau und Test bauen ihre
 * No-op-Halter selbst.
 */

/**
 * Was die TopAppBar auslöst: die Listen-Auswahl im Titel und das Überlauf-Menü.
 *
 * [onNewTemplateClick] und [onCreateListFromTemplateClick] sind die beiden Wege, die es ohne
 * Vorlagen nicht gäbe. Umbenennen und Löschen brauchen dagegen keine eigenen Rückrufe — sie
 * beziehen sich auf die offene Liste, und eine Vorlage *ist* eine, siehe
 * docs/decisions/0034-vorlagen-sind-listen-mit-einem-flag.md.
 */
@Immutable
data class TodoListTopBarActions(
    val onListSelected: (TodoList) -> Unit,
    val onNewListClick: () -> Unit,
    val onNewTemplateClick: () -> Unit,
    val onCreateListFromTemplateClick: () -> Unit,
    val onRenameListClick: () -> Unit,
    val onDeleteListClick: () -> Unit,
    val onSignOutClick: () -> Unit
)

/**
 * Was die drei Listen-Dialoge auslösen. Geöffnet werden sie aus der TopAppBar heraus — die
 * `*Click`-Rückrufe stehen deshalb in [TodoListTopBarActions], hier steht nur, was im Dialog selbst
 * passiert.
 */
@Immutable
data class ListDialogActions(
    val onNewListNameChange: (String) -> Unit,
    val onNewListSharedChange: (Boolean) -> Unit,
    val onNewListConfirm: () -> Unit,
    val onNewListDismiss: () -> Unit,
    val onRenamedListNameChange: (String) -> Unit,
    val onRenameListConfirm: () -> Unit,
    val onRenameListDismiss: () -> Unit,
    val onDeleteListConfirm: () -> Unit,
    val onDeleteListDismiss: () -> Unit
)

/** Was die Eingabeleiste und die Zeilen der Liste auslösen. */
@Immutable
data class TodoActions(
    val onNewTodoTitleChange: (String) -> Unit,
    val onAddTodoClick: () -> Unit,
    val onTodoDoneChange: (Todo, Boolean) -> Unit,
    val onTodoSwipedAway: (Todo) -> Unit,
    val onEditTodoClick: (Todo) -> Unit
)

/**
 * Was der Bearbeiten-Dialog auslöst.
 *
 * Ohne `onEdited`-Präfix: Der Name des Halters trägt den Zusammenhang schon. Die ViewModel-Methoden
 * behalten ihre Namen, `MainActivity` verdrahtet `onTitleChange = viewModel::onEditedTitleChange`.
 *
 * [onCalendarEventClick] nimmt den Titel als Parameter entgegen, statt ihn im Halter einzufangen:
 * Ein Halter, der sich bei jedem Tastendruck im Titelfeld ändert, hebt die Überspringbarkeit des
 * Bildschirms auf. Vorbild ist [TodoActions.onEditTodoClick].
 */
@Immutable
data class TodoEditActions(
    val onTitleChange: (String) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onPriorityChange: (TodoPriority) -> Unit,
    val onTargetListChange: (TodoList) -> Unit,
    val onCalendarEventClick: (String) -> Unit,
    val onConfirm: () -> Unit,
    val onDelete: () -> Unit,
    val onDismiss: () -> Unit
)

/**
 * Was die Snackbars auslösen — der fünfte Halter, und der einzige, dessen „Bereich" kein sichtbares
 * Element des Bildschirms ist, sondern der `SnackbarHostState` des Scaffolds.
 *
 * Vorher standen [onErrorShown] und [onMovedMessageShown] als einzelne Parameter am Bildschirm, mit
 * der Begründung, sie gehörten keinem der vier Bereiche. Mit dem Rückgängig zum Löschen (ADR 0031)
 * wären es vier einzelne geworden — genug für einen eigenen Bereich, und der Bildschirm bleibt bei
 * acht Parametern statt auf zehn zu wachsen (ADR 0028).
 *
 * [onUndoDelete] und [onDeletedMessageShown] schließen sich gegenseitig aus: Der Bildschirm ruft je
 * nach [androidx.compose.material3.SnackbarResult] genau einen von beiden. Beide leeren
 * [TodoListUiState.deletedTodo] — der eine, weil die Aufgabe zurück ist, der andere, weil das
 * Angebot abgelaufen ist.
 */
@Immutable
data class SnackbarActions(
    val onErrorShown: () -> Unit,
    val onMovedMessageShown: () -> Unit,
    val onUndoDelete: () -> Unit,
    val onDeletedMessageShown: () -> Unit
)
