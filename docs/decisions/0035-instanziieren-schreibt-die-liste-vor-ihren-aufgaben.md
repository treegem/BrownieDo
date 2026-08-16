# 0035 – Instanziieren schreibt die Liste vor ihren Aufgaben

**Status:** akzeptiert · **Datum:** 2026-08-16

Korrigiert einen Punkt aus [ADR 0034](0034-vorlagen-sind-listen-mit-einem-flag.md). Die
Kernentscheidung dort — eine Vorlage ist eine Liste mit einem Flag — bleibt unberührt; abgelöst wird
allein die Festlegung, das Instanziieren in **einem** `WriteBatch` zu schreiben.

## Kontext

ADR 0034 hat für „Liste aus Vorlage" einen einzigen Batch vorgesehen: Listen-Dokument und alle
Aufgaben zusammen, damit es keine halb angelegte Liste gibt. Auf dem Gerät kam dabei **keine Liste
an** — der Dialog schloss sich, es erschien keine Fehlermeldung, und unter „Listen" stand nichts
Neues.

Der Grund ist eine Asymmetrie in den Security Rules, die beim Bauen übersehen wurde:

- Die Regel auf `todos` lautet `allow read, write: if isListMember(listId)`, und `isListMember` macht
  ein `get(/databases/$(database)/documents/lists/$(listId)).data.members`.
- **Ein `get()` in den Rules liest den Stand vor dem Commit und sieht die übrigen Schreibvorgänge
  desselben Batches nicht.** Beim Anlegen existiert `lists/{neueId}` dort also noch nicht, `get()`
  liefert null, und der Zugriff auf `.data` lässt die Regelauswertung scheitern.
- Ein `WriteBatch` ist atomar: Wird eine Operation abgelehnt, scheitert der ganze Batch — das
  Listen-Dokument mit.

Sichtbar wurde davon nichts, und auch das hat einen benennbaren Grund: Firestore wendet den Batch
zuerst lokal an, die App springt in die neue Liste — und rollt alles zurück, sobald der Server
ablehnt. Weil der Commit nach [ADR 0011](0011-schreibvorgaenge-nicht-abwarten.md) nicht abgewartet
wird, kommt die Ablehnung nirgends an.

**Der eigentliche Lerninhalt ist die Asymmetrie zwischen Anlegen und Löschen.**
[ADR 0019](0019-schreibrechte-auf-listen-dokumente.md) hält für `deleteList` fest, dass der
Vor-Commit-Stand der Grund ist, warum ein gemeinsamer Batch *funktioniert*: Das Listen-Dokument steht
beim `get()` noch da, obwohl derselbe Batch es entfernt. ADR 0034 hat dieses Muster gespiegelt und
die Begründung mitgenommen — aber beim Anlegen ist genau derselbe Stand der Grund, warum es
*scheitert*. Eine Begründung überlebt die Spiegelung nicht automatisch.

## Entscheidung

**Zwei Schreibvorgänge in fester Reihenfolge: erst das Listen-Dokument allein, dann die Aufgaben in
einem gemeinsamen Batch.** Keiner von beiden wird abgewartet.

Das trägt, weil Firestores lokale Mutations-Warteschlange FIFO ist und der Write-Stream die
Reihenfolge erhält: Wenn die Aufgaben ausgewertet werden, existiert die Liste. Das gilt auch, wenn
beide Schreibvorgänge offline entstehen und erst beim Wiederverbinden rausgehen — **das Instanziieren
bleibt also offline-fähig**, was der Grund war, überhaupt nicht auf den Server zu warten.

Die Aufgaben bleiben unter sich in einem Batch. Sie hängen an keiner Regel, die etwas nachschlägt,
was derselbe Batch erst anlegt.

## Konsequenzen

- **Die Atomarität ist weg, und das ist der Preis.** Scheitert der zweite Schreibvorgang, bleibt eine
  leere Liste stehen. Das ist ein sichtbarer Zustand, den man löschen oder füllen kann — dieselbe
  Abwägung, die ADR 0019 für die Aufteilung von `deleteList` in mehrere Batches schon getroffen hat
  („ein Zustand, der sich schlicht wiederholen lässt"). Die umgekehrte Richtung, Aufgaben ohne Liste,
  kann bauartbedingt nicht entstehen: Die Liste geht zuerst raus.
- **An `firestore.rules` ändert sich nichts, es bleibt bei keinem Schritt in der Firebase Console.**
  Die Konsequenz aus ADR 0034 gilt unverändert — der Fehler lag nie in den Regeln, sondern in der
  Annahme darüber, was sie sehen.
- **Kein Unit-Test kann das absichern.** Die Attrappe im `TodoListViewModelTest` kennt keine Security
  Rules, und alle Tests blieben grün, während der Weg auf dem Gerät nicht funktionierte. Eine
  Regelprüfung bräuchte die Firebase-Emulator-Suite; `ROADMAP.md` schließt ein CLI-Setup für zwei
  Nutzer aus. **Der Geräteblick ist hier kein Feinschliff, sondern der einzige Test** — und die
  Offline-Runde ist der Teil, der die Reihenfolge-Annahme wirklich prüft.
- **Eine Regel für künftige Batches**, die über zwei Ebenen gehen: Prüfen, ob eine Regel im selben
  Batch etwas nachschlägt, das dieser Batch erst anlegt. Löschen darf zusammen, Anlegen nicht.

## Alternativen

- **Das Anlegen der Liste `await`en, dann die Aufgaben schreiben.** Garantiert die Reihenfolge, ohne
  sich auf die Mutations-Warteschlange zu verlassen, und wäre die naheliegende Antwort. Verworfen,
  weil ein Firestore-Schreib-Task erst nach Server-Bestätigung abschließt: Offline hinge der Dialog
  für immer, ohne Rückmeldung. Genau diese Falle hat `createList` heute schon — siehe den offenen
  Punkt dazu in `ROADMAP.md`.
- **Die Security Rules lockern**, sodass Aufgaben unter einer noch nicht existierenden Liste erlaubt
  sind. Das wäre ein echtes Loch: Beliebige Dokumente ließen sich unter erfundenen Listen-ids
  ablegen, unerreichbar für die Auswahl und unlöschbar über die App. Dazu ein Schritt von Hand in der
  Console. Die Atomarität ist das nicht wert.
- **Die Aufgaben erst schreiben, nachdem der Listen-Snapshot sie bestätigt hat.** Wäre korrekt und
  bräuchte keine Annahme über die Reihenfolge, führt aber einen Zustandsautomaten für einen Vorgang
  ein, der zwei Zeilen lang sein sollte — und offline gäbe es keinen Server-Snapshot, auf den man
  warten könnte.
