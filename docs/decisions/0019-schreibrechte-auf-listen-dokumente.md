# 0019 – Schreibrechte auf Listen-Dokumente

**Status:** akzeptiert · **Datum:** 2026-08-11

## Kontext

Bis Phase 8b verbot `firestore.rules` jedes Schreiben auf `lists/{listId}` mit `write: if false`.
Das war kein Versehen, sondern eine Zusicherung: Solange Listen-Dokumente ausschließlich von Hand in
der Console entstehen, kann sich niemand selbst in eine fremde Liste eintragen
([ADR 0009](0009-listen-dokument-mit-todo-subcollection.md)). Der Preis war, dass jede neue Liste
Handarbeit blieb.

8b löst das ein. Dabei sind zwei Dinge zu klären: was die Regeln künftig erlauben, und wie eine
Liste samt ihrer Aufgaben verschwindet — Firestore löscht nicht kaskadierend.

Der zweite Punkt ist heikler, als er aussieht. Die Regel für die Sub-Collection liest die
Mitgliedschaft per `get()` aus dem übergeordneten Listen-Dokument. Wird das **zuerst** gelöscht,
findet der `get()` nichts, die Regel verweigert, und die Todos darunter sind **unwiderruflich nicht
mehr löschbar** — verwaiste Daten, an die niemand mehr herankommt.

## Entscheidung

`write: if false` wird durch drei getrennte Regeln ersetzt:

- **`create`** verlangt, dass die eigene uid in `request.resource.data.members` steht (bei einem
  create existiert `resource` noch nicht), höchstens zwei Mitglieder, und einen nicht leeren Namen.
- **`update`** verlangt Mitgliedschaft **und** dass `members` unverändert bleibt.
- **`delete`** verlangt Mitgliedschaft.

Die Trennung ist der Punkt: **Umbenennen ja, die Mitgliederliste ändern nein.** Damit bleibt genau
die Zusicherung erhalten, die `write: if false` gegeben hat — niemand kann sich eintragen oder den
Partner hinauswerfen. Aus privat wird nicht nachträglich geteilt; dafür legt man eine neue Liste an.

Die Obergrenze `members.size() <= 2` gießt die Produktentscheidung „eine App für genau zwei
Personen" aus der `ROADMAP.md` in die Regel. Der Namens-Check hält Listen fern, die der Client als
unbrauchbar verwirft und die dann unsichtbar in Firestore lägen.

**Gelöscht wird in einem `WriteBatch`.** Alle Todo-Löschungen und das Listen-Dokument liegen in
derselben Operation. Regeln werten jede Schreibung eines Batches gegen den Stand **vor** dem Commit
aus — `isListMember` findet das Listen-Dokument also noch, obwohl derselbe Batch es entfernt. Die
Reihenfolge-Falle entfällt damit vollständig, und es kann kein Zwischenzustand entstehen.

Ein Batch fasst höchstens 500 Operationen. Bei mehr Aufgaben wird in Blöcken von 499 gelöscht, und
das Listen-Dokument kommt in den **letzten** Block. Bricht ein früherer ab, sind ein paar Aufgaben
weg und die Liste steht noch — ein Zustand, den ein zweiter Versuch aufräumt. Nie andersherum.

`createList` und `deleteList` sind damit die ersten Repository-Methoden, die **auf den Server
warten**. Das schränkt [ADR 0011](0011-schreibvorgaenge-nicht-abwarten.md) ein, hebt es aber nicht
auf: `createList` muss den Partner nachschlagen, `deleteList` braucht die Sub-Collection, bevor es
sie löschen kann. Alle übrigen Schreibvorgänge — auch `renameList` — laufen weiter ohne Warten.

## Konsequenzen

- Listen entstehen und verschwinden in der App; die Console wird dafür nicht mehr gebraucht.
- **Aus einer privaten Liste wird nie eine geteilte.** Das ist die Kehrseite der `update`-Regel und
  die häufigste Frage, die sie auslösen wird.
- Löschen ist endgültig und nimmt alle Aufgaben mit, auch beim Partner. Deshalb bekommt es einen
  eigenen Bestätigungsdialog mit der Anzahl der betroffenen Aufgaben — anders als das Löschen einer
  einzelnen Aufgabe, siehe [ADR 0016](0016-wischen-loescht-nur-erledigte-aufgaben.md).
- Kommt je eine dritte Person dazu, ist `members.size() <= 2` die Zeile, die man anfassen muss.
- Zwei Repository-Methoden warten jetzt auf den Server und können damit hängen, wenn das Netz
  schlecht ist. Bei `deleteList` ist das unvermeidbar; die Oberfläche zeigt den Fehler.
- Die Regeln werden weiterhin **von Hand** über die Firebase Console veröffentlicht — vor dem ersten
  Schreibversuch, sonst scheitert alles mit `PERMISSION_DENIED` und man sucht im Code.

## Alternativen

- **Todos einzeln und ungeordnet löschen:** Der einfachste Code. Genau der Weg in die verwaisten
  Daten, sobald das Listen-Dokument zuerst dran ist oder der Vorgang mittendrin abbricht.
- **Erst alle Todos awaiten, dann das Listen-Dokument:** Vermeidet die Falle ebenfalls und braucht
  keine Annahme über die Batch-Semantik. Dafür nicht atomar — ein Abbruch dazwischen lässt eine
  leere Liste stehen. Bleibt der Rückfall, falls sich die Batch-Auswertung anders verhält als hier
  angenommen.
- **Cloud Function fürs kaskadierende Löschen:** Der von Firebase empfohlene Weg für große
  Sub-Collections. Es gibt im Projekt kein Functions-Setup, und die `ROADMAP.md` schließt
  Betriebsaufwand ausdrücklich aus.
- **Soft-Delete über ein `deletedAt`-Feld:** Machte Löschen umkehrbar. ADR 0009 sieht das Feld nicht
  vor, und jede Query müsste es fortan filtern — für zwei Personen zu viel Apparat.
- **`update` ganz verbieten und Umbenennen auslassen:** Wäre die kleinste Regeländerung. Eine Liste,
  deren Name ein Tippfehler ist, müsste dann gelöscht und neu angelegt werden — samt Aufgaben.
