package eu.sweetgeorgie.browniedo.data.todo

import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TodoOrderTest {

    @Test
    fun `puts open entries above ticked ones`() {
        val todos = listOf(
            todo(id = "ticked-new", isDone = true, createdAtMillis = 4),
            todo(id = "open-old", isDone = false, createdAtMillis = 1)
        )

        assertEquals(
            listOf("open-old", "ticked-new"),
            todos.sortedWith(TODO_ORDER).map(Todo::id)
        )
    }

    @Test
    fun `keeps every open entry above every ticked one whatever their priority`() {
        val todos = listOf(
            todo(id = "ticked-urgent", isDone = true, createdAtMillis = 4, TodoPriority.HIGH),
            todo(id = "open-unimportant", isDone = false, createdAtMillis = 1, TodoPriority.LOW)
        )

        assertEquals(
            listOf("open-unimportant", "ticked-urgent"),
            todos.sortedWith(TODO_ORDER).map(Todo::id)
        )
    }

    @Test
    fun `sorts the newest first within each group`() {
        val todos = listOf(
            todo(id = "open-old", isDone = false, createdAtMillis = 1),
            todo(id = "ticked-old", isDone = true, createdAtMillis = 2),
            todo(id = "open-new", isDone = false, createdAtMillis = 3),
            todo(id = "ticked-new", isDone = true, createdAtMillis = 4)
        )

        assertEquals(
            listOf("open-new", "open-old", "ticked-new", "ticked-old"),
            todos.sortedWith(TODO_ORDER).map(Todo::id)
        )
    }

    @Test
    fun `sorts open entries by priority before their age`() {
        val todos = listOf(
            todo(id = "unimportant-new", isDone = false, createdAtMillis = 3, TodoPriority.LOW),
            todo(id = "normal-newer", isDone = false, createdAtMillis = 4, TodoPriority.MEDIUM),
            todo(id = "urgent-old", isDone = false, createdAtMillis = 1, TodoPriority.HIGH)
        )

        assertEquals(
            listOf("urgent-old", "normal-newer", "unimportant-new"),
            todos.sortedWith(TODO_ORDER).map(Todo::id)
        )
    }

    @Test
    fun `keeps the newest on top within one priority`() {
        val todos = listOf(
            todo(id = "urgent-old", isDone = false, createdAtMillis = 1, TodoPriority.HIGH),
            todo(id = "urgent-new", isDone = false, createdAtMillis = 2, TodoPriority.HIGH)
        )

        assertEquals(
            listOf("urgent-new", "urgent-old"),
            todos.sortedWith(TODO_ORDER).map(Todo::id)
        )
    }

    @Test
    fun `sorts ticked entries by when they were ticked off`() {
        // Das Anlagedatum läuft hier bewusst gegen den Erledigungszeitpunkt: Nur so belegt der
        // Test, dass wirklich completedAt entscheidet und nicht mehr createdAt.
        val todos = listOf(
            todo(id = "ticked-first", isDone = true, createdAtMillis = 9, completedAtMillis = 1),
            todo(id = "ticked-last", isDone = true, createdAtMillis = 2, completedAtMillis = 8)
        )

        assertEquals(
            listOf("ticked-last", "ticked-first"),
            todos.sortedWith(TODO_ORDER).map(Todo::id)
        )
    }

    @Test
    fun `does not reorder ticked entries by priority`() {
        val todos = listOf(
            todo(
                id = "urgent-ticked-earlier",
                isDone = true,
                createdAtMillis = 1,
                priority = TodoPriority.HIGH,
                completedAtMillis = 1
            ),
            todo(
                id = "unimportant-ticked-later",
                isDone = true,
                createdAtMillis = 2,
                priority = TodoPriority.LOW,
                completedAtMillis = 2
            )
        )

        assertEquals(
            listOf("unimportant-ticked-later", "urgent-ticked-earlier"),
            todos.sortedWith(TODO_ORDER).map(Todo::id)
        )
    }

    @Test
    fun `puts ticked entries without a completion time last`() {
        // Aufgaben von vor Phase 9. Sie dürfen nicht so aussehen, als wären sie gerade abgehakt
        // worden — genau das passiert, wenn nullsLast innerhalb einer absteigenden Sortierung
        // steht statt um sie herum.
        val todos = listOf(
            todo(id = "ticked-long-ago", isDone = true, createdAtMillis = 9),
            todo(id = "ticked-recently", isDone = true, createdAtMillis = 1, completedAtMillis = 5)
        )

        assertEquals(
            listOf("ticked-recently", "ticked-long-ago"),
            todos.sortedWith(TODO_ORDER).map(Todo::id)
        )
    }

    // --- Von Hand sortieren (ADR 0039) ---

    /**
     * Die Ausroll-Absicherung: Solange niemand von Hand sortiert hat, kommt exakt die Reihenfolge
     * heraus, die vor Phase 15 galt. Dass die Tests darüber alle unverändert grün bleiben, sagt
     * dasselbe — dieser hier sagt es ausdrücklich.
     */
    @Test
    fun `keeps the order of entries that were never sorted by hand`() {
        val todos = listOf(
            todo(id = "old", isDone = false, createdAtMillis = 1),
            todo(id = "new", isDone = false, createdAtMillis = 3),
            todo(id = "middle", isDone = false, createdAtMillis = 2)
        )

        assertEquals(listOf("new", "middle", "old"), todos.sortedWith(TODO_ORDER).map(Todo::id))
    }

    @Test
    fun `lifts a hand-sorted entry above a neighbour it is younger than`() {
        val todos = listOf(
            todo(id = "newer", isDone = false, createdAtMillis = 10),
            todo(id = "dragged-up", isDone = false, createdAtMillis = 1, sortOrder = 20.0)
        )

        assertEquals(listOf("dragged-up", "newer"), todos.sortedWith(TODO_ORDER).map(Todo::id))
    }

    @Test
    fun `sinks a hand-sorted entry below a neighbour it is older than`() {
        val todos = listOf(
            todo(id = "dragged-down", isDone = false, createdAtMillis = 10, sortOrder = 1.0),
            todo(id = "older", isDone = false, createdAtMillis = 5)
        )

        assertEquals(listOf("older", "dragged-down"), todos.sortedWith(TODO_ORDER).map(Todo::id))
    }

    /**
     * Regel 2 der Phase, festgenagelt: Die Priorität steht über der Handsortierung. Ein absurd hoher
     * Wert an einer niedrigen Stufe überholt keine mittlere.
     */
    @Test
    fun `never lets a hand-sorted entry cross a priority boundary`() {
        val todos = listOf(
            todo(
                id = "unimportant-dragged-up",
                isDone = false,
                createdAtMillis = 1,
                priority = TodoPriority.LOW,
                sortOrder = Double.MAX_VALUE
            ),
            todo(id = "normal", isDone = false, createdAtMillis = 1, priority = TodoPriority.MEDIUM)
        )

        assertEquals(
            listOf("normal", "unimportant-dragged-up"),
            todos.sortedWith(TODO_ORDER).map(Todo::id)
        )
    }

    /**
     * Regel 1, und das Spiegelbild von `does not reorder ticked entries by priority`: Der erledigte
     * Block ist ein Protokoll nach Erledigungszeitpunkt. Ein `sortOrder` bleibt an einer abgehakten
     * Aufgabe zwar stehen, wirkt dort aber nicht.
     */
    @Test
    fun `does not reorder ticked entries by hand-sorting`() {
        val todos = listOf(
            todo(
                id = "ticked-long-ago",
                isDone = true,
                createdAtMillis = 1,
                completedAtMillis = 2,
                sortOrder = Double.MAX_VALUE
            ),
            todo(id = "ticked-recently", isDone = true, createdAtMillis = 1, completedAtMillis = 5)
        )

        assertEquals(
            listOf("ticked-recently", "ticked-long-ago"),
            todos.sortedWith(TODO_ORDER).map(Todo::id)
        )
    }

    /**
     * Warum `addTodo` keinen Wert vergeben muss: Der Anlagezeitpunkt einer frischen Aufgabe liegt
     * über jedem Wert, der aus einem älteren Anker gerechnet wurde.
     */
    @Test
    fun `puts a brand-new entry above entries that were sorted by hand`() {
        val todos = listOf(
            todo(id = "dragged", isDone = false, createdAtMillis = 1, sortOrder = 500.0),
            todo(id = "brand-new", isDone = false, createdAtMillis = 1_000)
        )

        assertEquals(listOf("brand-new", "dragged"), todos.sortedWith(TODO_ORDER).map(Todo::id))
    }

    private fun todo(
        id: String,
        isDone: Boolean,
        createdAtMillis: Long,
        priority: TodoPriority = TodoPriority.MEDIUM,
        completedAtMillis: Long? = null,
        // Vorgabe null: Damit bleibt jeder Test, der vor Phase 15 geschrieben wurde, unverändert
        // gültig — und dass er weiterhin grün ist, ist selbst schon ein Beleg dafür, dass der
        // Rückfall auf createdAt die alte Reihenfolge exakt trifft (ADR 0039).
        sortOrder: Double? = null
    ): Todo {
        val createdAt = Instant.ofEpochMilli(createdAtMillis)
        return Todo(
            id = id,
            title = id,
            isDone = isDone,
            priority = priority,
            createdAt = createdAt,
            // Ohne Belang für die Reihenfolge, muss aber gesetzt sein.
            updatedAt = createdAt,
            completedBy = "uid-partner".takeIf { isDone },
            completedAt = completedAtMillis?.let(Instant::ofEpochMilli),
            // Notiz und Menge ordnen nichts — TODO_ORDER fasst beide nicht an.
            notes = null,
            quantity = null,
            sortOrder = sortOrder
        )
    }
}
