package eu.sweetgeorgie.browniedo.ui.todo

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Der Aufgaben-Bildschirm. Die Rückrufe kommen gebündelt statt einzeln, siehe
 * docs/decisions/0028-rueckrufe-in-actions-haltern.md — vorher waren es 27 Parameter, und jedes neue
 * Feld am Bearbeiten-Dialog kostete drei Stellen.
 *
 * Die Rückrufe des [SnackbarHostState] stehen in einem eigenen Halter [SnackbarActions] — sie
 * gehören diesem Scaffold und keinem der vier sichtbaren Bereiche.
 *
 * [onCreateListFromTemplateClick] steht dagegen einzeln, denn der schwebende Knopf gehört wie der
 * `SnackbarHost` dem Scaffold selbst und ist genau ein Rückruf. Das ist derselbe Weg, den
 * `onErrorShown` und `onMovedMessageShown` genommen haben: einzeln, solange es wenige sind, und in
 * einen Halter, sobald es vier werden (ADR 0028).
 */
@Composable
fun TodoListScreen(
    uiState: TodoListUiState,
    topBarActions: TodoListTopBarActions,
    listDialogActions: ListDialogActions,
    todoActions: TodoActions,
    editActions: TodoEditActions,
    snackbarActions: SnackbarActions,
    onCreateListFromTemplateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()

    /*
     * Die Reihenfolge, solange ein Finger auf der Zeile liegt. Bildschirmlokal und **nicht** im
     * UiState: Das ist ein Finger auf dem Glas, keine Absicht — im UiState erbte es jede
     * Aufräumpflicht, die dort schon dokumentiert ist (Listenwechsel, Fehler, Rückgängig), und
     * müsste zusätzlich gegen jeden Schnappschuss abgeglichen werden. Auf die Listen-id gekeyt
     * erledigt sich der einzige Fall, der wirklich zählt, von selbst.
     *
     * Ohne diesen Zustand bewegte sich beim Ziehen gar nichts: Die Zeile wandert nur, weil sich die
     * gezeichnete Liste ändert.
     */
    var provisionalTodos by remember(uiState.selectedList?.id) {
        mutableStateOf<List<Todo>?>(null)
    }
    val todos = provisionalTodos ?: uiState.todos

    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val current = provisionalTodos ?: uiState.todos
        val dragged = current.firstOrNull { it.id == from.key }
        val target = current.firstOrNull { it.id == to.key }
        // Über den `key` und nicht über `from.index`: Die LazyColumn trägt zwei Einträge, die keine
        // Aufgabe sind (der Ladefehler und der Platz unter dem schwebenden Knopf) — beide lösen zu
        // keinem Todo auf und werden damit von derselben Prüfung abgewiesen, die die Gruppengrenze
        // durchsetzt.
        //
        // Die Grenze wird **laufend verweigert und nicht geklemmt**: Die Zeile folgt dem Finger
        // weiter (eine Zeile, die stehen bleibt, liest sich wie eine abgerissene Geste), nur der
        // Tausch unterbleibt. Frei ziehen und erst beim Ablegen verwerfen wäre das Schlechteste —
        // die ganze Liste ordnete sich sichtbar um und schnappte dann zurück.
        if (dragged != null && target != null && target.sortsBeside(dragged)) {
            provisionalTodos = current.toMutableList().apply {
                add(indexOf(target), removeAt(indexOf(dragged)))
            }
        }
    }

    /*
     * Die vorläufige Reihenfolge bleibt nach dem Ablegen stehen, bis der Firestore-Schnappschuss
     * dieselbe liefert — sonst hüpfte die Zeile zurück und sofort wieder vor. Firestore wendet den
     * Schreibvorgang lokal an, das ist meist derselbe oder der nächste Frame.
     *
     * Die Notbremse ist kein Beiwerk: Wird der Schreibvorgang abgelehnt, käme der passende
     * Schnappschuss nie, und der Bildschirm bliebe für immer in einer Lüge stehen. Und solange
     * gezogen wird, läuft überhaupt nichts davon — sonst räumte die Bremse mitten in einer langen
     * Geste auf.
     */
    val provisionalIds = provisionalTodos?.map(Todo::id)

    LaunchedEffect(provisionalIds, uiState.todos, reorderState.isAnyItemDragging) {
        if (provisionalIds == null || reorderState.isAnyItemDragging) return@LaunchedEffect
        if (uiState.todos.map(Todo::id) != provisionalIds) delay(PROVISIONAL_ORDER_TIMEOUT)
        provisionalTodos = null
    }

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
            snackbarActions.onErrorShown()
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
            snackbarActions.onMovedMessageShown()
        }
    }

    /*
     * Der dritte Kanal: gelöscht mit Rückgängig (ADR 0031). Er weicht in zwei Punkten von den beiden
     * darüber ab, und beide Male mit Grund.
     *
     * Gekeyt wird auf die **Aufgabe**, nicht auf die aufgelöste Meldung: Der Text ist für jede
     * Löschung derselbe, ein Meldungs-Key änderte sich also nicht und die zweite Löschung in Folge
     * bekäme keine Snackbar. Die Aufgabe unterscheidet sich immer — und trüge sie zufällig gleiche
     * Werte, liegt zwischen beiden Löschungen ohnehin ein null.
     *
     * Und der Rückgabewert wird ausgewertet: Ein Tipp auf „Rückgängig" ist ActionPerformed, alles
     * andere heißt, das Angebot ist abgelaufen. Genau einer der beiden Rückrufe läuft.
     */
    val deletedMessage = stringResource(R.string.todo_list_deleted)
    val undoLabel = stringResource(R.string.todo_list_undo)

    LaunchedEffect(uiState.deletedTodo) {
        if (uiState.deletedTodo != null) {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                // Länger als die Vorgabe: Vier Sekunden sind knapp, um ein Versehen zu bemerken,
                // die Snackbar zu lesen und zu tippen — und das Rückgängig ist der ganze Zweck.
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                snackbarActions.onUndoDelete()
            } else {
                snackbarActions.onDeletedMessageShown()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TodoListTopBar(
                lists = uiState.lists,
                templates = uiState.templates,
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            // Eine Liste aus der Vorlage zu erzeugen ist der Zweck einer Vorlage — der Knopf steht
            // deshalb im Bildschirm statt im Überlauf-Menü, siehe ADR 0038. In einer Arbeitsliste
            // gibt es nichts zu instanziieren, dann bleibt der Slot leer.
            if (uiState.isTemplateOpen) {
                val label = stringResource(R.string.todo_list_create_list_from_template)

                ExtendedFloatingActionButton(
                    onClick = onCreateListFromTemplateClick,
                    // Die Beschriftung steht sichtbar daneben, landet aber **nicht** im
                    // Semantik-Knoten: Der Extended FAB faltet seinen Text-Slot nicht in den
                    // zusammengefassten Knoten (nachgesehen im Semantik-Baum auf dem Gerät — dort
                    // trägt er nur `Role = Button`). Ohne diese Zeile wäre er für TalkBack ein
                    // Knopf ohne Namen.
                    modifier = Modifier.semantics { contentDescription = label },
                    // Dieselben Farben wie der Hinzufügen-Knopf der Eingabeleiste
                    // (primary/onPrimary) statt der FAB-Vorgabe primaryContainer/onPrimaryContainer:
                    // Der Knopf soll wie der eine andere gefüllte Knopf des Bildschirms aussehen,
                    // und dieses Paar sichert ColorSchemeContrastTest bereits ab.
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = {
                        // Ohne contentDescription: die Beschriftung daneben sagt dasselbe, und
                        // TalkBack läse sie sonst zweimal vor.
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null
                        )
                    },
                    text = { Text(text = label) }
                )
            }
        }
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
            uiState.todos.isEmpty() && !hasLoadError -> EmptyState(
                isTemplate = uiState.isTemplateOpen,
                modifier = Modifier.padding(innerPadding)
            )

            else -> LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding
            ) {
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
                itemsIndexed(items = todos, key = { _, todo -> todo.id }) { index, todo ->
                    val moveUp = todos.moveUpNeighbours(index)
                    val moveDown = todos.moveDownNeighbours(index)

                    ReorderableItem(
                        state = reorderState,
                        key = todo.id,
                        // Erledigte Aufgaben lassen sich nicht anheben: Ihr Block ist ein Protokoll
                        // nach Erledigungszeitpunkt (ADR 0039).
                        enabled = !todo.isDone,
                        // **Das getunte Ausblenden muss hierher.** ReorderableItem legt selbst ein
                        // `animateItem()` an; bliebe die Angabe unten an der Zeile stehen, hingen
                        // zwei davon am selben Eintrag und die Lösch-Animation aus ADR 0031 wäre
                        // still wieder die Vorgabe — ohne Compilerfehler und ohne roten Test.
                        //
                        // Abgehakte Einträge sinken sofort nach unten. Ohne Bewegung sähe das aus,
                        // als wäre die Liste gesprungen — die Animation zeigt, wohin. Nur das
                        // Ausblenden bekommt eine eigene Dauer, Einblenden und Verschieben behalten
                        // ihre Vorgabe-Federn: Die Vorgabe fürs Ausblenden ist so kurz, dass eine
                        // gelöschte Aufgabe schlicht weg ist — und weil das Löschen umkehrbar ist,
                        // soll man sehen, *dass* etwas verschwindet, um nach dem „Rückgängig" zu
                        // greifen.
                        animateItemModifier = Modifier.animateItem(
                            fadeOutSpec = tween(
                                durationMillis = REMOVAL_FADE_MILLIS,
                                easing = REMOVAL_FADE_EASING
                            )
                        )
                    ) {
                        SwipeableTodoRow(
                            todo = todo,
                            deleteFailed = uiState.error == TodoListError.DELETE_FAILED,
                            isTemplateEntry = uiState.isTemplateOpen,
                            onDoneChange = { isDone -> todoActions.onTodoDoneChange(todo, isDone) },
                            onClick = { todoActions.onEditTodoClick(todo) },
                            onSwipedAway = { todoActions.onTodoSwipedAway(todo) },
                            onMoveUp = moveUp?.let { (above, below) ->
                                { todoActions.onTodoReordered(todo, above, below) }
                            },
                            onMoveDown = moveDown?.let { (above, below) ->
                                { todoActions.onTodoReordered(todo, above, below) }
                            },
                            modifier = Modifier.longPressDraggableHandle(
                                enabled = !todo.isDone,
                                // Die Geste ist unauffällig — das Anheben muss man spüren, sonst
                                // findet sie niemand. Dieselbe Abwägung wie beim Wischen (ADR 0016).
                                onDragStarted = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    provisionalTodos?.let { dropped ->
                                        todoActions.onTodoReordered(
                                            todo,
                                            dropped.neighbourAbove(todo),
                                            dropped.neighbourBelow(todo)
                                        )
                                    }
                                }
                            )
                        )
                    }
                }
                // Der schwebende Knopf liegt über der Liste und wüsste sonst niemand etwas davon:
                // Ohne diesen Platz am Ende läge die letzte Zeile darunter und wäre nicht
                // antippbar. Ein Element statt eines aufgeschlagenen contentPadding, damit die
                // seitlichen Insets aus innerPadding unangetastet bleiben.
                if (uiState.isTemplateOpen) {
                    item(key = FAB_SPACE_KEY) {
                        Spacer(modifier = Modifier.height(FAB_CONTENT_SPACE))
                    }
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
            onFactorChange = listDialogActions.onNewListFactorChange,
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
            quantity = editedTodo.quantity,
            priority = editedTodo.priority,
            // Nur Gleichartiges als Ziel — die Regel steht im UiState, nicht hier.
            lists = uiState.targetLists,
            targetListId = editedTodo.targetListId,
            isTemplateEntry = uiState.isTemplateOpen,
            actions = editActions
        )
    }
}

