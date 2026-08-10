package eu.sweetgeorgie.browniedo.ui.todo

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.ui.theme.BrownieDoTheme
import java.time.Instant

@Composable
fun TodoListScreen(
    uiState: TodoListUiState,
    onListSelected: (TodoList) -> Unit,
    onNewTodoTitleChange: (String) -> Unit,
    onAddTodoClick: () -> Unit,
    onTodoDoneChange: (Todo, Boolean) -> Unit,
    onTodoSwipedAway: (Todo) -> Unit,
    onEditTodoClick: (Todo) -> Unit,
    onEditedTitleChange: (String) -> Unit,
    onEditConfirm: () -> Unit,
    onDeleteTodoClick: () -> Unit,
    onEditDismiss: () -> Unit,
    onErrorShown: () -> Unit,
    onSignOutClick: () -> Unit,
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TodoListTopBar(
                lists = uiState.lists,
                selectedList = uiState.selectedList,
                onListSelected = onListSelected,
                onSignOutClick = onSignOutClick
            )
        },
        bottomBar = {
            NewTodoBar(
                title = uiState.newTodoTitle,
                onTitleChange = onNewTodoTitleChange,
                onAddClick = onAddTodoClick
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
                        onDoneChange = { isDone -> onTodoDoneChange(todo, isDone) },
                        onClick = { onEditTodoClick(todo) },
                        onSwipedAway = { onTodoSwipedAway(todo) },
                        // Abgehakte Einträge sinken sofort nach unten. Ohne Bewegung sähe das
                        // aus, als wäre die Liste gesprungen — die Animation zeigt, wohin.
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    uiState.editedTodo?.let { editedTodo ->
        EditTodoDialog(
            title = editedTodo.title,
            onTitleChange = onEditedTitleChange,
            onConfirm = onEditConfirm,
            onDelete = onDeleteTodoClick,
            onDismiss = onEditDismiss
        )
    }
}

/** Der Ladefehler ist kein Todo und braucht daher einen eigenen, kollisionsfreien Item-Key. */
private const val LOAD_ERROR_KEY = "load-error"

/**
 * Anteil der Zeilenbreite, über den eine erledigte Aufgabe gezogen werden muss, damit sie gelöscht
 * wird. Bewusst weit über dem Material-Standard von 50 %: Gelöscht ist endgültig, und ein Streifen
 * im Vorbeiscrollen soll nichts auslösen.
 */
private const val DELETE_SWIPE_FRACTION = 0.85f

private fun TodoListError.messageResId() = when (this) {
    TodoListError.LOAD_FAILED -> R.string.todo_list_error_load_failed
    TodoListError.ADD_FAILED -> R.string.todo_list_error_add_failed
    TodoListError.UPDATE_FAILED -> R.string.todo_list_error_update_failed
    TodoListError.DELETE_FAILED -> R.string.todo_list_error_delete_failed
}

// TopAppBar selbst ist stabil, aber seine Vorgabewerte stammen aus der noch experimentellen
// TopAppBarDefaults-API.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoListTopBar(
    lists: List<TodoList>,
    selectedList: TodoList?,
    onListSelected: (TodoList) -> Unit,
    onSignOutClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var listMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            // Der Titel ist die Listen-Auswahl: Unten rechts ist durch die Eingabeleiste belegt,
            // Primäraktionen gehören deshalb in die TopAppBar — siehe ADR 0013.
            Box {
                Row(
                    modifier = Modifier.clickable(
                        enabled = lists.isNotEmpty(),
                        onClick = { listMenuExpanded = true }
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = selectedList?.name ?: stringResource(R.string.app_name))
                    if (lists.isNotEmpty()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_drop_down),
                            contentDescription = stringResource(R.string.todo_list_choose_list)
                        )
                    }
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
                                onListSelected(list)
                            }
                        )
                    }
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
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.todo_list_sign_out)) },
                    onClick = {
                        menuExpanded = false
                        onSignOutClick()
                    }
                )
            }
        }
    )
}

@Composable
private fun ListMenuItem(list: TodoList, isSelected: Boolean, onClick: () -> Unit) {
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

/**
 * Umschließt eine Zeile mit der Wischgeste. Gelöscht wird nur, was schon erledigt ist, und nur
 * nach rechts — siehe docs/decisions/0016-wischen-loescht-nur-erledigte-aufgaben.md.
 */
@Composable
private fun SwipeableTodoRow(
    todo: Todo,
    deleteFailed: Boolean,
    onDoneChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onSwipedAway: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swipeState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * DELETE_SWIPE_FRACTION }
    )

    // Schlägt das Löschen fehl, bleibt der Eintrag in der Liste — dann muss die weggewischte
    // Zeile zurück an ihren Platz, sonst klafft dort eine leere Fläche.
    LaunchedEffect(deleteFailed) {
        if (deleteFailed && swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
            swipeState.reset()
        }
    }

    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = { DeleteBackground() },
        modifier = modifier,
        // Nach links wird nie gelöscht, und offene Aufgaben lassen sich gar nicht erst bewegen:
        // Was sich ziehen lässt, ist erledigt.
        enableDismissFromEndToStart = false,
        gesturesEnabled = todo.isDone,
        onDismiss = { direction ->
            if (direction == SwipeToDismissBoxValue.StartToEnd) onSwipedAway()
        }
    ) {
        TodoRow(todo = todo, onDoneChange = onDoneChange, onClick = onClick)
    }
}

