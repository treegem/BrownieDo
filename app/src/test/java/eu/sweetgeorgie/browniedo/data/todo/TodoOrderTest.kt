package eu.sweetgeorgie.browniedo.data.todo

import eu.sweetgeorgie.browniedo.domain.todo.Todo
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

    private fun todo(id: String, isDone: Boolean, createdAtMillis: Long): Todo {
        val createdAt = Instant.ofEpochMilli(createdAtMillis)
        return Todo(
            id = id,
            title = id,
            isDone = isDone,
            createdAt = createdAt,
            // Ohne Belang für die Reihenfolge, muss aber gesetzt sein.
            updatedAt = createdAt,
            completedBy = "uid-partner".takeIf { isDone }
        )
    }
}