/*
 * Die Nachbarn für einen Zug um genau eine Position, als Paar (darüber, darunter) — und zwar so, wie
 * sie **nach** dem Zug dastehen, denn genau das erwartet `sortOrderBetween`. Null heißt: In diese
 * Richtung geht nichts, die Aktion wird gar nicht erst angeboten.
 *
 * Ein Nachbar zählt nur, wenn er offen ist und dieselbe Priorität trägt — damit endet der Zug
 * automatisch an der Gruppengrenze, ohne dass irgendwo Indexbereiche ausgerechnet werden müssten.
 * Eine erledigte Aufgabe lässt sich gar nicht erst bewegen: Ihr Block ist ein Protokoll nach
 * Erledigungszeitpunkt (ADR 0039).
 */

private fun List<Todo>.moveUpNeighbours(index: Int): Pair<Todo?, Todo?>? {
    val todo = getOrNull(index)?.takeIf { !it.isDone } ?: return null
    // Die Aufgabe rückt vor ihren bisherigen Vorgänger — der wird damit zu ihrem Nachbarn darunter.
    val below = getOrNull(index - 1)?.takeIf { it.sortsBeside(todo) } ?: return null
    return getOrNull(index - 2)?.takeIf { it.sortsBeside(todo) } to below
}

private fun List<Todo>.moveDownNeighbours(index: Int): Pair<Todo?, Todo?>? {
    val todo = getOrNull(index)?.takeIf { !it.isDone } ?: return null
    val above = getOrNull(index + 1)?.takeIf { it.sortsBeside(todo) } ?: return null
    return above to getOrNull(index + 2)?.takeIf { it.sortsBeside(todo) }
}

