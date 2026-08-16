package eu.sweetgeorgie.browniedo.domain.todo

import java.time.Instant

/**
 * A single entry of a list.
 *
 * [updatedAt] is the server-assigned time of the last write and decides last-write-wins conflicts,
 * see docs/decisions/0006-server-zeitstempel-fuer-last-write-wins.md.
 */
data class Todo(
    val id: String,
    val title: String,
    val isDone: Boolean,
    val priority: TodoPriority,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Uid of the partner who ticked the entry off; null while it is still open. */
    val completedBy: String?,
    /**
     * When the entry was ticked off; null while it is open — and also null for entries that were
     * finished before this field existed. Those sort to the end of the finished block, see
     * docs/decisions/0023-prioritaet-migration-und-sortierung.md.
     */
    val completedAt: Instant?,
    /**
     * Was der Titel allein nach Wochen nicht mehr sagt. Null heißt „keine Notiz" — nicht „leer": Ein
     * geleertes Feld wird beim Speichern zu null, damit es nur eine Form für „nichts" gibt.
     *
     * Aufgaben von vor Phase 12 tragen das Feld nicht und werden ebenfalls zu null gelesen.
     */
    val notes: String?,
    /**
     * Die Menge eines **Vorlagen-Eintrags** — und ihr Vorhandensein ist zugleich der Schalter: Ein
     * Eintrag mit Menge wird beim Instanziieren mit dem Faktor multipliziert, einer ohne bleibt, wie
     * er ist. Drei Tage heißen drei T-Shirts, aber nicht drei Shampoo. Siehe
     * docs/decisions/0037-menge-am-eintrag-statt-zahl-im-titel.md.
     *
     * In einer Arbeitsliste steht hier null: Beim Instanziieren wandert die gerechnete Menge in den
     * Titel, das Feld selbst nicht mit.
     */
    val quantity: Double?
)