@Composable
private fun DeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_delete),
            // Rein dekorativ: Der Hintergrund taucht nur während einer Geste auf, die sich mit
            // TalkBack ohnehin nicht ausführen lässt. Dort führt der Bearbeiten-Dialog zum Löschen.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
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

@Composable
private fun TodoRow(
    todo: Todo,
    onDoneChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                text = todo.title,
                textDecoration = if (todo.isDone) TextDecoration.LineThrough else null
            )
        },
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = { Checkbox(checked = todo.isDone, onCheckedChange = onDoneChange) },
        // Die Farbe gehört an die Slot-Dekoration von ListItem, nicht an den inneren Text —
        // ListItem setzt die Textfarbe selbst und würde eine Farbe am Text überschreiben.
        colors = ListItemDefaults.colors(
            headlineColor = if (todo.isDone) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                Color.Unspecified
            }
        )
    )
}

@Composable
private fun EditTodoDialog(
    title: String,
    onTitleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.todo_list_edit_headline)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text(text = stringResource(R.string.todo_list_title_label)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = title.isNotBlank()) {
                Text(text = stringResource(R.string.todo_list_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.todo_list_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.todo_list_cancel))
                }
            }
        }
    )
}

// Die Vorschau rendert ohne Systemleisten und Tastatur — sie prüft Zeilenlayout, Leisten und
// Farben, nicht das Inset-Verhalten. Das muss aufs Gerät.
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TodoListScreenPreview() {
    // Dynamic Color kann der Vorschau-Renderer nicht, so ist sichtbar was auf API 24-30 läuft.
    BrownieDoTheme(dynamicColor = false) {
        TodoListScreen(
            uiState = TodoListUiState(
                lists = PREVIEW_LISTS,
                selectedList = PREVIEW_LISTS.first(),
                todos = PREVIEW_TODOS,
                isLoading = false
            ),
            onListSelected = {},
            onNewTodoTitleChange = {},
            onAddTodoClick = {},
            onTodoDoneChange = { _, _ -> },
            onTodoSwipedAway = {},
            onEditTodoClick = {},
            onEditedTitleChange = {},
            onEditConfirm = {},
            onDeleteTodoClick = {},
            onEditDismiss = {},
            onErrorShown = {},
            onSignOutClick = {}
        )
    }
}

// Der Leerzustand ist der Bildschirm beim allerersten Start — er verdient eine eigene Vorschau.
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TodoListScreenEmptyPreview() {
    BrownieDoTheme(dynamicColor = false) {
        TodoListScreen(
            uiState = TodoListUiState(
                lists = PREVIEW_LISTS,
                selectedList = PREVIEW_LISTS.first(),
                isLoading = false
            ),
            onListSelected = {},
            onNewTodoTitleChange = {},
            onAddTodoClick = {},
            onTodoDoneChange = { _, _ -> },
            onTodoSwipedAway = {},
            onEditTodoClick = {},
            onEditedTitleChange = {},
            onEditConfirm = {},
            onDeleteTodoClick = {},
            onEditDismiss = {},
            onErrorShown = {},
            onSignOutClick = {}
        )
    }
}

private val PREVIEW_TIMESTAMP: Instant = Instant.parse("2026-08-07T20:00:00Z")

private val PREVIEW_LISTS = listOf(
    TodoList(id = "list-shared", name = "Einkauf", isShared = true),
    TodoList(id = "list-private", name = "Meine Erledigungen", isShared = false)
)

private val PREVIEW_TODOS = listOf(
    Todo(
        id = "todo-1",
        title = "Milch kaufen",
        isDone = false,
        createdAt = PREVIEW_TIMESTAMP,
        updatedAt = PREVIEW_TIMESTAMP,
        completedBy = null
    ),
    Todo(
        id = "todo-2",
        title = "Kaffee kaufen",
        isDone = true,
        createdAt = PREVIEW_TIMESTAMP,
        updatedAt = PREVIEW_TIMESTAMP,
        completedBy = "uid-1"
    )
)
