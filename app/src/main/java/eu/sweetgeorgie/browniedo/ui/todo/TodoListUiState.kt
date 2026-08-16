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

    /** Das „Rückgängig" nach dem Löschen ist gescheitert — die Aufgabe bleibt weg (ADR 0031). */
    RESTORE_FAILED,
    MOVE_FAILED,
    LIST_ADD_FAILED,
    LIST_UPDATE_FAILED,
    LIST_DELETE_FAILED,

    /** Kein Schreibfehler: Der Kalender-Intent fand keine App, siehe ADR 0027 und ADR 0029. */
    CALENDAR_APP_MISSING
}

data class TodoListUiState(
    /** Die Arbeitslisten — **ohne** die Vorlagen, die stehen in [templates]. */
    val lists: List<TodoList> = emptyList(),
    /**
     * Die Vorlagen, nach demselben Namen sortiert wie [lists]. Getrennt gehalten statt beim Ablesen
     * gefiltert: Beide Abschnitte der Auswahl lesen sie, und die Aufteilung ist eine Aussage über
     * die Daten, keine über die Darstellung.
     */
    val templates: List<TodoList> = emptyList(),
    /**
     * Die geöffnete Liste — eine aus [lists] oder aus [templates]. Null, solange die Listen laden
     * oder der Nutzer zu keiner gehört.
     */
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
    val movedToListName: String? = null,
    /**
     * Die zuletzt gelöschte Aufgabe, solange ihr „Rückgängig" noch angeboten wird — null, sobald die
     * Snackbar weg ist oder wiederhergestellt wurde. Siehe
     * docs/decisions/0031-rueckgaengig-statt-rueckfrage-beim-loeschen.md.
     *
     * Der dritte Meldungskanal neben [error] und [movedToListName], und der einzige, der eine Last
     * trägt: Die ganze Aufgabe steht hier, weil das Wiederherstellen sie vollständig braucht — aus
     * Firestore ist sie nach dem Löschen nicht mehr zu holen.
     *
     * **Ein Einzelslot, und das ist eine Entscheidung:** Löscht man zwei Aufgaben schnell
     * hintereinander, lässt sich nur die zweite zurückholen. Eine Warteschlange wäre die
     * vollständige Antwort, aber „Rückgängig" bezieht sich in jeder App auf den letzten Schritt —
     * mehrere gleichzeitig offene Angebote wären eher verwirrend als hilfreich.
     */
    val deletedTodo: Todo? = null
) {
    /** Ob gerade eine Vorlage offen ist. Der Bildschirm verhält sich dann in drei Punkten anders. */
    val isTemplateOpen: Boolean get() = selectedList?.isTemplate == true

    /**
     * Wohin eine Aufgabe von hier aus verschoben werden kann: Gleiches zu Gleichem. Eine Aufgabe in
     * eine Vorlage zu schieben wäre kein Ablegen, sondern ein Verlust aus der Arbeitsliste — und
     * umgekehrt genauso, siehe docs/decisions/0034-vorlagen-sind-listen-mit-einem-flag.md.
     */
    val targetLists: List<TodoList> get() = if (isTemplateOpen) templates else lists
}

/**
 * The entry currently open in the edit dialog, together with what has been typed and picked so far.
 *
 * [priority] is never absent — the dialog opens seeded from the entry's current level.
 *
 * [targetListId] ist die Liste, in der die Aufgabe nach dem Speichern liegen soll. Sie startet auf
 * der Liste, in der die Aufgabe gerade steht: „Speichern" schreibt dann an Ort und Stelle, und erst
 * eine andere Wahl macht daraus ein Verschieben, siehe
 * docs/decisions/0022-verschieben-im-bearbeiten-dialog.md.
 *
 * [notes] ist **nicht** nullable, anders als [Todo.notes]: Das hier ist der Puffer des Textfelds,
 * und ein Textfeld braucht einen String. Leer heißt „keine Notiz"; die Übersetzung nach null
 * passiert beim Speichern, an einer Stelle.
 */
data class TodoEdit(
    val todoId: String,
    val title: String,
    val priority: TodoPriority,
    val targetListId: String,
    val notes: String
)

/**
 * The list being created, together with what has been typed and picked so far.
 *
 * Trägt alle drei Wege, auf denen ein Dokument in `lists` entsteht — [kind] sagt welcher. Ein
 * gemeinsamer Zustand und ein gemeinsamer Dialog, weil alle drei dieselben zwei Eingaben haben:
 * Name und geteilt/privat. Auch eine Vorlage ist eine Liste, siehe
 * docs/decisions/0034-vorlagen-sind-listen-mit-einem-flag.md.
 */
data class NewList(
    val name: String = "",
    val shared: Boolean = false,
    val kind: NewListKind = NewListKind.LIST
)

/**
 * Was gerade angelegt wird. Entscheidet über Überschrift und Beschriftung des Dialogs — und darüber,
 * was beim Bestätigen geschrieben wird.
 *
 * [FROM_TEMPLATE] braucht keine Quell-id: Angeboten wird der Weg nur, während die Vorlage offen ist,
 * und ein Listenwechsel schließt jeden offenen Dialog. Die Einträge kommen deshalb aus dem
 * angezeigten Stand, genau wie beim Verschieben.
 */
enum class NewListKind { LIST, TEMPLATE, FROM_TEMPLATE }

/** The list being renamed. [listId] is kept so the write cannot drift to another list. */
data class RenamedList(
    val listId: String,
    val name: String
)
