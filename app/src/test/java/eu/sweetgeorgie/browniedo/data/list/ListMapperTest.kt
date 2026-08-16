package eu.sweetgeorgie.browniedo.data.list

import eu.sweetgeorgie.browniedo.domain.list.TodoList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Date

class ListMapperTest {

    @Test
    fun `maps a complete document onto the domain model`() {
        val document = ListDocument(
            name = "Einkauf",
            members = listOf("uid-1", "uid-2"),
            createdAt = Date.from(CREATED_AT)
        )

        assertEquals(
            TodoList(id = DOCUMENT_ID, name = "Einkauf", isShared = true, isTemplate = false),
            document.toTodoList(DOCUMENT_ID)
        )
    }

    @Test
    fun `a list with a single member is private`() {
        val document = completeDocument().copy(members = listOf("uid-1"))

        assertFalse(document.toTodoList(DOCUMENT_ID)!!.isShared)
    }

    @Test
    fun `a list with more than one member is shared`() {
        assertTrue(completeDocument().toTodoList(DOCUMENT_ID)!!.isShared)
    }

    @Test
    fun `rejects a document without a name`() {
        assertNull(completeDocument().copy(name = "  ").toTodoList(DOCUMENT_ID))
    }

    @Test
    fun `rejects a document nobody belongs to`() {
        assertNull(completeDocument().copy(members = emptyList()).toTodoList(DOCUMENT_ID))
    }

    @Test
    fun `a document marked as a template maps to a template`() {
        assertTrue(completeDocument().copy(isTemplate = true).toTodoList(DOCUMENT_ID)!!.isTemplate)
    }

    /**
     * Der Migrationsfall: Listen von vor Phase 14 tragen das Feld nicht. Firestore lässt es dann auf
     * dem Standardwert des Dokuments stehen — hier nachgestellt, indem der Standard nicht gesetzt
     * wird. Kein Nachziehen in der Console nötig.
     */
    @Test
    fun `a document without the template field is a plain list`() {
        assertFalse(completeDocument().toTodoList(DOCUMENT_ID)!!.isTemplate)
    }

    @Test
    fun `maps without a creation timestamp — nothing reads it`() {
        assertEquals(
            TodoList(id = DOCUMENT_ID, name = "Einkauf", isShared = true, isTemplate = false),
            completeDocument().copy(createdAt = null).toTodoList(DOCUMENT_ID)
        )
    }

    private fun completeDocument() = ListDocument(
        name = "Einkauf",
        members = listOf("uid-1", "uid-2"),
        createdAt = Date.from(CREATED_AT)
    )

    private companion object {
        const val DOCUMENT_ID = "list-1"
        val CREATED_AT: Instant = Instant.ofEpochMilli(1_700_000_000_000)
    }
}
