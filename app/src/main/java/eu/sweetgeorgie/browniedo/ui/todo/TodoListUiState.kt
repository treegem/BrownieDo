package eu.sweetgeorgie.browniedo.ui.todo

import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import eu.sweetgeorgie.browniedo.domain.user.Partner

enum class TodoListError {
    LOAD_FAILED,
    ADD_FAILED,
    UPDATE_FAILED,
    DELETE_FAILED,
    MOVE_FAILED,
    LIST_ADD_FAILED,
    LIST_UPDATE_FAILED,
    LIST_DELETE_FAILED,

    /** Kein Schreibfehler: Der Kalender-Intent fand keine App, siehe ADR 0027 und ADR 0029. */
    CALENDAR_APP_MISSING
}

data class TodoListUiState(
    val lists: List<TodoList> = emptyList(),
    /** Null while the lists are still loading, or when the user belongs to none at all. */
    val selectedList: TodoList? = null,
    /** Null when nobody is on file in `users`; only then a shared list cannot be offered. */
    val partner: Partner? = null,
    val todos: List<Todo> = emptyList(),
    val isLoading: Boolean = true,
    val newTodoTitle: String = "",
    val editedTodo: TodoEdit? = null,
    val newList: NewList? = null,
    val renamedList: RenamedList? = null,
    val listPendingDeletion: TodoList? = null,
    val error: TodoListError? = null,
    /**
     * Name der Liste, in die zuletzt verschoben wurde — null, sobald die Bestätigung gezeigt wurde.
     *
     * Ein zweiter Kanal neben [error] und nicht in ihm: Verschieben ist die einzige Aktion, deren
     * Ergebnis in der Liste nicht zu sehen ist. Der Eintrag verschwindet einfach und sähe damit
     * genau wie ein Löschen aus. Der Name statt der Liste, weil die Meldung nichts weiter braucht —
     * und statt einer Ressourcen-id, weil das ViewModel keine Android-Ressourcen kennt.
     */
    val movedToListName: String? = null
)

/**
 * The entry currently open in the edit dialog, together with what has been typed and picked so far.
 *
 * [priority] is never absent — the dialog opens seeded from the entry's current level.
 *
 * [targetListId] ist die Liste, in der die Aufgabe nach dem Speichern liegen soll. Sie startet auf
 * der Liste, in der die Aufgabe gerade steht: „Speichern" schreibt dann an Ort und Stelle, und erst
 * eine andere Wahl macht daraus ein Verschieben, siehe
 * docs/decisions/0022-verschieben-im-bearbeiten-dialog.md.
 */
data class TodoEdit(
    val todoId: String,
    val title: String,
    val priority: TodoPriority,
    val targetListId: String
)

/** The list being created, together with what has been typed and picked so far. */
data class NewList(
    val name: String = "",
    val shared: Boolean = false
)

/** The list being renamed. [listId] is kept so the write cannot drift to another list. */
data class RenamedList(
    val listId: String,
    val name: String
)
