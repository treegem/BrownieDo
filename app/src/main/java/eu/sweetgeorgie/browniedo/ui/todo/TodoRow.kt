package eu.sweetgeorgie.browniedo.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import eu.sweetgeorgie.browniedo.domain.todo.formatQuantity

/**
 * Anteil der Zeilenbreite, über den eine erledigte Aufgabe gezogen werden muss, damit sie gelöscht
 * wird. Bewusst hoch: Ein Streifen im Vorbeiscrollen soll nichts auslösen, siehe
 * docs/decisions/0016-wischen-loescht-nur-erledigte-aufgaben.md. Seit ADR 0031 fängt zusätzlich eine
 * Rückgängig-Snackbar den Fehlgriff — die Schwelle **könnte** deshalb sinken, das ist aber eine
 * eigene Entscheidung und in der `ROADMAP.md` noch offen.
 *
 * **Der eigene Wert bleibt in jedem Fall nötig**, auch wenn die Schwelle einmal sinkt: Der
 * Vorgabewert von `SwipeToDismissBoxDefaults.positionalThreshold` ist in Material 3 1.4.0 kein
 * Anteil, sondern feste 56 dp — auf einer 360 dp breiten Zeile rund 15 %. Wer die Angabe unten
 * weglässt, macht die Geste also nicht standardkonform, sondern dreimal empfindlicher als hier
 * gewollt.
 */
private const val DELETE_SWIPE_FRACTION = 0.85f

/**
 * Umschließt eine Zeile mit der Wischgeste. Gelöscht wird nur, was schon erledigt ist, und nur
 * nach rechts — siehe docs/decisions/0016-wischen-loescht-nur-erledigte-aufgaben.md.
 */
@Composable
internal fun SwipeableTodoRow(
    todo: Todo,
    deleteFailed: Boolean,
    isTemplateEntry: Boolean,
    onDoneChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onSwipedAway: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
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

    // Dasselbe Aufräumen für den zweiten Weg, auf dem eine weggewischte Zeile zurückkehrt: das
    // Rückgängig zum Löschen (ADR 0031). Die Aufgabe kommt unter **derselben** Dokument-id zurück,
    // und `rememberSwipeToDismissBoxState` merkt sich seinen Zustand über `rememberSaveable` — die
    // LazyColumn bewahrt den pro Item-Key auf. Die Zeile erschiene dann in der Stellung, in der sie
    // hinausgewischt wurde, also als leere Fläche.
    //
    // Vorsorge, kein beobachteter Fehler: ob die LazyColumn den Zustand eines *entfernten* Eintrags
    // wirklich aufbewahrt, hängt an Compose-Interna. Ein No-op, falls nicht — beim ersten Erscheinen
    // steht der Zustand ohnehin auf Settled.
    LaunchedEffect(todo.id) {
        if (swipeState.currentValue != SwipeToDismissBoxValue.Settled) swipeState.reset()
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
        TodoRow(
            todo = todo,
            isTemplateEntry = isTemplateEntry,
            onDoneChange = onDoneChange,
            onClick = onClick,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown
        )
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
            // TalkBack ohnehin nicht ausführen lässt. Dort führt der Löschen-Knopf unter den Feldern
            // des Bearbeiten-Dialogs zum Ziel (ADR 0016 verlangt ihn genau dafür, ADR 0032 sagt,
            // warum er dort steht und nicht in der Knopfzeile).
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
    isTemplateEntry: Boolean,
    onDoneChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val markerIconResId = todo.priority.markerIconResId()
    val moveUpLabel = stringResource(R.string.todo_list_move_up)
    val moveDownLabel = stringResource(R.string.todo_list_move_down)
    // Was sich nicht bewegen lässt, wird gar nicht erst angeboten — sonst liest TalkBack an einer
    // Gruppengrenze eine Aktion vor, die nichts tut.
    val reorderActions = remember(onMoveUp, onMoveDown, moveUpLabel, moveDownLabel) {
        listOfNotNull(
            onMoveUp?.let { CustomAccessibilityAction(moveUpLabel) { it(); true } },
            onMoveDown?.let { CustomAccessibilityAction(moveDownLabel) { it(); true } }
        )
    }

    ListItem(
        headlineContent = {
            Text(
                // In einer Vorlage steht die Menge vor dem Titel — genau so, wie der Eintrag in der
                // erzeugten Liste heißen wird („1 T-Shirt"). Ohne das wäre nicht zu sehen, welche
                // Einträge überhaupt mitskalieren, und das ist die eine Frage, die man an eine
                // Vorlage hat. Dieselbe Formatierung wie beim Skalieren, also keine zweite Regel.
                text = todo.quantity
                    ?.takeIf { isTemplateEntry }
                    ?.let { "${formatQuantity(it)} ${todo.title}" }
                    ?: todo.title,
                textDecoration = if (todo.isDone) TextDecoration.LineThrough else null
            )
        },
        // Die Semantik gehört **an diese Kette**, neben den Klick: Dieser Knoten ist der, den
        // TalkBack fokussiert (er fasst die Zeile zusammen), und auf einem Vorfahren deklarierte
        // Aktionen würden dort nicht angeboten.
        //
        // `combinedClickable` statt `clickable`, und der leere `onLongClick` ist der ganze Zweck:
        // Der lange Druck gehört der Ziehgeste, nicht dem Klick. Ohne ihn kennt der Tipp-Erkenner
        // nur „gedrückt und losgelassen" und öffnet beim Loslassen den Bearbeiten-Dialog — man hebt
        // die Zeile an, überlegt es sich anders, und bekommt einen Dialog. Der
        // `longPressDraggableHandle` des Vorfahren verhindert das nicht, er verbraucht den Druck
        // nicht. Belegt von `aLongPressWithoutMovementDoesNotOpenTheEditDialog`, der vorher rot war.
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = {})
            .semantics { customActions = reorderActions },
        // Angedeutet, nicht ausgebreitet: eine Zeile mit Auslassungspunkten, siehe
        // docs/decisions/0030-notiz-als-zweite-zeile.md. Keine eigene Farbe — ListItem färbt den
        // Slot schon auf onSurfaceVariant, und eine Farbe am inneren Text würde überschrieben.
        supportingContent = todo.notes?.let { notes ->
            {
                Text(text = notes, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        // In einer Vorlage gibt es nichts abzuhaken: Sie beschreibt, was jedes Mal dazugehört, und
        // erledigt wird erst in der Liste, die aus ihr entsteht (ADR 0034). Ohne Checkbox rückt die
        // Zeile nach links — das ist der sichtbare Unterschied zwischen den beiden Arten.
        leadingContent = if (isTemplateEntry) {
            null
        } else {
            { Checkbox(checked = todo.isDone, onCheckedChange = onDoneChange) }
        },
        trailingContent = if (markerIconResId == null) {
            null
        } else {
            {
                Icon(
                    painter = painterResource(markerIconResId),
                    // Die Stufe steckt sonst allein in der Form des Pfeils — für TalkBack ist das
                    // nichts.
                    contentDescription = stringResource(
                        R.string.todo_list_priority_content_description,
                        stringResource(todo.priority.labelResId())
                    ),
                    // Rot nur, solange die Aufgabe offen ist: Auf einer durchgestrichenen Zeile
                    // wäre es Lärm, und „niedrig" ist ohnehin kein Alarm.
                    tint = if (todo.isDone || todo.priority == TodoPriority.LOW) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        },
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
