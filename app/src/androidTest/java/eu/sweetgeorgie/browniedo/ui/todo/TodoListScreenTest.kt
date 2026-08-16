package eu.sweetgeorgie.browniedo.ui.todo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.sweetgeorgie.browniedo.R
import eu.sweetgeorgie.browniedo.domain.list.TodoList
import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import eu.sweetgeorgie.browniedo.ui.theme.BrownieDoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Prüft, was [TodoListScreen] anstelle der Liste anzeigt (Fehler, Laden, Leerzustand) und dass die
 * Wischgeste nur erledigte Aufgaben löscht. Der Bildschirm ist zustandslos, der Test kommt daher
 * ohne Firebase und ohne Anmeldung aus.
 */
@RunWith(AndroidJUnit4::class)
class TodoListScreenTest {

    /**
     * Die `v2`-Fassung der Regel: Sie tauscht die ganze `AndroidComposeUiTestEnvironment`, die
     * Komposition wird eingereiht statt sofort ausgeführt. Der Wechsel war deshalb kein
     * Import-Austausch, sondern eine Verhaltensänderung — und ein Fehlschlag sieht dabei aus wie ein
     * grüner Build: Mit derselben Regel scheiterten hier schon einmal **alle** Tests reproduzierbar
     * an „No compose hierarchies found", ohne dass es jemandem auffiel.
     *
     * Deshalb gilt für diese Datei: **Nach jeder Änderung einmal auf einem Gerät laufen lassen und
     * die Anzahl grüner Tests nachzählen.** Ein grüner Gradle-Lauf allein beweist nichts, wenn die
     * Tests gar nicht erst zur Komposition kommen.
     */
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aWriteErrorIsShownInASnackbarAndClearedAfterwards() {
        var errorShownCount = 0
        setScreenContent(
            uiState = TodoListUiState(selectedList = LIST, isLoading = false, error = TodoListError.ADD_FAILED),
            onErrorShown = { errorShownCount++ }
        )

        val message = composeTestRule.activity.getString(R.string.todo_list_error_add_failed)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()

        // Die Snackbar verschwindet von selbst, erst danach wird der Fehler gelöscht.
        composeTestRule.waitUntil(timeoutMillis = DISMISS_TIMEOUT_MILLIS) { errorShownCount == 1 }
    }

    @Test
    fun aLoadErrorStaysVisibleAndIsNotCleared() {
        var errorShownCount = 0
        setScreenContent(
            uiState = TodoListUiState(selectedList = LIST, isLoading = false, error = TodoListError.LOAD_FAILED),
            onErrorShown = { errorShownCount++ }
        )

        val message = composeTestRule.activity.getString(R.string.todo_list_error_load_failed)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()

        composeTestRule.waitForIdle()
        assertEquals(0, errorShownCount)
    }

