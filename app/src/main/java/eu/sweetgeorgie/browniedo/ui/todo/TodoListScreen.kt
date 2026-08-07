package eu.sweetgeorgie.browniedo.ui.todo

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.ui.theme.BrownieDoTheme
import java.time.Instant

@Composable
fun TodoListScreen(
    uiState: TodoListUiState,
    onNewTodoTitleChange: (String) -> Unit,
    onAddTodoClick: () -> Unit,
    onTodoDoneChange: (Todo, Boolean) -> Unit,
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
        topBar = { TodoListTopBar(onSignOutClick = onSignOutClick) },
        bottomBar = {
            NewTodoBar(
                title = uiState.newTodoTitle,
                onTitleChange = onNewTodoTitleChange,
                onAddClick = onAddTodoClick
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
            if (uiState.error == TodoListError.LOAD_FAILED) {
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
                TodoRow(
                    todo = todo,
                    onDoneChange = { isDone -> onTodoDoneChange(todo, isDone) },
                    onClick = { onEditTodoClick(todo) }
                )
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
private fun TodoListTopBar(onSignOutClick: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(text = stringResource(R.string.app_name)) },
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
private fun TodoRow(todo: Todo, onDoneChange: (Boolean) -> Unit, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = todo.title,
                textDecoration = if (todo.isDone) TextDecoration.LineThrough else null
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
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
            uiState = TodoListUiState(todos = PREVIEW_TODOS, isLoading = false),
            onNewTodoTitleChange = {},
            onAddTodoClick = {},
            onTodoDoneChange = { _, _ -> },
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
