# 0033 – Bearbeiten bleibt ein Dialog, bis ein Auslöser greift

**Status:** akzeptiert · **Datum:** 2026-08-12

## Kontext

Der Bearbeiten-Dialog trägt fünf Eingaben (Titel, Notiz, Priorität, Zielliste — und mit dem Löschen
aus [ADR 0032](0032-gefuellte-bestaetigung-und-loeschen-im-inhalt.md) zwei Aktionszeilen im Inhalt),
dazu die Knopfzeile. Seit Phase 12 hat er ein `verticalScroll`. Material 3 zieht die Linie bei
„Formular mit mehreren Eingaben → Full-Screen-Dialog", und der Durchgang nach Phase 12 hat die Frage
gestellt, ob Bearbeiten ein eigener Bildschirm werden soll — ausdrücklich als **Entscheidung**, nicht
als Aufgabe.

Zwei Argumente, die die Frage einmal getragen haben, sind inzwischen **verbraucht** und dürfen nicht
noch einmal auftreten:

- Die kaputte Knopfzeile ist mit ADR 0032 gelöst; „ein eigener Bildschirm hätte das Problem gar nicht"
  ist damit kein Grund mehr.
- [ADR 0022](0022-verschieben-im-bearbeiten-dialog.md) hatte für den Fall vorgesorgt („Wird er dadurch
  unübersichtlich, ist das der nächste Anlass") — das ist der Ursprung dieser Frage, nicht ihre
  Antwort.

Die Kosten sind gemessen, nicht geschätzt. Ein eigener Bildschirm bedeutet:

- **~133 Zeilen und 10 öffentliche Methoden** wandern aus `TodoListViewModel` (471 Zeilen) heraus.
- **Drei bildschirmübergreifende Kopplungen müssen neu entworfen werden.** `onEditConfirm` liest beim
  Verschieben `state.todos` und `state.lists`, weil ADR 0024 die *ganze* Aufgabe verlangt.
  `onDeleteTodoClick` liest `state.todos` für den Rückgängig-Schnappschuss und schreibt `deletedTodo`
  — die Snackbar dazu lebt im **Listen**-Bildschirm ([ADR 0031](0031-rueckgaengig-statt-rueckfrage-beim-loeschen.md)).
  `movedToListName` genauso. Vier der zehn `TodoListError`-Werte entstehen nur im Bearbeiten-Pfad.
- **38 Tests angefasst:** 10 der 24 instrumentierten Tests sind Dialog-Tests, 5 davon grenzen über
  `hasAnyAncestor(isDialog())` ein — das gilt für einen Bildschirm nicht mehr. Rund 28 Unit-Tests
  ziehen um, und die Attrappen liegen als private Klassen *innerhalb* von `TodoListViewModelTest`;
  sie herauszulösen fasst alle 70 Tests dort an.
- **Zwei im Repo präzedenzlose Mechanismen:** Es gibt nirgends `BackHandler` und nirgends
  `rememberSaveable` oder `SavedStateHandle`. Ein Dialog bekommt die Zurück-Taste geschenkt, ein
  Bildschirm nicht.

Dagegen steht als Gewinn: ein Scrollen weniger und ein Drittel weniger ViewModel.

## Entscheidung

**Bearbeiten bleibt ein `AlertDialog`.** Das ist eine Entscheidung und keine Verschiebung aus
Zeitmangel: Alle vier Mängel, die die Frage aufgeworfen haben, lagen *im* Dialog und kosteten rund
15 Zeilen — fehlendes `fillMaxWidth()`, uneinheitliche Beschriftungen, fehlende `keyboardOptions`,
und die Zielliste als selbstgebautes Steuerelement. Keiner davon kam vom Behälter.

**Der Bau ist vorab genehmigt, sobald einer dieser Auslöser eintritt.** Jeder einzelne genügt, und
jeder ist eine überprüfbare Tatsache statt einer Geschmacksfrage:

1. **Ein sechstes Eingabefeld** landet im Dialog (Fälligkeit, Zuweisung, Wiederholung, Unterpunkte,
   Anhang). Fünf ist die hier festgehaltene Obergrenze.
2. **Eine dritte Aktion im Inhalt** tritt neben Termin und Löschen. ADR 0032 hat zwei dort
   untergebracht; drei machen aus dem Inhalt ein Menü.
3. **`maxLines = 5` an der Notiz muss steigen** — dann will jemand einen echten Notiz-Editor, und das
   ist dasselbe Eingeständnis auf einem anderen Weg.
4. **Der Geräteblick scheitert** bei Schriftskalierung ≥ 1,3 mit offener Tastatur: Wenn Priorität oder
   Zielliste nicht erreichbar sind, ohne an „Löschen" vorbeizuscrollen, oder wenn „Löschen" beim
   Scrollen getroffen wird. ADR 0032 hat das als „für Sehende die eigentliche Frage" offen gelassen;
   die zusätzliche Höhe des neuen Zielliste-Felds macht den nächsten Blick zu dem, der entscheidet.
5. **Ein zweiter Weg in den Editor** entsteht — Deep-Link, Benachrichtigungsaktion, Widget,
   Verknüpfung. Ein Dialog kann kein Navigationsziel sein. Am billigsten zu beobachten, am klarsten
   in der Folge.
6. **`TodoListViewModel` überschreitet ~600 Zeilen** oder bekommt ein viertes Thema. Darunter ist die
   Aufteilung eine Annehmlichkeit, darüber die billigere von zwei Umbauten.
7. **Die App bekommt aus einem anderen Grund Navigation** (Einstellungen, Partner-Verwaltung,
   Statistik). Dann gibt es Präzedenz für `BackHandler` und gespeicherten Zustand, und der Editor
   kostet *ein* Ziel statt *das erste*.

## Konsequenzen

- **Die Frage ist beantwortet und wird nicht jede Phase neu verhandelt.** Wer sie wieder aufwirft,
  nennt einen der sieben Auslöser — oder eine neue Tatsache, die dann hier dazukommt.
- **[ADR 0012](0012-scaffold-pro-bildschirm.md) und [ADR 0004](0004-manuelle-dependency-injection-ohne-hilt.md)
  erlauben den Bildschirm bereits** (Scaffold je Bildschirm ist die Regel, manuelle DI trägt bis etwa
  fünf ViewModels). Beide können später **nicht** als Grund dagegen zitiert werden; die Kosten liegen
  woanders, nämlich bei den drei Kopplungen und den Tests.
- **Wer den Bildschirm baut, entwirft zwei fremde Entscheidungen mit:** die Rückgängig-Snackbar aus
  ADR 0031 (sie zeigt sich dort, wo die Zeile verschwindet — nicht auf dem Editor) und das
  vollständige `Todo` beim Verschieben aus ADR 0024.
- **Der ROADMAP-Punkt „ViewModel aufteilen" ist damit entblockt.** Er stand unter dem Vorbehalt, erst
  die Bildschirm-Frage zu entscheiden; er kann jetzt für sich angefasst werden, nimmt dann aber kein
  Drittel mit, sondern muss innerhalb eines ViewModels sortieren.
- Solange es ein Dialog ist, bleiben Zurück-Taste, Prozesstod und Zustandswiederherstellung
  **kostenlos**. Ab dem Bildschirm sind alle drei Handarbeit ohne Vorbild im Repo — das gehört in die
  Schätzung, wenn ein Auslöser greift.

## Alternativen

- **Jetzt bauen.** Oben quantifiziert: 38 angefasste Tests, drei neu entworfene Kopplungen und zwei
  neue Mechanismen gegen ein Scrollen. Verworfen — nicht weil es falsch wäre, sondern weil das
  Verhältnis zu den 15 Zeilen, die die Mängel wirklich gekostet haben, nicht stimmt.
- **Die Frage offen lassen.** Verworfen: Sie hat schon zweimal Prosa gekostet, und die Argumente sind
  dabei gewandert — die `ROADMAP.md` trug zuletzt eine „hier stand einmal"-Korrektur zu genau diesem
  Punkt. Eine offene Frage ohne Kriterium wird bei jeder Berührung neu bewertet.
- **`ModalBottomSheet` als Mittelweg.** Mehr Platz, keine Navigation, und die Tastatur ist gelöst.
  Verworfen für jetzt: Es wäre eine dritte Behälter-Konvention neben Dialog und Bildschirm, und es
  beantwortet keinen der sieben Auslöser — die Nummern 1, 2, 3 und 4 träfen genauso zu.
