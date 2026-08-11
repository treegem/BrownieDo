# 0023 – Priorität: Migration bestehender Aufgaben und Einfluss auf die Sortierung

**Status:** akzeptiert · **Datum:** 2026-08-11

## Kontext

Phase 9 der `ROADMAP.md` fügt Aufgaben eine Priorität hinzu (niedrig · mittel · hoch, Standard
mittel). Zwei Fragen daran betreffen bestehendes Verhalten und mussten vor der Umsetzung geklärt
sein:

1. Aufgaben, die vor diesem Feld angelegt wurden, haben das Feld nicht. Wie liest die App das?
2. `TODO_ORDER` sortiert bisher offen vor erledigt, je neueste zuerst
   ([ADR 0010](0010-sortierung-im-client-statt-orderby.md)). Ändert die Priorität diese Reihenfolge?

Bei Frage 2 kam dazu eine dritte, unabhängig von der Priorität: Erledigte Aufgaben sortieren
bisher nach `createdAt` — also danach, wann sie *angelegt* wurden, nicht wann sie *erledigt*
wurden. Für den Block der erledigten Aufgaben ist das die falsche Zeit; gewünscht ist, wann
abgehakt wurde.

`updatedAt` scheidet dafür aus, obwohl es beim Abhaken per `FieldValue.serverTimestamp()` gesetzt
wird ([`FirestoreTodoRepository.setDone`](../../app/src/main/java/eu/sweetgeorgie/browniedo/data/todo/FirestoreTodoRepository.kt)):
Es wird von *jeder* Feldänderung überschrieben, auch von `setTitle`. Wird eine bereits erledigte
Aufgabe nachträglich umbenannt, würde sie in der „wann erledigt"-Sortierung nach oben springen,
ohne dass sich am Erledigt-Zustand etwas geändert hat.

## Entscheidung

**Migration:** Ein fehlendes Prioritätsfeld wird beim Lesen als „mittel" ausgelegt, nicht durch
einen einmaligen Schreibvorgang in der Console nachgezogen. Das deckt nicht nur die heute
bestehenden Dokumente ab, sondern auch den laufenden Fall, dass eines der beiden Geräte die App
noch nicht aktualisiert hat und weiterhin Dokumente ohne das Feld schreibt — ein einmaliger
Konsolen-Fix würde das nicht auffangen.

**Sortierung:** Die Priorität wird zum primären Sortierkriterium innerhalb der offenen Aufgaben.
Die neue Reihenfolge ist:

1. Erledigt vs. offen (wie bisher: offen oben)
2. Innerhalb der offenen Aufgaben: Priorität (hoch → mittel → niedrig), dann `createdAt` absteigend
3. Innerhalb der erledigten Aufgaben: **neu** — der Zeitpunkt, wann sie erledigt wurden, absteigend
   (zuletzt abgehakt oben), statt wie bisher `createdAt`

Erledigte Aufgaben werden von der Priorität nicht umsortiert — sie bleiben als Block unter den
offenen. Die Priorität wird dort weiterhin angezeigt, sie wirkt sich nur nicht auf die Reihenfolge
aus, weil abgehakte Einträge ohnehin aus dem Weg sollen.

Damit der Erledigungszeitpunkt zuverlässig ist, bekommt `Todo` ein eigenes Feld `completedAt`
(nullable, analog zu `completedBy`). Es wird nur beim Abhaken gesetzt und beim Wiederöffnen wieder
auf `null` gesetzt — ein reiner Verwendungswechsel von `updatedAt` genügt nicht, siehe Kontext.

## Konsequenzen

- `TODO_ORDER` aus [ADR 0010](0010-sortierung-im-client-statt-orderby.md) bekommt pro Zweig ein
  eigenes Sortierkriterium: offene Aufgaben nach Priorität dann `createdAt`, erledigte Aufgaben
  nach `completedAt`. Das Grundprinzip aus ADR 0010 — Sortierung im Client statt per `orderBy` —
  bleibt unverändert; diese Entscheidung erweitert nur, wonach dort sortiert wird.
- Ein Eintrag kann beim Ändern der Priorität innerhalb der offenen Aufgaben nach oben oder unten
  wandern. Das ist beabsichtigt: Wer eine Aufgabe auf „hoch" setzt, will sie oben sehen.
- Kein Mapper-Sonderfall für alte Dokumente nötig — die fehlende Priorität fällt beim Lesen auf
  „mittel" zurück, an derselben Stelle, an der auch der Wert für die Anzeige feststeht.
- `completedAt` ist ein weiteres nullable Feld auf `Todo`/`TodoDocument`, gesetzt und gelöscht
  zusammen mit `completedBy` in `setDone`. Bestehende erledigte Aufgaben haben es nicht — dieselbe
  Migrationsfrage wie bei der Priorität, nur mit anderem Rückfallwert: Ohne `completedAt` sortiert
  ein alter Eintrag ans Ende des erledigten Blocks statt nach oben zu springen, damit nicht so
  aussieht, als wäre er gerade eben abgehakt worden.
- Unit-Tests für `TODO_ORDER` brauchen Fälle für: gemischte Prioritäten unter den offenen Aufgaben,
  gemischte `completedAt`-Zeitpunkte unter den erledigten, und erledigte Aufgaben ohne
  `completedAt`.

## Alternativen

- **Bestehende Dokumente einmalig in der Console nachziehen:** Explizit und ohne Rückfallwert im
  Code, löst das Problem aber nicht dauerhaft, solange beide Geräte nicht gleichzeitig aktualisiert
  sind — ein Gerät mit alter App-Version schreibt weiterhin Dokumente ohne das Feld. Verworfen.
- **Priorität nur anzeigen, Sortierung unverändert lassen:** Einfacher und ADR 0010 bliebe
  unangetastet, verfehlt aber den Zweck des Features — eine Aufgabe auf „hoch" zu setzen, ändert
  dann nichts an ihrer Position, und die drei Stufen wären reine Deko. Verworfen.
- **Priorität auch bei erledigten Aufgaben sortierungsrelevant machen:** Konsequent zu Ende gedacht,
  aber erledigte Aufgaben sollen aus dem Weg sein, nicht nach Priorität geordnet wieder Aufmerksamkeit
  verlangen. Verworfen.
- **`updatedAt` als Erledigungszeitpunkt verwenden, statt ein neues Feld einzuführen:** Kein
  zusätzliches Feld nötig, aber `updatedAt` gehört gleichermaßen dem Titel — eine nachträgliche
  Korrektur des Titels einer bereits erledigten Aufgabe würde sie in der Sortierung nach oben
  reißen, ohne dass sich am Erledigt-Zustand etwas geändert hat. Verworfen.
- **Erledigte Aufgaben weiterhin nach `createdAt` sortieren:** Kein neues Feld, aber sortiert nach
  einer Zeit, die mit „gerade abgehakt" nichts zu tun hat — eine alte, früh angelegte Aufgabe bliebe
  auch nach dem Abhaken am Ende des erledigten Blocks stehen. Verworfen, das war der ursprüngliche
  Stand dieses ADRs vor der Präzisierung.
