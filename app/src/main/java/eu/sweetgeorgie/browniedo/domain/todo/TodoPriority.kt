package eu.sweetgeorgie.browniedo.domain.todo

/**
 * Dringlichkeit einer Aufgabe. Neue Aufgaben stehen auf [MEDIUM], und eine Aufgabe ohne hinterlegte
 * Stufe gilt ebenfalls als [MEDIUM], siehe
 * docs/decisions/0023-prioritaet-migration-und-sortierung.md.
 *
 * Die Reihenfolge der Konstanten ist nicht beliebig: Sie steigt mit der Dringlichkeit. Davon hängen
 * zwei Dinge ab, die man an ihrer Verwendungsstelle nicht sieht — `TODO_ORDER` holt mit
 * `compareByDescending` die dringenden Aufgaben nach oben, und die Auswahl im Bearbeiten-Dialog
 * liest von links nach rechts „niedrig · mittel · hoch". Wer die Konstanten umsortiert, dreht
 * beides um; `TodoOrderTest` hält es fest.
 *
 * In Firestore steht der **Name** der Konstante, nicht ihre Position — eine spätere Umsortierung
 * darf gespeicherte Aufgaben nicht umdeuten.
 */
enum class TodoPriority {
    LOW,
    MEDIUM,
    HIGH
}
