package eu.sweetgeorgie.browniedo.domain.list

/**
 * A list of todos.
 *
 * Named [TodoList] rather than `List` to stay clear of `kotlin.collections.List`.
 *
 * [isShared] carries what the UI actually needs — whether a partner is on the list — instead of the
 * raw member uids, which have no business outside the data layer. Shared simply means more than one
 * member, see docs/decisions/0009-listen-dokument-mit-todo-subcollection.md.
 */
data class TodoList(
    val id: String,
    val name: String,
    val isShared: Boolean
)
