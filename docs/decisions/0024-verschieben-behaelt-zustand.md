# 0024 – Verschieben behält alle Felder außer der Liste

**Status:** akzeptiert · **Datum:** 2026-08-11

## Kontext

Firestore kennt kein Verschieben eines Dokuments zwischen Sub-Collections. Eine Aufgabe von einer
Liste in eine andere zu verschieben (Phase 10 der `ROADMAP.md`) heißt technisch: in der
Ziel-Sub-Collection neu anlegen und in der Quelle löschen, in einem `WriteBatch`. Damit stellt sich
die Frage, welche Felder dabei übernommen werden und welche wie bei einer Neuanlage entstehen.

`updatedAt` wird davon nicht berührt — es ist `@ServerTimestamp`
([ADR 0006](0006-server-zeitstempel-fuer-last-write-wins.md)) und bekommt beim Schreiben des neuen
Dokuments ohnehin einen neuen Wert. Offen war der Rest: `createdAt`, der Erledigt-Zustand
(`isDone`), `completedBy` und — seit Phase 9, siehe
[ADR 0023](0023-prioritaet-migration-und-sortierung.md) — auch `priority` und `completedAt`. Die
Dokument-id ist rein technisch und ergibt sich aus dem Anlegen automatisch neu; daran hängt keine
fachliche Bedeutung.

## Entscheidung

**Alle fachlichen Felder wandern unverändert mit**, also `createdAt`, `isDone`, `completedBy`,
`priority` und `completedAt`. Verschieben ist eine organisatorische Aktion — die Aufgabe wechselt
die Liste, in der sie geführt wird, sie bleibt aber dieselbe Aufgabe. Wann sie angelegt wurde, wie
dringend sie ist, ob sie erledigt ist und wann sie abgehakt wurde, ändert sich dadurch nicht.

Praktisch heißt das: Kommt ein Feld zu `Todo` dazu, wandert es mit, solange es die Aufgabe
beschreibt und nicht ihren Ort. Neu ist beim Verschieben nur, was technisch neu entsteht — die
Dokument-id und `updatedAt`.

## Konsequenzen

- Die Sortierung nach `createdAt` (ADR 0010, erweitert um Priorität in
  [ADR 0023](0023-prioritaet-migration-und-sortierung.md)) bleibt nach dem Verschieben stabil — der
  Eintrag springt nicht ans Ende der Zielliste, nur weil er dort neu geschrieben wurde.
- Eine erledigte Aufgabe bleibt erledigt, wenn sie verschoben wird — inklusive `completedBy` und
  `completedAt`. Wer sie abgehakt hat und wann, bleibt nachvollziehbar, auch über einen
  Listenwechsel hinweg, und sie sitzt in der Zielliste an derselben Stelle im erledigten Block.
- Eine dringende Aufgabe bleibt dringend. Würde `priority` beim Verschieben zurückfallen, sänke der
  Eintrag in der Zielliste unbemerkt nach unten.
- Das Verschieben ändert an den Daten nichts außer der Liste, in der die Aufgabe liegt, und
  `updatedAt`. Damit ist es die einzige Operation im Repository, die ein Dokument unverändert an
  einen neuen Ort kopiert, statt eines seiner Felder zu ändern.
- Der `WriteBatch` schreibt das komplette `TodoDocument` in die Zielliste, nicht nur einzelne
  Felder — anders als `setDone`/`updateTodo`, die gezielt einzelne Felder aktualisieren.
- **Diese Liste wächst mit `Todo` mit.** Wer dem Modell ein Feld hinzufügt, muss es hier
  mitnehmen; ein vergessenes Feld fällt beim Verschieben still auf seinen Standardwert zurück.

## Alternativen

- **Erledigt-Zustand und `completedBy` beim Verschieben zurücksetzen:** Ließe sich als „die Aufgabe
  fängt in der neuen Liste neu an" lesen, widerspricht aber der Erwartung, dass Verschieben reine
  Organisation ist. Eine bereits erledigte Aufgabe würde in der Zielliste plötzlich wieder als offen
  erscheinen, ohne dass irgendjemand daran etwas geändert hat. Verworfen.
- **`createdAt` auf den Verschiebezeitpunkt setzen:** Würde `createdAt` zum „seit wann in dieser
  Liste" machen statt zum „seit wann existiert diese Aufgabe" — verändert die Bedeutung des Felds
  gegenüber ADR 0006 und würde die Sortierung nach Erstellungsdatum durch das Verschieben ändern.
  Verworfen.
- **Die Priorität beim Verschieben auf „mittel" zurücksetzen:** Ließe sich damit begründen, dass
  Dringlichkeit im Zusammenhang einer Liste gilt. Für zwei Personen mit einer Handvoll Listen ist
  das gedankliche Feinarbeit ohne praktischen Gewinn — und der Eintrag verschwände nach dem
  Verschieben unbemerkt nach unten. Verworfen.