private fun Todo.sortsBeside(other: Todo): Boolean = !isDone && priority == other.priority

/*
 * Die Nachbarn nach dem Ablegen — dieselbe Regel wie oben, nur aus der bereits umsortierten Liste
 * gelesen statt um eine Position gerechnet.
 */

private fun List<Todo>.neighbourAbove(todo: Todo): Todo? =
    getOrNull(indexOfFirst { it.id == todo.id } - 1)?.takeIf { it.sortsBeside(todo) }

private fun List<Todo>.neighbourBelow(todo: Todo): Todo? =
    getOrNull(indexOfFirst { it.id == todo.id } + 1)?.takeIf { it.sortsBeside(todo) }

/**
 * Wie lange die vorläufige Reihenfolge einem Schnappschuss Zeit gibt, der nie kommt. Nur der
 * Fehlerfall landet hier — im Normalfall räumt der passende Schnappschuss sofort auf.
 */
private val PROVISIONAL_ORDER_TIMEOUT = 1.seconds

/** Der Ladefehler ist kein Todo und braucht daher einen eigenen, kollisionsfreien Item-Key. */
private const val LOAD_ERROR_KEY = "load-error"

/** Aus demselben Grund wie [LOAD_ERROR_KEY]: der Platzhalter unter dem schwebenden Knopf. */
private const val FAB_SPACE_KEY = "fab-space"

