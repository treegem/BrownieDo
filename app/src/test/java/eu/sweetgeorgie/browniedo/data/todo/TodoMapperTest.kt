package eu.sweetgeorgie.browniedo.data.todo

import eu.sweetgeorgie.browniedo.domain.todo.Todo
import eu.sweetgeorgie.browniedo.domain.todo.TodoPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.Date

class TodoMapperTest {

    @Test
    fun `maps a complete document onto the domain model`() {
        val todo = completeDocument().toTodo(DOCUMENT_ID)

        assertEquals(
            Todo(
                id = DOCUMENT_ID,
                title = "Milch kaufen",
                isDone = true,
                priority = TodoPriority.HIGH,
                createdAt = CREATED_AT,
                updatedAt = UPDATED_AT,
                completedBy = "uid-partner",
                completedAt = COMPLETED_AT
            ),
            todo
        )
    }

    @Test
    fun `drops the completing partner while the entry is still open`() {
        val document = completeDocument().copy(done = false, completedBy = "uid-partner")

        assertNull(document.toTodo(DOCUMENT_ID)?.completedBy)
    }

    @Test
    fun `drops the completion time while the entry is still open`() {
        val document = completeDocument().copy(done = false)

        assertNull(document.toTodo(DOCUMENT_ID)?.completedAt)
    }

    @Test
    fun `keeps a finished entry that never recorded when it was ticked off`() {
        val document = completeDocument().copy(completedAt = null)

        val todo = document.toTodo(DOCUMENT_ID)

        // Aufgaben, die vor Phase 9 abgehakt wurden, haben das Feld nicht — sie dürfen deshalb
        // nicht verworfen werden.
        assertNotNull(todo)
        assertNull(todo?.completedAt)
    }

    @Test
    fun `reads every priority it knows`() {
        TodoPriority.entries.forEach { priority ->
            val document = completeDocument().copy(priority = priority.name)

            assertEquals(priority, document.toTodo(DOCUMENT_ID)?.priority)
        }
    }

    @Test
    fun `falls back to medium when the priority is missing`() {
        val document = completeDocument().copy(priority = null)

        assertEquals(TodoPriority.MEDIUM, document.toTodo(DOCUMENT_ID)?.priority)
    }

    @Test
    fun `falls back to medium for a priority it does not know`() {
        // Kann ein Gerät mit neuerer App-Version geschrieben haben. Ein Enum-Feld im Dokument
        // würde hier werfen, statt das Dokument zu verwerfen.
        val document = completeDocument().copy(priority = "URGENT")

        assertEquals(TodoPriority.MEDIUM, document.toTodo(DOCUMENT_ID)?.priority)
    }

    @Test
    fun `rejects a document whose server timestamps are still missing`() {
        assertNull(completeDocument().copy(createdAt = null).toTodo(DOCUMENT_ID))
        assertNull(completeDocument().copy(updatedAt = null).toTodo(DOCUMENT_ID))
    }

    @Test
    fun `rejects a document without a title`() {
        assertNull(completeDocument().copy(title = "  ").toTodo(DOCUMENT_ID))
    }

    private fun completeDocument() = TodoDocument(
        title = "Milch kaufen",
        done = true,
        createdAt = Date.from(CREATED_AT),
        updatedAt = Date.from(UPDATED_AT),
        completedBy = "uid-partner",
        completedAt = Date.from(COMPLETED_AT),
        priority = TodoPriority.HIGH.name
    )

    private companion object {
        const val DOCUMENT_ID = "todo-1"
        val CREATED_AT: Instant = Instant.ofEpochMilli(1_700_000_000_000)
        val UPDATED_AT: Instant = Instant.ofEpochMilli(1_700_000_060_000)
        val COMPLETED_AT: Instant = Instant.ofEpochMilli(1_700_000_030_000)
    }
}
