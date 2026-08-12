# 0029 – Der Kalender-Fehler läuft über `TodoListError`

**Status:** akzeptiert · **Datum:** 2026-08-12

## Kontext

[ADR 0027](0027-termine-per-kalender-intent.md) hält für Phase 11 fest, dass das Feature vollständig
in der UI-Schicht lebt, und schreibt dazu: „Das ViewModel wird nicht angefasst — es gibt keinen
Zustand und keine Regel, die dort hingehörte."

Bei der Umsetzung zeigte sich eine Stelle, die dabei übersehen wurde: der **Fehlschlag**. Findet
weder der Google Kalender noch eine andere App den `ACTION_INSERT`-Intent, soll eine Snackbar
kommen — `ROADMAP.md` verlangt dafür ausdrücklich einen neuen Wert in `TodoListError`. Dieses Enum
sitzt zwar in `ui/todo`, aber das Feld `TodoListUiState.error` wird ausschließlich vom ViewModel
geschrieben, und nur daraus speist sich die vorhandene Snackbar-Mechanik in `TodoListScreen`.

## Entscheidung

**`TodoListError` bekommt `CALENDAR_APP_MISSING`, und das ViewModel bekommt einen Einzeiler
`onCalendarAppMissing()`, der ihn setzt.** Die Verdrahtung in `MainActivity` bleibt eine Zeile:

```kotlin
onCalendarEventClick = { title ->
    if (!startCalendarEventInsert(this@MainActivity, title)) {
        todoListViewModel.onCalendarAppMissing()
    }
}
```

Der Satz aus ADR 0027 ist damit in diesem einen Punkt überholt. **Alles Übrige an ADR 0027 gilt
unverändert:** Intent statt Calendar API, kein Datum, erst mit Paket und dann ohne, kein
`resolveActivity`, kein Rückkanal, kein gespeicherter Zustand. `Intent` und `CalendarContract`
bleiben in `ui/todo/CalendarEventIntent.kt` und kommen dem ViewModel nicht nahe — es erfährt nur,
*dass* etwas schiefging, nicht *womit*.

## Konsequenzen

- Der Kalender-Fehler verhält sich wie die acht anderen: Er erscheint als Snackbar, und
  `onErrorShown()` räumt ihn wieder auf. Kein zusätzliches Feld im UiState, kein dritter
  Meldungskanal — anders als bei `movedToListName`, das eine *Bestätigung* trägt und deshalb neben
  `error` stehen muss.
- Er ist der einzige Wert in `TodoListError`, der nicht aus einem Schreibvorgang stammt. Das steht
  als Kommentar am Enum-Wert, damit die Reihe nicht als „Firestore-Fehler" missverstanden wird.
- **Der Fehlerweg ist ohne Gerät prüfbar** und im `TodoListViewModelTest` mit zwei Tests abgedeckt.
  Ein Zustand im Bildschirm wäre nur instrumentiert prüfbar gewesen.
- `messageResId()` in `TodoListScreen` ist ein `when` ohne `else` — der neue Wert erzwingt dort
  einen Zweig. Der Fehler ohne Meldung ist damit unmöglich.
- ADR 0027 wird **nicht** umgeschrieben, wie in `docs/decisions/README.md` festgelegt. Wer dort den
  Satz zum ViewModel liest, findet die Korrektur hier.

## Alternativen

- **Ein `remember { mutableStateOf<TodoListError?>(null) }` in `TodoListScreen`.** Hält ADR 0027
  wörtlich ein und war der erste Entwurf. Verworfen: Er verdoppelt die `LaunchedEffect`-Mechanik am
  `SnackbarHostState` ein drittes Mal, macht den Bildschirm zustandsbehaftet — was er heute
  ausdrücklich nicht ist, siehe die Einleitung von `TodoListScreenTest` — und verlegt einen
  prüfbaren Weg vom Unit- in den instrumentierten Test.
- **Gar keine Meldung, der Knopf tut im Fehlerfall nichts.** Der Fall ist selten (beide Geräte haben
  einen Kalender), aber ein Knopf, der stumm nichts tut, ist genau die Fehlerklasse, die dieses
  Projekt schon zweimal getroffen hat (siehe ADR 0028 zu den fehlenden Standardwerten). Verworfen.
- **Eine `Toast`-Meldung direkt aus dem Intent-Aufruf.** Spart das Enum und das ViewModel, bringt
  aber einen zweiten Meldungsstil in eine App, die alles über Snackbars sagt. Verworfen.