/**
 * Höhe des Platzhalters am Listenende: 56 dp für den schwebenden Knopf, dazu die 16 dp, die das
 * Scaffold ohnehin um ihn legt, und noch einmal so viel als Rand.
 */
private val FAB_CONTENT_SPACE = 88.dp

/**
 * Dauer, in der eine verschwindende Zeile ausblendet. Lang genug, dass die Bewegung auffällt, und
 * kurz genug, dass sie beim Aufräumen mehrerer Einträge nicht bremst.
 *
 * Die verblassende Zeile belegt dabei keinen Platz mehr — die Einträge darunter rücken schon auf,
 * während sie noch zu sehen ist.
 */
private const val REMOVAL_FADE_MILLIS = 800

/**
 * **Linear und nicht die Vorgabe `FastOutSlowInEasing`.** Die beschleunigt am Anfang: Nach einem
 * Fünftel der Zeit stünde die Deckkraft schon bei etwa der Hälfte, und der Rest der Dauer verstreicht
 * an einer praktisch unsichtbaren Zeile. Genau das ließ die erste Fassung „kaum sichtbar" aussehen,
 * obwohl die Dauer stimmte. Bei einer Deckkraft, die gleichmäßig fällt, sagt die Zahl oben, wie lange
 * man wirklich etwas sieht.
 */
private val REMOVAL_FADE_EASING = LinearEasing

