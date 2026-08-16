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
                completedAt = COMPLETED_AT,
                notes = "Die haltbare, nicht die frische",
                quantity = 2.0
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
    fun `reads an entry that has no notes field at all`() {
        // Der Migrationsfall: Aufgaben von vor Phase 12 tragen das Feld nicht. Firestores toObject
        // lässt es auf dem Standardwert stehen, also null — und die Aufgabe bleibt erhalten.
        val document = completeDocument().copy(notes = null)

        val todo = document.toTodo(DOCUMENT_ID)

        assertNotNull(todo)
        assertNull(todo?.notes)
    }

    @Test
    fun `reads an empty notes field as no notes`() {
        // Kann beim Editieren in der Console entstehen. Sonst trüge die Zeile eine leere zweite.
        val document = completeDocument().copy(notes = "   ")

        assertNull(document.toTodo(DOCUMENT_ID)?.notes)
    }

    @Test
    fun `reads an entry that has no quantity field at all`() {
        // Der Migrationsfall wie bei der Notiz: Aufgaben von vor Phase 14b tragen das Feld nicht,
        // und „keine Menge" ist die richtige Antwort — sie skalieren einfach nicht mit.
        val document = completeDocument().copy(quantity = null)

        val todo = document.toTodo(DOCUMENT_ID)

        assertNotNull(todo)
        assertNull(todo?.quantity)
    }

    @Test
    fun `reads a quantity of zero or less as no quantity`() {
        // Kann beim Editieren in der Console entstehen. Null und Zero hießen sonst dasselbe in zwei
        // Formen, und jede Rechenstelle bräuchte einen Sonderfall.
        assertNull(completeDocument().copy(quantity = 0.0).toTodo(DOCUMENT_ID)?.quantity)
        assertNull(completeDocument().copy(quantity = -1.0).toTodo(DOCUMENT_ID)?.quantity)
    }

    @Test
    fun `an entry without a quantity survives being mapped to the domain and back`() {
        // Die Menge muss beim Verschieben und beim Rückgängig mitwandern (ADR 0024) — und ihr Fehlen
        // darf die Aufgabe dabei nicht hängen lassen.
        val document = completeDocument().copy(quantity = null)

        assertEquals(
            document.copy(updatedAt = null),
            document.toTodo(DOCUMENT_ID)?.toDocument()
        )
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
    fun `writes every field a move has to carry`() {
        val todo = completeDocument().toTodo(DOCUMENT_ID)

        assertEquals(
            TodoDocument(
                title = "Milch kaufen",
                done = true,
                createdAt = Date.from(CREATED_AT),
                updatedAt = null,
                completedBy = "uid-partner",
                completedAt = Date.from(COMPLETED_AT),
                priority = TodoPriority.HIGH.name,
                notes = "Die haltbare, nicht die frische",
                quantity = 2.0
            ),
            todo?.toDocument()
        )
    }

    @Test
    fun `leaves the change time empty so the server fills it in`() {
        val todo = completeDocument().toTodo(DOCUMENT_ID)

        assertNull(todo?.toDocument()?.updatedAt)
    }

    @Test
    fun `keeps the creation time instead of letting the server set it`() {
        val todo = completeDocument().toTodo(DOCUMENT_ID)

        // @ServerTimestamp ersetzt nur null-Felder — ein gesetztes createdAt überlebt das
        // Verschieben, siehe docs/decisions/0024-verschieben-behaelt-zustand.md.
        assertEquals(Date.from(CREATED_AT), todo?.toDocument()?.createdAt)
    }

    @Test
    fun `writes the priority by name`() {
        val todo = completeDocument().copy(priority = TodoPriority.LOW.name).toTodo(DOCUMENT_ID)

        assertEquals(TodoPriority.LOW.name, todo?.toDocument()?.priority)
    }

    @Test
    fun `writes no completion time for an entry that is still open`() {
        val todo = completeDocument().copy(done = false).toTodo(DOCUMENT_ID)

        assertNull(todo?.toDocument()?.completedAt)
        assertNull(todo?.toDocument()?.completedBy)
    }

    @Test
    fun `a document survives being mapped to the domain and back`() {
        val document = completeDocument()

        val roundTripped = document.toTodo(DOCUMENT_ID)?.toDocument()

        // Nur updatedAt darf sich unterscheiden, es entsteht beim Verschieben neu. Alles andere
        // muss überleben — vergisst jemand ein neues Feld in toDocument(), scheitert dieser Test.
        assertEquals(document.copy(updatedAt = null), roundTripped)
    }

    @Test
    fun `an entry without notes survives being mapped to the domain and back`() {
        // Der Migrationsfall beim Verschieben: Eine Aufgabe von vor Phase 12 darf dabei nicht
        // hängen bleiben, nur weil das Feld fehlt.
        val document = completeDocument().copy(notes = null)

        val roundTripped = document.toTodo(DOCUMENT_ID)?.toDocument()

        assertEquals(document.copy(updatedAt = null), roundTripped)
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
        priority = TodoPriority.HIGH.name,
        notes = "Die haltbare, nicht die frische",
        quantity = 2.0
    )

    private companion object {
        const val DOCUMENT_ID = "todo-1"
        val CREATED_AT: Instant = Instant.ofEpochMilli(1_700_000_000_000)
        val UPDATED_AT: Instant = Instant.ofEpochMilli(1_700_000_060_000)
        val COMPLETED_AT: Instant = Instant.ofEpochMilli(1_700_000_030_000)
    }
}
