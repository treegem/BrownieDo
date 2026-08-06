package eu.sweetgeorgie.browniedo.data.todo

import eu.sweetgeorgie.browniedo.domain.todo.Todo

/**
 * Maps a Firestore document onto the domain model.
 *
 * Returns null when the document is unusable — an empty title or a missing timestamp means the
 * document was written by something other than this app. The repository decides how to report
 * that; silently mapping it to a placeholder would hide the problem.
 */
fun TodoDocument.toTodo(id: String): Todo? {
    val createdAtInstant = createdAt?.toInstant() ?: return null
    val updatedAtInstant = updatedAt?.toInstant() ?: return null
    if (title.isBlank()) return null

    return Todo(
        id = id,
        title = title,
        isDone = done,
        createdAt = createdAtInstant,
        updatedAt = updatedAtInstant,
        completedBy = completedBy.takeIf { done }
    )
}
