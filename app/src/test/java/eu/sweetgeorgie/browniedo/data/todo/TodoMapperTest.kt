package eu.sweetgeorgie.browniedo.data.todo

import eu.sweetgeorgie.browniedo.domain.todo.Todo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.Date

class TodoMapperTest {

    @Test
    fun `maps a complete document onto the domain model`() {
        val document = TodoDocument(
            title = "Milch kaufen",
            done = true,
            createdAt = Date.from(CREATED_AT),
            updatedAt = Date.from(UPDATED_AT),
            completedBy = "uid-partner"
        )

        val todo = document.toTodo(DOCUMENT_ID)

        assertEquals(
            Todo(
                id = DOCUMENT_ID,                title = "Milch kaufen",
                isDone = true,
                createdAt = CREATED_AT,
                updatedAt = UPDATED_AT,
                completedBy = "uid-partner"
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
        completedBy = "uid-partner"
    )

    private companion object {
        const val DOCUMENT_ID = "todo-1"
        val CREATED_AT: Instant = Instant.ofEpochMilli(1_700_000_000_000)
        val UPDATED_AT: Instant = Instant.ofEpochMilli(1_700_000_060_000)
    }
}
