# 0028 – Rückrufe des Aufgaben-Bildschirms in Actions-Haltern bündeln

**Status:** akzeptiert · **Datum:** 2026-08-12

## Kontext

`TodoListScreen` nahm **27 Lambdas** als Einzelparameter entgegen, und dieselbe Liste stand ein
zweites Mal in `MainActivity` und ein drittes Mal in `setScreenContent` des instrumentierten Tests.
Jedes neue Feld am Bearbeiten-Dialog kostete damit drei Stellen. Die Datei war auf 1067 Zeilen
gewachsen und trug den Bildschirm plus fünf Dialoge.

[ADR 0022](0022-verschieben-im-bearbeiten-dialog.md) hatte das vorhergesehen — „Wird er dadurch
unübersichtlich, ist das der nächste Anlass, ihn zu überarbeiten" — und
[ADR 0027](0027-termine-per-kalender-intent.md) machte es fällig: Mit dem Kalender-Knopf käme der
Dialog auf fünf Aufgaben, mit der Notiz aus Phase 12 auf sechs.

## Entscheidung

**Die Rückrufe reisen in vier `@Immutable data class`-Haltern, gruppiert nach ihrem Ort in der
Oberfläche**, nicht nach Domäne: `TodoListTopBarActions`, `ListDialogActions`, `TodoActions`,
`TodoEditActions` in `ui/todo/TodoListActions.kt`. Der Bildschirm nimmt damit **8 Parameter statt
28**, und jedes private Composable bekommt genau einen Halter.

`onErrorShown` und `onMovedMessageShown` bleiben Einzelparameter: Sie gehören dem `SnackbarHostState`
des Scaffolds und keinem der vier Bereiche.

Dazu wurde die Datei in fünf geteilt (`TodoListScreen`, `TodoListTopBar`, `TodoRow`,
`TodoListDialogs`, `TodoPriorityResources`). Zwei Helfer mit mehreren Nutzern haben die Schnitte
bestimmt: `ListMenuItem` bleibt bei der TopAppBar, weil ADR 0022 es ausdrücklich als „das vorhandene
`ListMenuItem` aus der TopAppBar" beschreibt, und die beiden Stufe→Ressource-Abbildungen ziehen in
eine eigene Datei, weil Zeile *und* Dialog `labelResId()` brauchen.

Zwei Details sind dabei nicht verhandelbar, und beide sieht man dem fertigen Code nicht an:

**`@Immutable data class` plus `remember` in `MainActivity`.** Compose kann einen Parameter nur
überspringen, wenn er sich vergleichen lässt. Vorher trug der Bildschirm 27 Methodenreferenzen
(`viewModel::onX`), und die sind strukturell gleich, wenn Empfänger und Methode gleich sind — der
Bildschirm war also überspringbar. Ein Halter mit bloßer Identitätsgleichheit hätte das kaputt
gemacht: Bei jeder Rekomposition ein neues Objekt, also jedes Mal der ganze Bildschirm neu. Die von
`data class` erzeugte `equals` stellt das wieder her. Zusätzlich wird die Konstruktion in
`remember(todoListViewModel)` gefasst, weil `onSignOutClick` ein Lambda-Literal ist, das
`appContainer` einfängt und sonst bei jeder Rekomposition neu entsteht. **Ohne beides wäre dieser
Umbau eine Verschlechterung**, die kein Test bemerkt.

**Keine Standardwerte auf den Haltern.** `val onConfirm: () -> Unit = {}` wäre für Vorschau und Test
bequem, ließe aber einen in `MainActivity` vergessenen Rückruf still zu „tut nichts" werden — genau
die Fehlerklasse, die in diesem Projekt schon zweimal wie ein grüner Build aussah (siehe den
`junit4.v2`-Punkt in `ROADMAP.md`). Vorschau und Test bauen ihre No-op-Halter deshalb selbst, je
einmal pro Datei. Dass sie damit zweimal existieren, ist kein Verstoß gegen `reuse-shared-constants`:
`main` und `androidTest` sind getrennte Source-Sets und könnten sie ohnehin nicht teilen.

## Konsequenzen

- Ein neues Feld am Bearbeiten-Dialog kostet **einen Eintrag im Halter** statt drei Stellen. Genau
  darauf zielen Phase 11 (Termin) und Phase 12 (Notiz).
- Der Test wird kürzer und aussagekräftiger: `setScreenContent` listet nicht mehr 27 Lambdas, und ein
  Test, der einen Rückruf beobachtet, schreibt
  `todoActions = NO_TODO_ACTIONS.copy(onTodoSwipedAway = { … })`. Dass die Halter `data class` sind,
  ist damit doppelt begründet — `copy` im Test, `equals` in der Composition.
- **Wer einen Halter anfasst, muss `@Immutable` und das `remember` in Ruhe lassen.** Ein Halter mit
  einer Liste oder einem anderen veränderlichen Feld wäre gelogen und würde die Überspringbarkeit
  aufheben, ohne dass etwas rot wird.
- Der Umbau war reine Struktur: **97 Unit-Tests und 16 instrumentierte Tests blieben grün, ohne dass
  eine einzige Zusicherung angepasst wurde** — das war das Akzeptanzkriterium. `TodoListViewModel` und
  `TodoListUiState` wurden nicht angefasst.
- Dies sieht auf den ersten Blick wie ein Verstoß gegen `avoid-unnecessary-wrappers` (SHOULD FIX) aus.
  Ist es nicht: Die Halter umhüllen keine Funktionalität, sie bündeln Parameter — und `ROADMAP.md`
  fragt seit ADR 0022 genau danach.

## Alternativen

- **Das ViewModel direkt in den Bildschirm geben.** Der kürzeste Weg zu einem Parameter, aber er
  bricht „Composables rendern Zustand und senden Ereignisse" aus
  `.github/instructions/architecture.instructions.md` — und er macht `TodoListScreenTest` unmöglich,
  der den Bildschirm heute ohne Firebase und ohne Anmeldung prüft. Verworfen.
- **Ein einziger großer `TodoListActions`-Halter.** Verschiebt die Liste von 27 Parametern nur an eine
  andere Stelle, ohne zu zeigen, wohin ein Rückruf gehört, und jedes Composable bekäme Zugriff auf
  alles. Verworfen.
- **Nach Domäne gruppieren** (Listen / Aufgaben / Bearbeiten) statt nach Ort. Klingt sauberer, führt
  aber dazu, dass ein Halter auf zwei Composables aufgeteilt werden muss — die Listen-Rückrufe sitzen
  teils in der TopAppBar, teils in den Dialogen. Nach Ort bekommt jedes Composable genau einen Halter,
  und die Durchreiche bleibt kurz. Verworfen.
- **Nur die Datei aufteilen, die 27 Parameter stehen lassen.** Behebt die Lesbarkeit, nicht das
  Wachstum — und das Wachstum ist der Grund, warum ADR 0022 und 0027 auf diesen Punkt zeigen.
  Verworfen.
