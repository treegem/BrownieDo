package eu.sweetgeorgie.browniedo.domain.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TodoSortOrderTest {

    // --- Der Anker ---

    @Test
    fun `falls back to the creation time when nothing was sorted by hand`() {
        assertEquals(CREATED_AT_MILLIS.toDouble(), entry().effectiveOrder, 0.0)
    }

    @Test
    fun `prefers the value that was sorted by hand`() {
        assertEquals(42.0, entry(sortOrder = 42.0).effectiveOrder, 0.0)
    }

    /**
     * Die Gegenprobe zur Regel für die Menge: Dort wirft der Mapper alles `<= 0` weg, hier wäre das
     * falsch. Am Ende einer Gruppe entstehen negative Werte ganz regulär, sobald der Anker klein
     * genug ist, und 0 ist ein Platz wie jeder andere.
     */
    @Test
    fun `keeps zero and negative values as real positions`() {
        assertEquals(0.0, entry(sortOrder = 0.0).effectiveOrder, 0.0)
        assertEquals(-5.0, entry(sortOrder = -5.0).effectiveOrder, 0.0)
    }

    // --- Einen Platz ausrechnen ---

    @Test
    fun `puts an entry strictly between its two neighbours`() {
        val above = entry(id = "above", sortOrder = 300.0)
        val below = entry(id = "below", sortOrder = 100.0)

        assertEquals(200.0, sortOrderBetween(above, below)!!, 0.0)
    }

    @Test
    fun `puts an entry dropped at the top above its only neighbour`() {
        val below = entry(id = "below", sortOrder = 100.0)

        assertTrue(sortOrderBetween(above = null, below = below)!! > below.effectiveOrder)
    }

    @Test
    fun `puts an entry dropped at the bottom below its only neighbour`() {
        val above = entry(id = "above", sortOrder = 100.0)

        assertTrue(sortOrderBetween(above = above, below = null)!! < above.effectiveOrder)
    }

    @Test
    fun `has nothing to write for an entry without neighbours`() {
        assertNull(sortOrderBetween(above = null, below = null))
    }

    /**
     * Der Fall, den es ohne diese Prüfung dauerhaft und unerklärlich gäbe: Zwei nie sortierte
     * Aufgaben aus derselben Millisekunde haben denselben Anker, und zwischen zwei gleiche Zahlen
     * passt nichts. Der Aufrufer muss dann die Gruppe neu nummerieren.
     */
    @Test
    fun `refuses a drop between two neighbours with the same anchor`() {
        val above = entry(id = "above", sortOrder = 100.0)
        val below = entry(id = "below", sortOrder = 100.0)

        assertNull(sortOrderBetween(above, below))
    }

    @Test
    fun `refuses a drop between neighbours that are one step apart`() {
        val above = entry(id = "above", sortOrder = Math.nextUp(ANCHOR))
        val below = entry(id = "below", sortOrder = ANCHOR)

        assertNull(sortOrderBetween(above, below))
    }

    /**
     * Das Gegenstück zum `0,1 × 3`-Fall aus `TodoQuantityTest`: der Test, den es gibt, weil über
     * Fließkomma nachgedacht statt geraten wurde.
     *
     * Beim Anker um 1,7 × 10¹² liegt ein Schritt von `Double` bei 2⁻¹², eine Lücke von einer
     * Millisekunde fasst also 4096 davon — nach zwölf Halbierungen ist sie aufgebraucht. Die
     * dreizehnte Ablage wird verweigert statt still danebenzugreifen, und ab da hilft nur noch das
     * Neunummerieren.
     */
    @Test
    fun `survives twelve drops into the gap of a single millisecond and refuses the thirteenth`() {
        val below = entry(id = "below", sortOrder = ANCHOR)
        var above = entry(id = "above", sortOrder = ANCHOR + 1.0)

        repeat(12) { round ->
            val middle = sortOrderBetween(above, below)
            assertNotNull("Die Ablage Nummer ${round + 1} hätte noch gehen müssen", middle)
            above = entry(id = "above", sortOrder = middle!!)
        }

        assertNull(sortOrderBetween(above, below))
    }

    /** Der gemischte Fall, und für Monate nach dem Ausrollen der häufigste. */
    @Test
    fun `interpolates between a hand-sorted and a never-sorted neighbour`() {
        val above = entry(id = "above", sortOrder = CREATED_AT_MILLIS + 100.0)
        val below = entry(id = "below")

        val middle = sortOrderBetween(above, below)!!

        assertTrue(middle > below.effectiveOrder && middle < above.effectiveOrder)
    }

    // --- Neu nummerieren ---

    @Test
    fun `hands out descending values in the given order`() {
        val ordered = listOf(
            entry(id = "first", sortOrder = 500.0),
            entry(id = "second"),
            entry(id = "third")
        )

        val renumbered = renumberedSortOrders(ordered)

        assertEquals(listOf("first", "second", "third"), renumbered.keys.toList())
        assertTrue(renumbered.getValue("first") > renumbered.getValue("second"))
        assertTrue(renumbered.getValue("second") > renumbered.getValue("third"))
    }

    /** Die Gruppe bleibt, wo sie im Zahlenraum lag — sie springt nicht gegenüber den anderen Stufen. */
    @Test
    fun `starts renumbering at the anchor of the first entry`() {
        val ordered = listOf(entry(id = "first", sortOrder = 500.0), entry(id = "second"))

        assertEquals(500.0, renumberedSortOrders(ordered).getValue("first"), 0.0)
    }

    @Test
    fun `has nothing to renumber in an empty group`() {
        assertTrue(renumberedSortOrders(emptyList()).isEmpty())
    }

    private fun entry(id: String = "todo-1", sortOrder: Double? = null) = Todo(
        id = id,
        title = id,
        isDone = false,
        priority = TodoPriority.MEDIUM,
        createdAt = Instant.ofEpochMilli(CREATED_AT_MILLIS),
        updatedAt = Instant.ofEpochMilli(CREATED_AT_MILLIS),
        completedBy = null,
        completedAt = null,
        notes = null,
        quantity = null,
        sortOrder = sortOrder
    )

    private companion object {
        /** Ein realistischer Anlagezeitpunkt — die Größenordnung entscheidet über die Genauigkeit. */
        const val CREATED_AT_MILLIS = 1_786_000_000_000

        const val ANCHOR = 1_786_000_000_000.0
    }
}
