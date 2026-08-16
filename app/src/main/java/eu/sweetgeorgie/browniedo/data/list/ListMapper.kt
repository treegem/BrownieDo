package eu.sweetgeorgie.browniedo.data.list

import eu.sweetgeorgie.browniedo.domain.list.TodoList

/**
 * Maps a Firestore document onto the domain model.
 *
 * Returns null when the document is unusable — a blank name or no members at all means it was not
 * written the way this app expects. Same reasoning as for todos: silently mapping it to a
 * placeholder would hide the problem.
 *
 * `createdAt` is deliberately dropped. Nothing in the app orders or shows lists by creation time;
 * they are sorted by name. Carrying a field nobody reads would only invite it to drift.
 */
fun ListDocument.toTodoList(id: String): TodoList? {
    if (name.isBlank()) return null
    if (members.isEmpty()) return null

    return TodoList(
        id = id,
        name = name,
        isShared = members.size > 1,
        isTemplate = isTemplate
    )
}
