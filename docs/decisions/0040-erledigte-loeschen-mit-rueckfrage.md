# 0040 – Erledigte löschen mit Rückfrage: die Anzahl entscheidet, nicht die Art

**Status:** akzeptiert · **Datum:** 2026-08-16

## Kontext

Nach einer Woche steht unter jeder Liste ein Haufen abgehakter Zeilen. Wegräumen ging bisher nur
einzeln — wischen oder Bearbeiten-Dialog, je Eintrag —, und das passt nicht zu einem wöchentlichen
Ritual. „Erledigte löschen" räumt sie in einem Zug weg.

Damit stellt sich eine Frage, auf die das Projekt schon **zwei** Antworten gegeben hat, die einander
zu widersprechen scheinen:

- Eine **einzelne** Aufgabe zu löschen kostet keine Rückfrage. Das Netz spannt sich danach auf, als
  „Rückgängig" in einer Snackbar ([ADR 0031](0031-rueckgaengig-statt-rueckfrage-beim-loeschen.md)).
- Eine **ganze Liste** zu löschen bekommt einen Bestätigungsdialog mit der Anzahl der enthaltenen
  Aufgaben und hat kein Rückgängig. `DeleteListDialog` sagt in seinem KDoc, warum: „Die Anzahl steht
  bewusst im Text — sie ist die Information, die die Folge greifbar macht."

Gelöscht werden hier **Aufgaben**, was für ADR 0031 spräche. Es sind aber **viele auf einmal**, was
für den Listen-Dialog spricht.

## Entscheidung

**Ein Bestätigungsdialog mit der Anzahl, kein Rückgängig.** Damit ist die Trennlinie ausgesprochen,
die vorher nur implizit dalag:

> Entscheidend ist die **Anzahl** der betroffenen Einträge, nicht ihre Art. Eine Aufgabe →
> Rückgängig. Viele auf einmal → Rückfrage mit der Zahl.

Das ordnet auch die zwei bestehenden Fälle: Das Löschen einer Liste ist kein Sonderfall, weil es
eine *Liste* ist, sondern weil alle ihre Aufgaben mitgehen.

Der Dialog trägt dieselbe Form wie „Liste löschen?" — Überschrift mit Fragezeichen, Anzahl im Text,
gefüllter Bestätigungsknopf in `error`/`onError`
([ADR 0032](0032-gefuellte-bestaetigung-und-loeschen-im-inhalt.md)). Beide teilen sich dafür jetzt
ein privates `DestructiveConfirmButton`, damit die Begründung an einer Stelle steht.

**Der Menüeintrag erscheint nur, wenn es Erledigtes gibt**, und in einer Vorlage nie — dort wird
nicht abgehakt ([ADR 0034](0034-vorlagen-sind-listen-mit-einem-flag.md)). Das ist dieselbe Logik, mit
der „Umbenennen" und „Löschen" ohne offene Liste verschwinden.

## Konsequenzen

- **Das Rückgängig aus ADR 0031 bleibt unangetastet.** Ein `deletedTodos: List<Todo>` hätte den
  Einzelslot im UiState, das Repository und sieben Unit-Tests plus einen instrumentierten mitgezogen —
  für eine Aktion, die zwei Tipps hinter einem Menü liegt.
- **Ein offenes „Rückgängig" verfällt beim Aufräumen.** Sonst holte es eine Aufgabe zurück, die
  gerade mit weggeräumt wurde. Das ist die eine Stelle, an der die zwei Löschwege sich berühren, und
  sie hat einen eigenen Test.
- Ein eigener Fehlerwert `DELETE_FINISHED_FAILED`. `DELETE_FAILED` steht im Singular („Die Aufgabe
  konnte nicht gelöscht werden.") und wäre für einen Schwung schlicht falsch. Wo die Meldung passt,
  wird weiterhin wiederverwendet — das Sortieren von Hand meldet seinen Fehlschlag als
  `UPDATE_FAILED` (ADR 0039).
- `deleteTodos` ist **nicht suspend**, anders als `ListRepository.deleteList`: Dort muss erst
  nachgeschlagen werden, welche Aufgaben es gibt, hier kommen die ids aus dem Snapshot. Damit bleibt
  der Weg bei [ADR 0011](0011-schreibvorgaenge-nicht-abwarten.md) und funktioniert offline.
- Gestückelt zu 500 wie in `deleteList`, das genau dieselbe Operation schon so macht. Anders als beim
  Instanziieren aus einer Vorlage ist die Grenze hier erreichbar — eine Liste, die ein Jahr nicht
  aufgeräumt wurde, hat mehr als 500 abgehakte Zeilen, und genau die will man dann wegräumen.
- Keine Bestätigungs-Snackbar danach. Die Zeilen blenden sichtbar aus (800 ms, ADR 0031), und der
  Dialog hat vorher gesagt, wie viele es sind.
- **Kein Schritt in der Firebase Console und keine Regeländerung** — es werden Dokumente gelöscht,
  für die `allow read, write: if isListMember(listId)` ohnehin gilt.

## Alternativen

- **Rückgängig für den ganzen Schwung.** Konsequenter zu ADR 0031 und ohne Extra-Tipp im Ritual. Der
  Preis ist der Umbau des Einzelslots samt seiner getesteten Maschinerie — und ein „Rückgängig" für
  vierzig Einträge ist eine Behauptung, die man erst glaubt, wenn man sie geprüft hat.
- **Beides: Dialog *und* Rückgängig.** Was man baut, wenn man sich nicht entscheiden will. Zwei
  Bremsen für eine Aktion, die die am wenigsten wertvollen Daten der App entfernt.
- **Den Eintrag dauerhaft und abgeblendet zeigen.** Besser auffindbar, aber das Projekt hat genau
  diese Frage schon einmal andersherum entschieden: Ein deaktivierter Menüeintrag „sähe aus wie eine
  Aktion, die gerade nicht geht" (Kommentar an `SectionLabel`).
- **Erledigte automatisch nach N Tagen aufräumen.** Bräuchte einen Auslöser (WorkManager) und führte
  zu genau dem Hintergrundweg, den [ADR 0027](0027-termine-per-kalender-intent.md) vermeidet. Und es
  nähme eine Entscheidung ab, die zum Ritual gehört.
