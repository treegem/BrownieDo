package eu.sweetgeorgie.browniedo.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority

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
    onDoneChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val markerIconResId = todo.priority.markerIconResId()

    ListItem(
        headlineContent = {
            Text(
                text = todo.title,
                textDecoration = if (todo.isDone) TextDecoration.LineThrough else null
            )
        },
        modifier = modifier.clickable(onClick = onClick),
        // Angedeutet, nicht ausgebreitet: eine Zeile mit Auslassungspunkten, siehe
        // docs/decisions/0030-notiz-als-zweite-zeile.md. Keine eigene Farbe — ListItem färbt den
        // Slot schon auf onSurfaceVariant, und eine Farbe am inneren Text würde überschrieben.
        supportingContent = todo.notes?.let { notes ->
            {
                Text(text = notes, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = { Checkbox(checked = todo.isDone, onCheckedChange = onDoneChange) },
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
