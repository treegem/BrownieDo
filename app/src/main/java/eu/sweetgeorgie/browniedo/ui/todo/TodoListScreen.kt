package eu.sweetgeorgie.browniedo.ui.todo

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import eu.sweetgeorgie.browniedo.ui.theme.BrownieDoTheme
import java.time.Instant

/**
 * Der Aufgaben-Bildschirm. Die Rückrufe kommen gebündelt statt einzeln, siehe
 * docs/decisions/0028-rueckrufe-in-actions-haltern.md — vorher waren es 27 Parameter, und jedes neue
 * Feld am Bearbeiten-Dialog kostete drei Stellen.
 *
 * [onErrorShown] und [onMovedMessageShown] stehen absichtlich einzeln: Sie gehören dem
 * [SnackbarHostState] dieses Scaffolds und keinem der vier Bereiche.
 */
@Composable
fun TodoListScreen(
    uiState: TodoListUiState,
    topBarActions: TodoListTopBarActions,
    listDialogActions: ListDialogActions,
    todoActions: TodoActions,
    editActions: TodoEditActions,
    onErrorShown: () -> Unit,
    onMovedMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // Auf die aufgelöste Meldung statt auf das Enum keyen: stringResource gehört in die
    // Composition, und so löst derselbe Fehler ein zweites Mal wieder eine Snackbar aus.
    val writeErrorMessage = uiState.error
        ?.takeIf { it != TodoListError.LOAD_FAILED }
        ?.let { stringResource(it.messageResId()) }

    LaunchedEffect(writeErrorMessage) {
        if (writeErrorMessage != null) {
            // showSnackbar wartet, bis die Snackbar wieder weg ist — erst danach den Fehler
            // löschen, sonst kippt der Key auf null und die Snackbar verschwindet sofort.
            snackbarHostState.showSnackbar(writeErrorMessage)
            onErrorShown()
        }
    }

    // Der zweite Kanal, aus demselben Grund auf die aufgelöste Meldung gekeyt. Zwei Effekte am
    // selben SnackbarHostState streiten sich nicht: showSnackbar nimmt einen fairen Mutex, die
    // Meldungen reihen sich also an, statt einander zu verdrängen.
    val moveConfirmation = uiState.movedToListName
        ?.let { stringResource(R.string.todo_list_moved_to, it) }

    LaunchedEffect(moveConfirmation) {
        if (moveConfirmation != null) {
            snackbarHostState.showSnackbar(moveConfirmation)
            onMovedMessageShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TodoListTopBar(
                lists = uiState.lists,
                selectedList = uiState.selectedList,
                actions = topBarActions
            )
        },
        bottomBar = {
            NewTodoBar(
                title = uiState.newTodoTitle,
                onTitleChange = todoActions.onNewTodoTitleChange,
                onAddClick = todoActions.onAddTodoClick
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        val hasLoadError = uiState.error == TodoListError.LOAD_FAILED
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            // Ohne Liste gibt es nichts, wozu man eine Aufgabe hinzufügen könnte — das ist eine
            // andere Aussage als „diese Liste ist leer" und bekommt deshalb einen eigenen Text.
            uiState.selectedList == null && !hasLoadError ->
                NoListState(modifier = Modifier.padding(innerPadding))

            // Ein Ladefehler verdrängt den Leerzustand: „Noch keine Aufgaben" wäre eine Aussage
            // über die Liste, die wir gerade nicht treffen können.
            uiState.todos.isEmpty() && !hasLoadError ->
                EmptyState(modifier = Modifier.padding(innerPadding))

            else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
                if (hasLoadError) {
                    item(key = LOAD_ERROR_KEY) {
                        Text(
                            text = stringResource(R.string.todo_list_error_load_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                items(items = uiState.todos, key = Todo::id) { todo ->
                    SwipeableTodoRow(
                        todo = todo,
                        deleteFailed = uiState.error == TodoListError.DELETE_FAILED,
                        onDoneChange = { isDone -> todoActions.onTodoDoneChange(todo, isDone) },
                        onClick = { todoActions.onEditTodoClick(todo) },
                        onSwipedAway = { todoActions.onTodoSwipedAway(todo) },
                        // Abgehakte Einträge sinken sofort nach unten. Ohne Bewegung sähe das
                        // aus, als wäre die Liste gesprungen — die Animation zeigt, wohin.
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    uiState.newList?.let { newList ->
        NewListDialog(
            newList = newList,
            partner = uiState.partner,
            onNameChange = listDialogActions.onNewListNameChange,
            onSharedChange = listDialogActions.onNewListSharedChange,
            onConfirm = listDialogActions.onNewListConfirm,
            onDismiss = listDialogActions.onNewListDismiss
        )
    }

    uiState.renamedList?.let { renamedList ->
        RenameListDialog(
            name = renamedList.name,
            onNameChange = listDialogActions.onRenamedListNameChange,
            onConfirm = listDialogActions.onRenameListConfirm,
            onDismiss = listDialogActions.onRenameListDismiss
        )
    }

    uiState.listPendingDeletion?.let { list ->
        DeleteListDialog(
            list = list,
            todoCount = uiState.todos.size,
            onConfirm = listDialogActions.onDeleteListConfirm,
            onDismiss = listDialogActions.onDeleteListDismiss
        )
    }

    uiState.editedTodo?.let { editedTodo ->
        EditTodoDialog(
            title = editedTodo.title,
            notes = editedTodo.notes,
            priority = editedTodo.priority,
            lists = uiState.lists,
            targetListId = editedTodo.targetListId,
            actions = editActions
        )
    }
}

/** Der Ladefehler ist kein Todo und braucht daher einen eigenen, kollisionsfreien Item-Key. */
private const val LOAD_ERROR_KEY = "load-error"

private fun TodoListError.messageResId() = when (this) {
    TodoListError.LOAD_FAILED -> R.string.todo_list_error_load_failed
    TodoListError.ADD_FAILED -> R.string.todo_list_error_add_failed
    TodoListError.UPDATE_FAILED -> R.string.todo_list_error_update_failed
    TodoListError.DELETE_FAILED -> R.string.todo_list_error_delete_failed
    TodoListError.MOVE_FAILED -> R.string.todo_list_error_move_failed
    TodoListError.LIST_ADD_FAILED -> R.string.todo_list_error_list_add_failed
    TodoListError.LIST_UPDATE_FAILED -> R.string.todo_list_error_list_update_failed
    TodoListError.LIST_DELETE_FAILED -> R.string.todo_list_error_list_delete_failed
    TodoListError.CALENDAR_APP_MISSING -> R.string.todo_list_error_calendar_app_missing
}

@Composable
private fun NewTodoBar(title: String, onTitleChange: (String) -> Unit, onAddClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // safeDrawing nimmt je Seite das Maximum aus Systemleisten, Tastatur und
                // Display-Aussparung: Tastatur zu ergibt die Navigationsleiste, Tastatur offen
                // die Tastaturhöhe. Das Padding sitzt an der Row und nicht an der Surface, damit
                // deren Hintergrund bis zum Bildschirmrand reicht.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text(text = stringResource(R.string.todo_list_new_todo_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                // Eigene Aktion statt der Vorgabe: die Tastatur bleibt offen, damit mehrere
                // Einträge hintereinander getippt werden können. Leere Titel fängt
                // TodoListViewModel.addTodo() ab.
                keyboardActions = KeyboardActions(onDone = { onAddClick() }),
                modifier = Modifier.weight(1f)
            )
            FilledIconButton(onClick = onAddClick, enabled = title.isNotBlank()) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.todo_list_add)
                )
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.todo_list_loading)

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Der Fortschrittskreis trägt von sich aus keine Beschriftung — ohne die Semantik bliebe
        // der Bildschirm für TalkBack stumm, solange geladen wird.
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) = CenteredMessage(
    headline = stringResource(R.string.todo_list_empty_headline),
    hint = stringResource(R.string.todo_list_empty_hint),
    modifier = modifier
)

@Composable
private fun NoListState(modifier: Modifier = Modifier) = CenteredMessage(
    headline = stringResource(R.string.todo_list_no_list_headline),
    hint = stringResource(R.string.todo_list_no_list_hint),
    modifier = modifier
)

@Composable
private fun CenteredMessage(headline: String, hint: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Die Vorschau rendert ohne Systemleisten und Tastatur — sie prüft Zeilenlayout, Leisten und
// Farben, nicht das Inset-Verhalten. Das muss aufs Gerät.
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TodoListScreenPreview() {
    // Dynamic Color kann der Vorschau-Renderer nicht, so ist sichtbar was auf API 24-30 läuft.
    BrownieDoTheme {
        TodoListScreen(
            uiState = TodoListUiState(
                lists = PREVIEW_LISTS,
                selectedList = PREVIEW_LISTS.first(),
                todos = PREVIEW_TODOS,
                isLoading = false
            ),
            topBarActions = PREVIEW_TOP_BAR_ACTIONS,
            listDialogActions = PREVIEW_LIST_DIALOG_ACTIONS,
            todoActions = PREVIEW_TODO_ACTIONS,
            editActions = PREVIEW_EDIT_ACTIONS,
            onErrorShown = {},
            onMovedMessageShown = {}
        )
    }
}

// Der Leerzustand ist der Bildschirm beim allerersten Start — er verdient eine eigene Vorschau.
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TodoListScreenEmptyPreview() {
    BrownieDoTheme {
        TodoListScreen(
            uiState = TodoListUiState(
                lists = PREVIEW_LISTS,
                selectedList = PREVIEW_LISTS.first(),
                isLoading = false
            ),
            topBarActions = PREVIEW_TOP_BAR_ACTIONS,
            listDialogActions = PREVIEW_LIST_DIALOG_ACTIONS,
            todoActions = PREVIEW_TODO_ACTIONS,
            editActions = PREVIEW_EDIT_ACTIONS,
            onErrorShown = {},
            onMovedMessageShown = {}
        )
    }
}

/*
 * Die Halter für die Vorschauen, einmal für beide. Die Rückrufe tun nichts — eine Vorschau ist ein
 * Bild, sie klickt nichts an. Die Halter selbst tragen bewusst keine Standardwerte, siehe
 * TodoListActions.kt.
 */

private val PREVIEW_TOP_BAR_ACTIONS = TodoListTopBarActions(
    onListSelected = {},
    onNewListClick = {},
    onRenameListClick = {},
    onDeleteListClick = {},
    onSignOutClick = {}
)

private val PREVIEW_LIST_DIALOG_ACTIONS = ListDialogActions(
    onNewListNameChange = {},
    onNewListSharedChange = {},
    onNewListConfirm = {},
    onNewListDismiss = {},
    onRenamedListNameChange = {},
    onRenameListConfirm = {},
    onRenameListDismiss = {},
    onDeleteListConfirm = {},
    onDeleteListDismiss = {}
)

private val PREVIEW_TODO_ACTIONS = TodoActions(
    onNewTodoTitleChange = {},
    onAddTodoClick = {},
    onTodoDoneChange = { _, _ -> },
    onTodoSwipedAway = {},
    onEditTodoClick = {}
)

private val PREVIEW_EDIT_ACTIONS = TodoEditActions(
    onTitleChange = {},
    onNotesChange = {},
    onPriorityChange = {},
    onTargetListChange = {},
    onCalendarEventClick = {},
    onConfirm = {},
    onDelete = {},
    onDismiss = {}
)

private val PREVIEW_TIMESTAMP: Instant = Instant.parse("2026-08-07T20:00:00Z")

private val PREVIEW_LISTS = listOf(
    TodoList(id = "list-shared", name = "Einkauf", isShared = true),
    TodoList(id = "list-private", name = "Meine Erledigungen", isShared = false)
)

// Alle drei Stufen, damit die Vorschau die Markierung und ihr Fehlen zeigt — und genau eine Notiz,
// damit die zweite Zeile neben einer Zeile ohne sie zu sehen ist.
private val PREVIEW_TODOS = listOf(
    Todo(
        id = "todo-1",
        title = "Milch kaufen",
        isDone = false,
        priority = TodoPriority.HIGH,
        createdAt = PREVIEW_TIMESTAMP,
        updatedAt = PREVIEW_TIMESTAMP,
        completedBy = null,
        completedAt = null,
        notes = "Die haltbare, nicht die frische — und zwei Packungen"
    ),
    Todo(
        id = "todo-2",
        title = "Zeitschrift mitbringen",
        isDone = false,
        priority = TodoPriority.MEDIUM,
        createdAt = PREVIEW_TIMESTAMP,
        updatedAt = PREVIEW_TIMESTAMP,
        completedBy = null,
        completedAt = null,
        notes = null
    ),
    Todo(
        id = "todo-3",
        title = "Gläser zum Container",
        isDone = false,
        priority = TodoPriority.LOW,
        createdAt = PREVIEW_TIMESTAMP,
        updatedAt = PREVIEW_TIMESTAMP,
        completedBy = null,
        completedAt = null,
        notes = null
    ),
    Todo(
        id = "todo-4",
        title = "Kaffee kaufen",
        isDone = true,
        priority = TodoPriority.MEDIUM,
        createdAt = PREVIEW_TIMESTAMP,
        updatedAt = PREVIEW_TIMESTAMP,
        completedBy = "uid-1",
        completedAt = PREVIEW_TIMESTAMP,
        notes = null
    )
)
