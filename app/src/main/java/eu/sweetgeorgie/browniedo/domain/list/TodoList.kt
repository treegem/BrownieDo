package eu.sweetgeorgie.browniedo.domain.list

/**
 * A list of todos.
 *
 * Named [TodoList] rather than `List` to stay clear of `kotlin.collections.List`.
 *
 * [isShared] carries what the UI actually needs — whether a partner is on the list — instead of the
 * raw member uids, which have no business outside the data layer. Shared simply means more than one
 * member, see docs/decisions/0009-listen-dokument-mit-todo-subcollection.md.
 *
 * [isTemplate] unterscheidet eine Vorlage von einer Arbeitsliste. Beide sind dasselbe Dokument in
 * derselben Collection und werden mit denselben Mitteln bearbeitet — der Unterschied liegt allein
 * darin, was man mit ihnen tut, siehe docs/decisions/0034-vorlagen-sind-listen-mit-einem-flag.md.
 */
data class TodoList(
    val id: String,
    val name: String,
    val isShared: Boolean,
    val isTemplate: Boolean
)