private fun TodoListError.messageResId() = when (this) {
    TodoListError.LOAD_FAILED -> R.string.todo_list_error_load_failed
    TodoListError.ADD_FAILED -> R.string.todo_list_error_add_failed
    TodoListError.UPDATE_FAILED -> R.string.todo_list_error_update_failed
    TodoListError.DELETE_FAILED -> R.string.todo_list_error_delete_failed
    TodoListError.RESTORE_FAILED -> R.string.todo_list_error_restore_failed
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

/**
 * Die Überschrift ist für beide dieselbe — in einer Vorlage stehen **Aufgaben** wie überall sonst,
 * und ein zweites Wort dafür wäre genau das Synonym, das `consistent-domain-terminology` verbietet.
 * Nur der Hinweis darunter wechselt: In einer Vorlage tippt man nicht ein, was zu tun ist, sondern
 * was jedes Mal dazugehört (ADR 0034).
 */
@Composable
private fun EmptyState(isTemplate: Boolean, modifier: Modifier = Modifier) = CenteredMessage(
    headline = stringResource(R.string.todo_list_empty_headline),
    hint = stringResource(
        if (isTemplate) R.string.todo_list_empty_template_hint else R.string.todo_list_empty_hint
    ),
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
            snackbarActions = PREVIEW_SNACKBAR_ACTIONS,
            onCreateListFromTemplateClick = {}
        )
    }
}

// Eine offene Vorlage: Markierung im Titel, keine Checkboxen, sonst derselbe Bildschirm (ADR 0034).
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TodoListScreenTemplatePreview() {
    BrownieDoTheme {
        TodoListScreen(
            uiState = TodoListUiState(
                lists = PREVIEW_LISTS,
                templates = PREVIEW_TEMPLATES,
                selectedList = PREVIEW_TEMPLATES.first(),
                todos = PREVIEW_TODOS.filterNot(Todo::isDone),
                isLoading = false
            ),
            topBarActions = PREVIEW_TOP_BAR_ACTIONS,
            listDialogActions = PREVIEW_LIST_DIALOG_ACTIONS,
            todoActions = PREVIEW_TODO_ACTIONS,
            editActions = PREVIEW_EDIT_ACTIONS,
            snackbarActions = PREVIEW_SNACKBAR_ACTIONS,
            onCreateListFromTemplateClick = {}
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
            snackbarActions = PREVIEW_SNACKBAR_ACTIONS,
            onCreateListFromTemplateClick = {}
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
    onNewTemplateClick = {},
    onRenameListClick = {},
    onDeleteListClick = {},
    onSignOutClick = {}
)

private val PREVIEW_LIST_DIALOG_ACTIONS = ListDialogActions(
    onNewListNameChange = {},
    onNewListSharedChange = {},
    onNewListFactorChange = {},
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
    onEditTodoClick = {},
    onTodoReordered = { _, _, _ -> }
)

private val PREVIEW_SNACKBAR_ACTIONS = SnackbarActions(
    onErrorShown = {},
    onMovedMessageShown = {},
    onUndoDelete = {},
    onDeletedMessageShown = {}
)

private val PREVIEW_EDIT_ACTIONS = TodoEditActions(
    onTitleChange = {},
    onNotesChange = {},
    onQuantityChange = {},
    onPriorityChange = {},
    onTargetListChange = {},
    onCalendarEventClick = {},
    onConfirm = {},
    onDelete = {},
    onDismiss = {}
)

private val PREVIEW_TIMESTAMP: Instant = Instant.parse("2026-08-07T20:00:00Z")

private val PREVIEW_LISTS = listOf(
    TodoList(id = "list-shared", name = "Einkauf", isShared = true, isTemplate = false),
    TodoList(id = "list-private", name = "Meine Erledigungen", isShared = false, isTemplate = false)
)

private val PREVIEW_TEMPLATES = listOf(
    TodoList(id = "template-trip", name = "Urlaub packen", isShared = true, isTemplate = true)
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
        notes = "Die haltbare, nicht die frische — und zwei Packungen",
        quantity = null,
        sortOrder = null
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
        notes = null,
        quantity = null,
        sortOrder = null
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
        notes = null,
        quantity = null,
        sortOrder = null
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
        notes = null,
        quantity = null,
        sortOrder = null
    )
)
