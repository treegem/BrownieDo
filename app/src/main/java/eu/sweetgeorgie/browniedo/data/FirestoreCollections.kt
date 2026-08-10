package eu.sweetgeorgie.browniedo.data

/**
 * Firestore collection names. They live here rather than in one of the repositories because both
 * the list and the todo repository address the same path `lists/{listId}/todos`, see
 * docs/decisions/0009-listen-dokument-mit-todo-subcollection.md.
 */
internal const val LISTS_COLLECTION = "lists"

internal const val TODOS_COLLECTION = "todos"
