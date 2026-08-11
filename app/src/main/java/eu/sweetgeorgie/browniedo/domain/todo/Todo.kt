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
    val completedAt: Instant?
)
