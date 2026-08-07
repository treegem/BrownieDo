# 0012 – Scaffold pro Bildschirm statt app-weit

**Status:** akzeptiert · **Datum:** 2026-08-07

## Kontext

Bis Phase 6 gab es genau ein `Scaffold`, und zwar in `MainActivity` um die Verzweigung zwischen
`LoginScreen` und `TodoListScreen` herum. Es hatte keine `topBar` und keine `bottomBar`, sein
`innerPadding` wurde als `Modifier` an beide Bildschirme durchgereicht.

Mit dem Material-3-Layout bekommt die Aufgabenliste eine TopAppBar (Titel plus Overflow-Menü) und
eine `bottomBar` mit dem Eingabefeld für neue Aufgaben (ADR 0013). Beide Leisten lesen
`TodoListUiState`: Der Hinzufügen-Button ist nur aktiv, wenn `newTodoTitle` nicht leer ist.

Bliebe das `Scaffold` in `MainActivity`, müsste dort `TodoListViewModel.uiState` **vor** der
Verzweigung auf `signedInUser` eingesammelt werden — das ViewModel würde also schon instanziiert,
während der Nutzer noch auf dem Login-Bildschirm steht, und der Firestore-Listener liefe ohne
angemeldeten Nutzer los.

## Entscheidung

Jeder Bildschirm bringt sein eigenes `Scaffold` mit. `MainActivity` ist nur noch eine Weiche
zwischen den beiden Bildschirmen und reicht keinen Layout-`Modifier` mehr durch.

`LoginScreen` bekommt ein `Scaffold` **ohne** Leisten. Es wird nicht wegen der Leisten gebraucht,
sondern weil sonst niemand `colorScheme.background` malt und der Inhalt unter Statusleiste und
Display-Aussparung rutschen würde.

## Konsequenzen

- Die Listen-Auswahl aus Phase 8 kann direkt in `TodoListTopBar` wandern und ihren Zustand aus
  `TodoListViewModel` beziehen, ohne dass Todo-Zustand durch `MainActivity` läuft.
- Insets werden pro Bildschirm gelöst statt einmal zentral. Für die Aufgabenliste ist das sogar
  nötig: Nur die `bottomBar` weiß, dass sie sich über die Tastatur schieben muss.
- Käme später eine wirklich app-weite Leiste dazu (etwa eine Navigationsleiste über mehrere
  Bildschirme), müsste jeder Bildschirm angefasst werden. Bei zwei Bildschirmen ist das billig.
- Wer einen neuen Bildschirm anlegt, muss daran denken, ein `Scaffold` zu setzen — sonst fehlen
  Hintergrund und Insets. Das ist der Preis dafür, dass es keine gemeinsame Hülle gibt.

## Alternativen

- **`Scaffold` in `MainActivity` behalten und `topBar`/`bottomBar` als Composable-Parameter
  hochreichen:** Ergibt eine Hülle mit genau einem echten Aufrufer und zwingt den Todo-Zustand
  trotzdem nach `MainActivity` — inklusive des ViewModels, das auf dem Login-Bildschirm gar nicht
  gebraucht wird. Verstößt außerdem gegen `avoid-unnecessary-wrappers.instructions.md`.
- **`LoginScreen` ohne `Scaffold`, nur mit `safeDrawingPadding()`:** Löst die Insets, aber nicht
  den Hintergrund. Durch die Compose-Oberfläche schiene der Fensterhintergrund aus `themes.xml`
  statt der Hintergrundfarbe des Farbschemas.
