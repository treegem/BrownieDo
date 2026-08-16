package eu.sweetgeorgie.browniedo.domain.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

class TodoQuantityTest {

    // --- Eingabe lesen ---

    @Test
    fun `reads a whole number`() {
        assertEquals(3.0, "3".toPositiveDecimalOrNull()!!, 0.0)
    }

    /** Was die deutsche Tastatur liefert. */
    @Test
    fun `reads a decimal written with a comma`() {
        assertEquals(1.5, "1,5".toPositiveDecimalOrNull()!!, 0.0)
    }

    /** Und was eine Tastatur mit anderer Sprache liefert — beides muss durchgehen. */
    @Test
    fun `reads a decimal written with a dot`() {
        assertEquals(1.5, "1.5".toPositiveDecimalOrNull()!!, 0.0)
    }

    @Test
    fun `ignores surrounding whitespace`() {
        assertEquals(2.0, "  2  ".toPositiveDecimalOrNull()!!, 0.0)
    }

    @Test
    fun `rejects an empty field`() {
        assertNull("".toPositiveDecimalOrNull())
        assertNull("   ".toPositiveDecimalOrNull())
    }

    @Test
    fun `rejects text that is not a number`() {
        assertNull("abc".toPositiveDecimalOrNull())
        assertNull("1,2,3".toPositiveDecimalOrNull())
    }

    @Test
    fun `rejects zero and negative numbers`() {
        assertNull("0".toPositiveDecimalOrNull())
        assertNull("-2".toPositiveDecimalOrNull())
    }

    // --- Ausgabe schreiben ---

    @Test
    fun `writes a whole number without decimals`() {
        assertEquals("3", formatQuantity(3.0))
    }

    @Test
    fun `writes decimals with a german comma`() {
        assertEquals("1,5", formatQuantity(1.5))
    }

    /**
     * Der Fallstrick, der diese Funktion überhaupt nötig macht: `0.1 * 3` ist in `Double`
     * 0,30000000000000004, und das darf nicht in einem Aufgabentitel landen.
     */
    @Test
    fun `rounds away binary floating point artefacts`() {
        assertEquals("0,3", formatQuantity(0.1 * 3))
    }

    // --- Skalieren ---

    @Test
    fun `scaling puts the amount in front of the title`() {
        val scaled = TEMPLATE_ENTRY.copy(quantity = 1.0).scaledBy(factor = 3.0)

        assertEquals("3 T-Shirt", scaled.title)
    }

    @Test
    fun `scaling keeps decimals where they are needed`() {
        val scaled = TEMPLATE_ENTRY.copy(quantity = 0.5).scaledBy(factor = 3.0)

        assertEquals("1,5 T-Shirt", scaled.title)
    }

    /** Der Normalfall bleibt der billigste: Faktor 1 ergibt, was in der Vorlage steht. */
    @Test
    fun `a factor of one writes the amount unchanged`() {
        val scaled = TEMPLATE_ENTRY.copy(quantity = 2.0).scaledBy(factor = 1.0)

        assertEquals("2 T-Shirt", scaled.title)
    }

    /** Drei Tage heißen drei T-Shirts, aber nicht drei Shampoo. */
    @Test
    fun `an entry without an amount is left alone`() {
        val entry = TEMPLATE_ENTRY.copy(title = "Shampoo", quantity = null)

        // assertSame statt assertEquals: Der Eintrag wird nicht einmal kopiert.
        assertSame(entry, entry.scaledBy(factor = 3.0))
    }

    /**
     * Die Menge landet im Titel und **nicht** als Feld in der erzeugten Liste — was entsteht, ist
     * eine gewöhnliche Liste ohne Sonderregeln.
     */
    @Test
    fun `scaling drops the amount field`() {
        val scaled = TEMPLATE_ENTRY.copy(quantity = 1.0).scaledBy(factor = 3.0)

        assertNull(scaled.quantity)
    }

    @Test
    fun `scaling leaves every other field untouched`() {
        val entry = TEMPLATE_ENTRY.copy(quantity = 1.0, notes = "Die dünnen")
        val scaled = entry.scaledBy(factor = 3.0)

        assertEquals(entry.id, scaled.id)
        assertEquals(entry.createdAt, scaled.createdAt)
        assertEquals(entry.priority, scaled.priority)
        assertEquals(entry.notes, scaled.notes)
    }

    private companion object {
        val TIMESTAMP: Instant = Instant.parse("2026-08-16T10:00:00Z")

        val TEMPLATE_ENTRY = Todo(
            id = "todo-1",
            title = "T-Shirt",
            isDone = false,
            priority = TodoPriority.MEDIUM,
            createdAt = TIMESTAMP,
            updatedAt = TIMESTAMP,
            completedBy = null,
            completedAt = null,
            notes = null,
            quantity = null
        )
    }
}
