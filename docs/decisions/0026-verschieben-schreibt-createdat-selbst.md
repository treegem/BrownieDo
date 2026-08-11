# 0026 – Verschieben schreibt `createdAt` selbst und gibt die Feldebene auf

**Status:** akzeptiert · **Datum:** 2026-08-11

## Kontext

[ADR 0024](0024-verschieben-behaelt-zustand.md) legt fest, dass beim Verschieben alle fachlichen
Felder mitwandern und nur die Dokument-id und `updatedAt` neu entstehen. Bei der Umsetzung ist
aufgefallen, dass diese Entscheidung zwei Dinge nach sich zieht, die dort nicht stehen und die
älteren ADRs widersprechen.

**Erstens:** [ADR 0006](0006-server-zeitstempel-fuer-last-write-wins.md) sagt wörtlich „Die App
schreibt diese Felder nie selbst." Beim Verschieben stimmt das nicht mehr — `createdAt` kommt aus
dem Domänenobjekt und wird als Wert ins neue Dokument geschrieben, damit es erhalten bleibt.

**Zweitens:** Firestore kennt kein Verschieben. Anlegen plus Löschen bedeutet, dass alles, was
zwischenzeitlich am Quelldokument passiert, mit ihm verschwindet.

## Entscheidung

Beides wird hingenommen und hier festgehalten, statt es zu verhindern. Das folgt dem Muster von
[ADR 0019](0019-schreibrechte-auf-listen-dokumente.md), der ADR 0011 auf dieselbe Weise
eingeschränkt hat, ohne ihn aufzuheben.

**`createdAt` wird beim Verschieben von der App geschrieben.** Technisch geht das ohne Zutun auf:
`@ServerTimestamp` ersetzt beim Schreiben genau die Felder, die `null` sind. `Todo.toDocument()`
setzt `createdAt` also auf den vorhandenen Wert und lässt `updatedAt` leer — der Anlagezeitpunkt
reist mit, der Änderungszeitpunkt kommt vom Server.

**Ein Verschieben gibt die Feldebene für diesen einen Eintrag auf.** Wer die Aufgabe verschiebt,
schreibt den Stand, den sein Gerät zuletzt gesehen hat, an den neuen Ort.

## Konsequenzen

- **Ein offline angelegter und vor dem Synchronisieren verschobener Eintrag behält eine geschätzte
  Erstellungszeit.** Gelesen wird mit `ServerTimestampBehavior.ESTIMATE` (ADR 0010), solange der
  Anlage-Schreibvorgang noch nicht bestätigt ist, steht in `Todo.createdAt` also eine Schätzung aus
  der Geräteuhr. Verschiebt man in diesem Moment, wird die Schätzung zum endgültigen Wert.
  Betroffen ist ausschließlich die Sortierung innerhalb einer Prioritätsstufe
  ([ADR 0023](0023-prioritaet-migration-und-sortierung.md)) — die Konfliktlösung nicht, denn
  `updatedAt` bleibt Server-Zeit. Eine falsch gestellte Uhr verschiebt einen Eintrag um ein paar
  Plätze, sie lässt kein Gerät jeden Konflikt gewinnen.
- **Erkannt wird das bewusst nicht.** Ob ein Zeitstempel geschätzt ist, weiß nur der Snapshot
  (`hasPendingWrites`). `Todo` müsste dafür einen Marker „das ist geschätzt" tragen, den sonst
  niemand liest, nur um in einem seltenen Moment das Verschieben zu verweigern. Das ist mehr
  Maschinerie als der Schaden wiegt.
- **`createdAt` verliert beim Verschieben an Auflösung.** `TodoDocument` hält den Zeitstempel als
  `java.util.Date`, also in Millisekunden; Firestore speichert Mikrosekunden. Gelesen wurde ohnehin
  schon gerundet, aber nach einem Verschieben steht der gerundete Wert auch im Dokument. Zwei
  Aufgaben derselben Millisekunde könnten danach gleich sortieren.
- **Was der Partner am Quelldokument ändert, während der Batch in der Warteschlange liegt, ist
  weg.** Hakt er die Aufgabe ab, während das verschiebende Gerät offline ist, trifft sein
  Schreibvorgang das Dokument, das derselbe Batch löscht. Die Kopie trägt den Erledigt-Zustand von
  dem Stand, den der Verschiebende zuletzt gesehen hat.
  [ADR 0025](0025-titel-und-prioritaet-in-einem-schreibvorgang.md) hat die Feldebene für ein
  Feldpaar aufgegeben, hier fällt sie für den ganzen Eintrag. Für zwei Personen, von denen eine
  gerade aufräumt, ist das vertretbar.
- Der Fall ist so eng wie die Zeit zwischen dem Verschieben und der nächsten Synchronisation. Wer
  ihn je schließen will, braucht ein `get()` auf das Quelldokument — damit müsste `moveTodo`
  suspenden und würde ADR 0011 brechen.

## Alternativen

- **`createdAt` beim Verschieben leer lassen und den Server setzen lassen:** Hielte ADR 0006 buchstäblich
  ein und wäre eine Zeile weniger. Verstößt aber gegen ADR 0024 — der Eintrag spränge in der
  Zielliste an die Spitze, als wäre er gerade angelegt worden. Verworfen; die Entscheidung, welcher
  ADR nachgibt, fällt hier zugunsten des sichtbaren Verhaltens.
- **Das Verschieben verweigern, solange Schreibvorgänge der Aufgabe noch ausstehen:** Verhinderte
  den geschätzten Zeitstempel und den verlorenen Erledigt-Zustand. Kostet einen Marker auf `Todo`,
  eine Fehlermeldung, die niemand versteht („warum kann ich das jetzt nicht verschieben?"), und
  bricht das Versprechen, dass die App offline vollständig bedienbar ist. Verworfen.
- **Vor dem Verschieben das Quelldokument lesen:** Beseitigte den Verlust am Quelldokument, machte
  `moveTodo` aber suspend und damit vom Server abhängig — offline würde das Verschieben hängen,
  statt zu funktionieren. Verworfen, siehe ADR 0011.