    /**
     * Die Snackbar zum Löschen ist die einzige mit einer Aktion — geprüft wird deshalb, dass der
     * Knopf da ist und der Tipp als „Rückgängig" ankommt und nicht als Wegwischen (ADR 0031).
     */
    @Test
    fun theUndoActionOfTheDeleteSnackbarReportsTheTap() {
        var undoCount = 0
        var messageShownCount = 0
        setScreenContent(
            uiState = TodoListUiState(
                selectedList = LIST,
                isLoading = false,
                deletedTodo = FINISHED_TODO
            ),
            onUndoDelete = { undoCount++ },
            onDeletedMessageShown = { messageShownCount++ }
        )

        val message = composeTestRule.activity.getString(R.string.todo_list_deleted)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.todo_list_undo))
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = DISMISS_TIMEOUT_MILLIS) { undoCount == 1 }
        // Genau einer der beiden Rückrufe läuft, sonst wäre das Angebot doppelt beantwortet.
        assertEquals(0, messageShownCount)
    }

    @Test
    fun theProgressIndicatorIsShownWhileTheListIsLoading() {
        setScreenContent(uiState = TodoListUiState(selectedList = LIST, isLoading = true))

        val label = composeTestRule.activity.getString(R.string.todo_list_loading)
        composeTestRule.onNodeWithContentDescription(label).assertIsDisplayed()
    }

    @Test
    fun anEmptyListInvitesTheUserToAddTheFirstEntry() {
        setScreenContent(uiState = TodoListUiState(selectedList = LIST, isLoading = false))

        val headline = composeTestRule.activity.getString(R.string.todo_list_empty_headline)
        val hint = composeTestRule.activity.getString(R.string.todo_list_empty_hint)
        composeTestRule.onNodeWithText(headline).assertIsDisplayed()
        composeTestRule.onNodeWithText(hint).assertIsDisplayed()
    }

    @Test
    fun aLoadErrorIsShownInsteadOfTheEmptyState() {
        setScreenContent(
            uiState = TodoListUiState(selectedList = LIST, isLoading = false, error = TodoListError.LOAD_FAILED)
        )

        val message = composeTestRule.activity.getString(R.string.todo_list_error_load_failed)
        val headline = composeTestRule.activity.getString(R.string.todo_list_empty_headline)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        composeTestRule.onNodeWithText(headline).assertDoesNotExist()
    }

    @Test
    fun aFinishedEntrySwipedToTheRightIsDeleted() {
        var swipedAway: Todo? = null
        setScreenContent(
            uiState = TodoListUiState(selectedList = LIST, isLoading = false, todos = TODOS),
            todoActions = NO_TODO_ACTIONS.copy(onTodoSwipedAway = { swipedAway = it })
        )

        composeTestRule.onNodeWithText(FINISHED_TODO.title).performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        assertEquals(FINISHED_TODO.id, swipedAway?.id)
    }

    @Test
    fun anEntryThatIsStillOpenCannotBeSwipedAway() {
        var swipedAway: Todo? = null
        setScreenContent(
            uiState = TodoListUiState(selectedList = LIST, isLoading = false, todos = TODOS),
            todoActions = NO_TODO_ACTIONS.copy(onTodoSwipedAway = { swipedAway = it })
        )

        composeTestRule.onNodeWithText(OPEN_TODO.title).performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        assertNull(swipedAway)
        // Die Zeile muss stehen bleiben, nicht nur den Rückruf unterlassen.
        composeTestRule.onNodeWithText(OPEN_TODO.title).assertIsDisplayed()
    }

    @Test
    fun anUrgentEntryCarriesAMarker() {
        setScreenContent(
            uiState = TodoListUiState(
                selectedList = LIST,
                isLoading = false,
                todos = listOf(URGENT_TODO)
            )
        )

        composeTestRule.onNodeWithContentDescription(priorityDescription(R.string.todo_list_priority_high))
            .assertIsDisplayed()
    }

    @Test
    fun anEntryOfMiddlingPriorityCarriesNoMarker() {
        setScreenContent(
            uiState = TodoListUiState(
                selectedList = LIST,
                isLoading = false,
                todos = listOf(OPEN_TODO)
            )
        )

        // „mittel" ist der Normalfall und bekommt bewusst kein Zeichen.
        composeTestRule.onNodeWithContentDescription(priorityDescription(R.string.todo_list_priority_medium))
            .assertDoesNotExist()
    }

    @Test
    fun theEditDialogPreselectsTheCurrentPriority() {
        setScreenContent(
            uiState = TodoListUiState(
                selectedList = LIST,
                isLoading = false,
                todos = listOf(URGENT_TODO),
                editedTodo = TodoEdit(
                    todoId = URGENT_TODO.id,
                    title = URGENT_TODO.title,
                    priority = TodoPriority.HIGH,
                    targetListId = LIST.id,
                    notes = "",
                    quantity = ""
                )
            )
        )

        val label = composeTestRule.activity.getString(R.string.todo_list_priority_high)
        composeTestRule.onNodeWithText(label).assertIsSelected()
    }

    @Test
    fun pickingAPriorityInTheEditDialogReportsIt() {
        var picked: TodoPriority? = null
        setScreenContent(
            uiState = TodoListUiState(
                selectedList = LIST,
                isLoading = false,
                todos = listOf(OPEN_TODO),
                editedTodo = TodoEdit(
                    todoId = OPEN_TODO.id,
                    title = OPEN_TODO.title,
                    priority = TodoPriority.MEDIUM,
                    targetListId = LIST.id,
                    notes = "",
                    quantity = ""
                )
            ),
            editActions = NO_EDIT_ACTIONS.copy(onPriorityChange = { picked = it })
        )

        val label = composeTestRule.activity.getString(R.string.todo_list_priority_low)
        composeTestRule.onNodeWithText(label).performClick()

        assertEquals(TodoPriority.LOW, picked)
    }

    @Test
    fun theEditDialogShowsTheListTheEntryIsIn() {
        setScreenContent(uiState = editingIn(LIST, lists = listOf(LIST, OTHER_LIST)))

        // Auf den Knoten *im Dialog* eingegrenzt: Der Name der aktuellen Liste steht auch im
        // TopAppBar-Titel, ein bloßes onNodeWithText fände zwei Knoten und scheiterte daran —
        // dieselbe Mehrdeutigkeit, vor der der Test unten bei offenem Menü warnt.
        //
        // Der Knoten ist seit ADR 0033 das schreibgeschützte Feld; `hasText` findet es, weil der
        // Matcher auch `EditableText` liest. Die Gefahr eines zweiten Treffers liegt damit nicht
        // mehr bei einem Geschwister-`Text`, sondern bei einem Symbol oder `supportingText` am Feld.
        composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(LIST.name))
            .assertIsDisplayed()
    }

    /**
     * Beschriftung und Wert stehen in **einem** verschmolzenen Knoten — das ist der Vertrag, den das
     * Feld für TalkBack erfüllt („Liste, Einkauf, Dropdown-Liste"). Der Test fängt genau den Fehler,
     * den ein `leadingIcon` oder ein `supportingText` einführen würde: Er spaltet den Knoten auf.
     */
    @Test
    fun theTargetListFieldCarriesItsLabelAndTheCurrentList() {
        setScreenContent(uiState = editingIn(LIST, lists = listOf(LIST, OTHER_LIST)))

        val label = composeTestRule.activity.getString(R.string.todo_list_target_list_label)
        composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(label))
            .assertTextContains(LIST.name)
    }

    @Test
    fun pickingAnotherListInTheEditDialogReportsIt() {
        var picked: TodoList? = null
        setScreenContent(
            uiState = editingIn(LIST, lists = listOf(LIST, OTHER_LIST)),
            editActions = NO_EDIT_ACTIONS.copy(onTargetListChange = { picked = it })
        )

        // Angetippt wird das Feld über seine Beschriftung, nicht der Pfeil daneben: Seit ADR 0033
        // ist das Feld die Steuerung, der Pfeil trägt keine eigene Beschreibung mehr.
        composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(targetListLabel()))
            .performClick()
        // Gegen den Namen der *anderen* Liste prüfen: Der Name der aktuellen steht bei offenem
        // Menü zweimal auf dem Bildschirm — im Feld und im Menü — und wäre mehrdeutig.
        composeTestRule.onNodeWithText(OTHER_LIST.name).performClick()

        assertEquals(OTHER_LIST, picked)
    }

    @Test
    fun theListCannotBePickedWhileItIsTheOnlyOne() {
        setScreenContent(uiState = editingIn(LIST, lists = listOf(LIST)))

        val field = composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(targetListLabel()))
        // Die direkte Zusicherung: Das Feld ist abgeblendet. **Nicht** assertHasNoClickAction — ein
        // abgeblendetes Textfeld behält seinen onClick absichtlich und markiert ihn als `disabled`;
        // TalkBack unterdrückt die Aktion daraufhin selbst.
        field.assertIsNotEnabled()
        field.performClick()

        // Das Menü bleibt zu: Das Symbol für die geteilte Liste gibt es nur im Menüeintrag.
        val sharedLabel = composeTestRule.activity.getString(R.string.todo_list_shared_list)
        composeTestRule.onNodeWithContentDescription(sharedLabel).assertDoesNotExist()
    }

    @Test
    fun aSuccessfulMoveIsConfirmedInASnackbar() {
        var shownCount = 0
        setScreenContent(
            uiState = TodoListUiState(
                lists = listOf(LIST, OTHER_LIST),
                selectedList = LIST,
                isLoading = false,
                movedToListName = OTHER_LIST.name
            ),
            onMovedMessageShown = { shownCount++ }
        )

        val message = composeTestRule.activity
            .getString(R.string.todo_list_moved_to, OTHER_LIST.name)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()

        composeTestRule.waitUntil(timeoutMillis = DISMISS_TIMEOUT_MILLIS) { shownCount == 1 }
    }

    @Test
    fun theCalendarButtonInTheEditDialogReportsTheTitle() {
        var reportedTitle: String? = null
        setScreenContent(
            uiState = editingIn(LIST, lists = listOf(LIST)),
            editActions = NO_EDIT_ACTIONS.copy(onCalendarEventClick = { reportedTitle = it })
        )

        val label = composeTestRule.activity.getString(R.string.todo_list_create_calendar_event)
        composeTestRule.onNodeWithText(label).performClick()

        // Der Titel kommt aus dem Feld, nicht aus dem gespeicherten Eintrag — der Dialog ist die
        // einzige Quelle, solange nichts gespeichert wurde.
        assertEquals(OPEN_TODO.title, reportedTitle)
    }

    /**
     * Seit ADR 0032 steht Löschen im Inhalt des Dialogs und nicht mehr in der Knopfzeile. Der Test
     * prüft, dass es dort erreichbar ist und meldet.
     *
     * **Auf den Dialog eingegrenzt:** „Löschen" ist auch die Beschriftung der Bestätigung in
     * `DeleteListDialog`. Beide Dialoge stehen nie gleichzeitig offen, aber ein bloßes
     * `onNodeWithText` würde bei zwei Treffern scheitern, statt sich einen auszusuchen — die Lehre
     * aus dem zweiten Testlauf, siehe „Querlaufend" in der `ROADMAP.md`.
     */
    @Test
    fun theDeleteButtonInTheEditDialogReportsTheTap() {
        var deleteCount = 0
        setScreenContent(
            uiState = editingIn(LIST, lists = listOf(LIST)),
            editActions = NO_EDIT_ACTIONS.copy(onDelete = { deleteCount++ })
        )

        val label = composeTestRule.activity.getString(R.string.todo_list_delete)
        composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(label)).performClick()

        assertEquals(1, deleteCount)
    }

    @Test
    fun theSaveButtonInTheEditDialogReportsTheTap() {
        var confirmCount = 0
        setScreenContent(
            uiState = editingIn(LIST, lists = listOf(LIST)),
            editActions = NO_EDIT_ACTIONS.copy(onConfirm = { confirmCount++ })
        )

        val label = composeTestRule.activity.getString(R.string.todo_list_save)
        composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(label)).performClick()

        assertEquals(1, confirmCount)
    }

    /**
     * Die Zusicherung, um die es beim gefüllten Knopf geht: Ein leerer Titel lässt sich nicht
     * speichern, und das ist am Knopf zu sehen — bei zwei gleich aussehenden Textknöpfen war der
     * Unterschied zwischen aktiv und deaktiviert nur eine zweite Grünnuance.
     */
    @Test
    fun theSaveButtonIsDisabledWithoutATitle() {
        var confirmCount = 0
        val state = editingIn(LIST, lists = listOf(LIST))
        setScreenContent(
            uiState = state.copy(editedTodo = state.editedTodo?.copy(title = "")),
            editActions = NO_EDIT_ACTIONS.copy(onConfirm = { confirmCount++ })
        )

        val label = composeTestRule.activity.getString(R.string.todo_list_save)
        val saveButton = composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(label))
        saveButton.assertIsNotEnabled()
        saveButton.performClick()

        assertEquals(0, confirmCount)
    }

    @Test
    fun aRowShowsTheNotesBelowTheTitle() {
        setScreenContent(
            uiState = TodoListUiState(
                selectedList = LIST,
                isLoading = false,
                todos = listOf(TODO_WITH_NOTES)
            )
        )

        composeTestRule.onNodeWithText(TODO_WITH_NOTES.notes!!).assertIsDisplayed()
    }

    @Test
    fun aRowWithoutNotesHasNoSecondLine() {
        setScreenContent(
            uiState = TodoListUiState(
                selectedList = LIST,
                isLoading = false,
                todos = listOf(OPEN_TODO, TODO_WITH_NOTES)
            )
        )

        // Gegen die Notiz der *anderen* Zeile geprüft: Stünde sie an beiden, wäre der Knoten
        // zweimal da — der Test soll aber zeigen, dass eine Zeile ohne Notiz keine zweite Zeile hat.
        composeTestRule.onNodeWithText(OPEN_TODO.title).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(TODO_WITH_NOTES.notes!!).assertCountEquals(1)
    }

    @Test
    fun typingNotesInTheEditDialogReportsIt() {
        var typed: String? = null
        setScreenContent(
            uiState = editingIn(LIST, lists = listOf(LIST)),
            editActions = NO_EDIT_ACTIONS.copy(onNotesChange = { typed = it })
        )

        // Gefunden wird hier das **Feld**, nicht seine Beschriftung: Ein Textfeld verschmilzt die
        // Semantik seiner Beschriftung mit hinein. Deshalb muss die Notiz-Beschriftung im `label`
        // des Felds bleiben — sie zu einem Kopf-Text über dem Feld zu machen, wie bei der
        // Priorität, würde diesen Test unbrauchbar machen: `hasText` fände dann den Kopf-Text,
        // und der kann keinen Text annehmen.
        val label = composeTestRule.activity.getString(R.string.todo_list_notes_label)
        composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(label))
            .performTextInput("Die haltbare")

        assertEquals("Die haltbare", typed)
    }

    // --- Vorlagen (ADR 0034) ---

    @Test
    fun anOpenTemplateIsMarkedInTheTopBar() {
        setScreenContent(uiState = openTemplate())

        val label = composeTestRule.activity.getString(R.string.todo_list_template)
        composeTestRule.onNodeWithContentDescription(label).assertIsDisplayed()
    }

    /**
     * Der sichtbare Unterschied zwischen einer Vorlage und einer Liste: In einer Vorlage gibt es
     * nichts abzuhaken. Gesucht wird über [isToggleable], nicht über einen Text — die Checkbox trägt
     * keinen.
     */
    @Test
    fun aTemplateRowHasNoCheckbox() {
        setScreenContent(uiState = openTemplate())

        composeTestRule.onNodeWithText(OPEN_TODO.title).assertIsDisplayed()
        composeTestRule.onNode(isToggleable()).assertDoesNotExist()
    }

    /**
     * Die Gegenprobe zum Test darüber, und sie ist keine Zierde: Ohne sie beweist ein
     * `assertDoesNotExist` nur, dass der Matcher nichts findet — nicht, dass er überhaupt etwas
     * finden *könnte*.
     */
    @Test
    fun aListRowHasACheckbox() {
        setScreenContent(
            uiState = TodoListUiState(
                lists = listOf(LIST),
                selectedList = LIST,
                isLoading = false,
                todos = listOf(OPEN_TODO)
            )
        )

        composeTestRule.onNode(isToggleable()).assertIsDisplayed()
    }

    @Test
    fun theEditDialogInATemplateOffersNoCalendarAction() {
        setScreenContent(uiState = editingInTemplate())

        // Ein Vorlagen-Eintrag hat keinen Tag, an dem er stattfindet (ADR 0027 und ADR 0034).
        val label = composeTestRule.activity.getString(R.string.todo_list_create_calendar_event)
        composeTestRule.onNodeWithText(label).assertDoesNotExist()
        // Aber gelöscht wird auch hier über den Dialog, das ist der TalkBack-Weg (ADR 0016).
        val deleteLabel = composeTestRule.activity.getString(R.string.todo_list_delete)
        composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(deleteLabel))
            .assertIsDisplayed()
    }

    @Test
    fun theListMenuShowsTemplatesInTheirOwnSection() {
        setScreenContent(uiState = openTemplate())

        // Der Titel ist die Auswahl (ADR 0013). Vor dem Tipp steht der Name nur dort.
        composeTestRule.onNodeWithText(TEMPLATE.name).performClick()

        val section = composeTestRule.activity.getString(R.string.todo_list_templates_section)
        composeTestRule.onNodeWithText(section).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.todo_list_new_template))
            .assertIsDisplayed()
    }

    // --- Menge und Faktor (ADR 0037) ---

    @Test
    fun theQuantityFieldInATemplateReportsWhatIsTyped() {
        var typed: String? = null
        setScreenContent(
            uiState = editingInTemplate(),
            editActions = NO_EDIT_ACTIONS.copy(onQuantityChange = { typed = it })
        )

        composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(quantityLabel()))
            .performTextInput("3")

        assertEquals("3", typed)
    }

    /**
     * Die Gegenprobe zum Test darüber: In einer Arbeitsliste ist die Zahl längst Teil des Titels, das
     * Feld hat dort nichts zu suchen. Ohne diese Zusicherung bliebe offen, ob das Feld überhaupt vom
     * Modus abhängt.
     */
    @Test
    fun theQuantityFieldIsAbsentInAWorkingList() {
        setScreenContent(uiState = editingIn(LIST, lists = listOf(LIST)))

        composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(quantityLabel()))
            .assertDoesNotExist()
    }

    @Test
    fun aTemplateRowShowsTheQuantityInFrontOfTheTitle() {
        val state = openTemplate()
        setScreenContent(uiState = state.copy(todos = listOf(OPEN_TODO.copy(quantity = 2.0))))

        // Genau so wird der Eintrag in der erzeugten Liste heißen.
        composeTestRule.onNodeWithText("2 ${OPEN_TODO.title}").assertIsDisplayed()
    }

    @Test
    fun theSaveButtonIsDisabledWhileTheQuantityIsUnreadable() {
        var confirmCount = 0
        val state = editingInTemplate()
        setScreenContent(
            uiState = state.copy(editedTodo = state.editedTodo?.copy(quantity = "zwei")),
            editActions = NO_EDIT_ACTIONS.copy(onConfirm = { confirmCount++ })
        )

        val label = composeTestRule.activity.getString(R.string.todo_list_save)
        val saveButton = composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(label))
        saveButton.assertIsNotEnabled()
        saveButton.performClick()

        assertEquals(0, confirmCount)
    }

    @Test
    fun theFactorFieldIsShownWhenInstantiatingATemplate() {
        var typed: String? = null
        val state = openTemplate()
        setScreenContent(
            uiState = state.copy(
                // Leer statt der Vorgabe „1", damit der Test die Eingabe misst und nicht das
                // Anhängen an einen vorhandenen Wert.
                newList = NewList(name = TEMPLATE.name, kind = NewListKind.FROM_TEMPLATE, factor = "")
            ),
            listDialogActions = NO_LIST_DIALOG_ACTIONS.copy(onNewListFactorChange = { typed = it })
        )

        composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(factorLabel()))
            .performTextInput("3")

        assertEquals("3", typed)
    }

    /** Ohne Vorlage gibt es keine Mengen — und damit nichts, was ein Faktor tun könnte. */
    @Test
    fun theFactorFieldIsAbsentWhenCreatingAPlainList() {
        val state = openTemplate()
        setScreenContent(uiState = state.copy(newList = NewList(kind = NewListKind.LIST)))

        composeTestRule.onNode(hasAnyAncestor(isDialog()) and hasText(factorLabel()))
            .assertDoesNotExist()
    }

    private fun quantityLabel(): String =
        composeTestRule.activity.getString(R.string.todo_list_quantity_label)

    private fun factorLabel(): String =
        composeTestRule.activity.getString(R.string.todo_list_factor_label)

    /** Eine offene Vorlage mit einem Eintrag — der Ausgangspunkt der Vorlagen-Tests. */
    private fun openTemplate() = TodoListUiState(
        lists = listOf(LIST),
        templates = listOf(TEMPLATE),
        selectedList = TEMPLATE,
        isLoading = false,
        todos = listOf(OPEN_TODO)
    )

    private fun editingInTemplate(): TodoListUiState {
        val state = openTemplate()
        return state.copy(
            editedTodo = TodoEdit(
                todoId = OPEN_TODO.id,
                title = OPEN_TODO.title,
                priority = TodoPriority.MEDIUM,
                targetListId = TEMPLATE.id,
                notes = "",
                quantity = ""
            )
        )
    }

    private fun editingIn(list: TodoList, lists: List<TodoList>) = TodoListUiState(
        lists = lists,
        selectedList = list,
        isLoading = false,
        todos = listOf(OPEN_TODO),
        editedTodo = TodoEdit(
            todoId = OPEN_TODO.id,
            title = OPEN_TODO.title,
            priority = TodoPriority.MEDIUM,
            targetListId = list.id,
            notes = "",
            quantity = ""
        )
    )

    /** Die Beschriftung des Zielliste-Felds — der Griff, an dem drei Tests es anfassen. */
    private fun targetListLabel(): String =
        composeTestRule.activity.getString(R.string.todo_list_target_list_label)

    private fun priorityDescription(labelResId: Int): String =
        composeTestRule.activity.getString(
            R.string.todo_list_priority_content_description,
            composeTestRule.activity.getString(labelResId)
        )

    /**
     * Der Bildschirm nimmt die Rückrufe gebündelt (ADR 0028). Wer einen davon beobachten will,
     * übergibt `NO_TODO_ACTIONS.copy(…)` bzw. `NO_EDIT_ACTIONS.copy(…)` — dafür sind die Halter
     * `data class`.
     *
     * Die Snackbar-Rückrufe bleiben hier dagegen einzelne Parameter: Ein Test, der einen davon
     * beobachtet, soll nicht die anderen drei mit aufschreiben müssen. Der Halter entsteht daraus an
     * einer Stelle.
     */
    private fun setScreenContent(
        uiState: TodoListUiState,
        onErrorShown: () -> Unit = {},
        todoActions: TodoActions = NO_TODO_ACTIONS,
        editActions: TodoEditActions = NO_EDIT_ACTIONS,
        listDialogActions: ListDialogActions = NO_LIST_DIALOG_ACTIONS,
        onMovedMessageShown: () -> Unit = {},
        onUndoDelete: () -> Unit = {},
        onDeletedMessageShown: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            BrownieDoTheme {
                TodoListScreen(
                    uiState = uiState,
                    topBarActions = NO_TOP_BAR_ACTIONS,
                    listDialogActions = listDialogActions,
                    todoActions = todoActions,
                    editActions = editActions,
                    snackbarActions = SnackbarActions(
                        onErrorShown = onErrorShown,
                        onMovedMessageShown = onMovedMessageShown,
                        onUndoDelete = onUndoDelete,
                        onDeletedMessageShown = onDeletedMessageShown
                    )
                )
            }
        }
    }

    private companion object {
        const val DISMISS_TIMEOUT_MILLIS = 10_000L

        /*
         * Halter, die nichts tun — die Vorgabe für jeden Test, der den jeweiligen Bereich nicht
         * beobachtet. Die Halter selbst tragen bewusst keine Standardwerte, damit ein in
         * MainActivity vergessener Rückruf auffällt statt still nichts zu tun (ADR 0028); für den
         * Test ist „tut nichts" dagegen genau richtig.
         */

        val NO_TOP_BAR_ACTIONS = TodoListTopBarActions(
            onListSelected = {},
            onNewListClick = {},
            onNewTemplateClick = {},
            onCreateListFromTemplateClick = {},
            onRenameListClick = {},
            onDeleteListClick = {},
            onSignOutClick = {}
        )

        val NO_LIST_DIALOG_ACTIONS = ListDialogActions(
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

        val NO_TODO_ACTIONS = TodoActions(
            onNewTodoTitleChange = {},
            onAddTodoClick = {},
            onTodoDoneChange = { _, _ -> },
            onTodoSwipedAway = {},
            onEditTodoClick = {}
        )

        val NO_EDIT_ACTIONS = TodoEditActions(
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

        val LIST = TodoList(id = "list-1", name = "Einkauf", isShared = true, isTemplate = false)

        val OTHER_LIST =
            TodoList(id = "list-2", name = "Zuhause", isShared = false, isTemplate = false)

        val TEMPLATE =
            TodoList(id = "template-1", name = "Urlaub packen", isShared = true, isTemplate = true)

        val TIMESTAMP: Instant = Instant.parse("2026-08-07T20:00:00Z")

        val OPEN_TODO = Todo(
            id = "todo-open",
            title = "Milch kaufen",
            isDone = false,
            priority = TodoPriority.MEDIUM,
            createdAt = TIMESTAMP,
            updatedAt = TIMESTAMP,
            completedBy = null,
            completedAt = null,
            notes = null,
            quantity = null
        )

        val TODO_WITH_NOTES = OPEN_TODO.copy(
            id = "todo-with-notes",
            title = "Fenster abdichten",
            notes = "Das im Schlafzimmer, Schaumband aus dem Baumarkt"
        )

        val FINISHED_TODO = OPEN_TODO.copy(
            id = "todo-finished",
            title = "Kaffee kaufen",
            isDone = true,
            completedBy = "uid-1",
            completedAt = TIMESTAMP
        )

        val URGENT_TODO = OPEN_TODO.copy(
            id = "todo-urgent",
            title = "Geschenk besorgen",
            priority = TodoPriority.HIGH
        )

        val TODOS = listOf(OPEN_TODO, FINISHED_TODO)
    }
}
