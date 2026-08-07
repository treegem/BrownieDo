package eu.sweetgeorgie.browniedo.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.domain.todo.Todo

@Composable
fun TodoListScreen(
    uiState: TodoListUiState,
    onNewTodoTitleChange: (String) -> Unit,
    onAddTodoClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.newTodoTitle,
                onValueChange = onNewTodoTitleChange,
                label = { Text(text = stringResource(R.string.todo_list_new_todo_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onAddTodoClick,
                enabled = uiState.newTodoTitle.isNotBlank()
            ) {
                Text(text = stringResource(R.string.todo_list_add))
            }
        }

        uiState.error?.let { error ->
            Text(
                text = stringResource(error.messageResId()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(items = uiState.todos, key = Todo::id) { todo ->
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
                HorizontalDivider()
            }
        }

        TextButton(
            onClick = onSignOutClick,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(text = stringResource(R.string.todo_list_sign_out))
        }
    }
}

private fun TodoListError.messageResId() = when (this) {
    TodoListError.LOAD_FAILED -> R.string.todo_list_error_load_failed
    TodoListError.ADD_FAILED -> R.string.todo_list_error_add_failed
}
