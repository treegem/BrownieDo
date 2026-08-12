# 0031 – Rückgängig statt Rückfrage beim Löschen einer Aufgabe

**Status:** akzeptiert · **Datum:** 2026-08-12

## Kontext

Der Best-Practice-Durchgang nach Phase 12 hat einen Widerspruch in der eigenen Logik gefunden:
**Löschen war der bequemste und gleichzeitig der ungeschützteste Weg der App.**

- Wischen löscht nur *erledigte* Aufgaben und verlangt 85 % der Zeilenbreite, ausdrücklich weil
  „gelöscht ist endgültig" ([ADR 0016](0016-wischen-loescht-nur-erledigte-aufgaben.md)).
- Eine *Liste* zu löschen hat einen Bestätigungsdialog, der die Anzahl der Aufgaben nennt
  ([ADR 0019](0019-schreibrechte-auf-listen-dokumente.md)).
- Eine *Aufgabe* aus dem Bearbeiten-Dialog zu löschen kostete dagegen **einen Tipp ohne Rückfrage**,
  direkt neben „Speichern" — und zwar für offene Aufgaben genauso wie für erledigte.

Der Schutz aus ADR 0016 galt damit der Geste, nicht der Aufgabe. Genau der Eintrag, den ADR 0016
für unantastbar erklärt („die Information, wegen der die App überhaupt existiert"), war über den
Dialog mit einem Fehlgriff weg — auf beiden Geräten, sofort.

`ROADMAP.md` stellte zwei Auflösungen zur Wahl: **(a)** eine Rückgängig-Snackbar oder **(b)** ein
Bestätigungsschritt wie bei Listen.

## Entscheidung

**Gelöscht wird weiterhin sofort und ohne Rückfrage — danach erscheint eine Snackbar „Aufgabe
gelöscht" mit der Aktion „Rückgängig".** Das gilt für **beide** Löschwege, den Dialog-Knopf und die
Wischgeste.

Ausschlaggebend war, dass eine Rückfrage den häufigen Fall bestraft, um den seltenen abzusichern:
Aufräumen ist in dieser App ein wöchentliches Ritual, ein Fehlgriff die Ausnahme. Eine Rückfrage
verlangt bei *jedem* Löschen einen zusätzlichen Tipp und bringt für den Fehlgriff nur einen
Aufmerksamkeitsmoment — dieselbe Rechnung, die schon ADR 0016 zur Wischgeste geführt hat.

Fünf Festlegungen im Einzelnen:

- **Wiederhergestellt wird unter der alten Dokument-id.** `restoreTodo` schreibt mit `set()` auf
  denselben Pfad, den das Löschen geleert hat. Damit ist „rückgängig" die Wahrheit und nicht ein
  neuer Eintrag, der dem alten gleicht. Möglich ist das nur, weil die id bekannt bleibt: Sie steht
  im aufbewahrten `Todo`.
- **Sofort schreiben, nicht verzögert.** Es gibt keine schwebende Löschung und keinen Timer — genau
  die Maschinerie, die ADR 0016 als „zu viel für einen Griff, der nur abgehakte Einträge trifft"
  verworfen hat. Der Preis dieser Umkehrung ist, dass zwischen Löschen und Rückgängig zwei
  Schreibvorgänge stehen und der Partner den Eintrag kurz verschwinden sieht.
- **Der Snapshot entscheidet, nicht der Dialog.** Zurückgelegt wird der Stand, der in Firestore
  stand. Was im offenen Dialog getippt und nicht gespeichert wurde, war nie gespeichert.
- **Ein Einzelslot, kein Stapel.** Nur die zuletzt gelöschte Aufgabe lässt sich zurückholen.
- **Das Angebot überlebt keinen Listenwechsel.** Es ist kein Rückblick, sondern eine Aktion, und die
  schriebe nach dem Wechsel in die falsche Liste. Die Verschiebe-Bestätigung bleibt dagegen stehen,
  weil sie nur etwas mitteilt.

**Die 85-%-Schwelle aus ADR 0016 bleibt vorerst unangetastet.** Sie ließe sich jetzt auf den
Material-Standard von 50 % senken, aber das ist eine eigene Entscheidung über das Gefühl der Geste
und gehört nicht in dieselbe Änderung. ADR 0016 gilt damit weiter, mit einer Ausnahme: Sein „Kein
Rückgängig. Dafür die hohe Schwelle." ist überholt — es gibt jetzt beides.

## Konsequenzen

- **Löschen ist erstmals umkehrbar**, und zwar auf beiden Wegen. Der Widerspruch aus dem Durchgang
  ist damit aufgelöst, ohne dass das Aufräumen einen Tipp teurer wird.
- **`updatedAt` ist nach dem Rückgängig neu.** Die Aufgabe kehrt an ihre alte Stelle in der Liste
  zurück, weil `createdAt` und `completedAt` mitreisen und die Sortierung nur diese beiden benutzt
  ([ADR 0023](0023-prioritaet-migration-und-sortierung.md)). Der Änderungszeitpunkt kommt vom
  Server, wie überall sonst ([ADR 0006](0006-server-zeitstempel-fuer-last-write-wins.md)).
- **`Todo.toDocument()` hat einen zweiten Aufrufer.** Der Hin-und-zurück-Test in `TodoMapperTest`
  deckt damit auch das Wiederherstellen ab, und die Ermahnung „wer `Todo` ein Feld hinzufügt, muss
  es hier mitnehmen" gilt für ein Feature mehr. Die Ausnahme aus
  [ADR 0026](0026-verschieben-schreibt-createdat-selbst.md) — die App schreibt `createdAt` selbst —
  ist nicht länger auf das Verschieben beschränkt.
- **Der Fehlerfall ist neu und gemeldet:** Scheitert das Wiederherstellen, sagt eine Snackbar das
  (`RESTORE_FAILED`), und das Angebot verfällt. Ein zweiter Versuch würde die Snackbar über sich
  selbst stapeln.
- **Zwei Löschungen in schneller Folge lassen nur die zweite zurückholen.** Das ist der Preis des
  Einzelslots und trifft genau den Aufräum-Fall. Vertretbar, weil „Rückgängig" überall den letzten
  Schritt meint — mehrere offene Angebote wären eher verwirrend.
- **Der Bildschirm hat einen fünften Actions-Halter** (`SnackbarActions`). Die Snackbar-Rückrufe
  standen bisher einzeln am Bildschirm, weil sie zu keinem der vier Bereiche gehörten
  ([ADR 0028](0028-rueckrufe-in-actions-haltern.md)); mit vier davon ist der `SnackbarHostState`
  selbst der Bereich, und der Bildschirm bleibt bei acht Parametern.
- **Offline funktioniert beides.** Löschen und Wiederherstellen sind lokale Schreibvorgänge, die
  Firestore selbst zustellt ([ADR 0011](0011-schreibvorgaenge-nicht-abwarten.md)).
- **Die alte id hat eine Nebenwirkung in der Oberfläche.** `rememberSwipeToDismissBoxState` sichert
  seinen Zustand über `rememberSaveable`, und die `LazyColumn` bewahrt den pro Item-Key auf. Eine
  weggewischte und zurückgeholte Aufgabe könnte deshalb in ihrer weggewischten Stellung erscheinen —
  als leere Fläche. `SwipeableTodoRow` setzt den Wischzustand beim Erscheinen einer id vorsorglich
  zurück; ob es nötig ist, hängt an Compose-Interna und zeigt sich erst auf dem Gerät. Dieselbe
  Falle wie beim gescheiterten Löschen (ADR 0016), nur mit umgekehrtem Vorzeichen.

## Alternativen

- **(b) Bestätigungsdialog wie bei Listen.** Billiger zu bauen und ohne neuen Schreibweg, aber ein
  Dialog über einem Dialog — und er verteuert jedes Löschen, um den Fehlgriff nur
  wahrscheinlichkeitsgemindert statt behebbar zu machen. Beim Löschen einer *Liste* bleibt die
  Rückfrage richtig: Dort hängen Aufgaben mit dran, und ein Rückgängig müsste die ganze
  Sub-Collection wiederherstellen. Für die einzelne Aufgabe verworfen.
- **Rückgängig mit verzögertem Schreiben** (Eintrag verschwindet sofort, gelöscht wird nach einigen
  Sekunden). Der Partner sähe nie ein Zucken. Kostet aber Timer, schwebenden Zustand und die Frage,
  was beim Schließen der App passiert — schon von ADR 0016 verworfen, und die Begründung gilt
  unverändert.
- **Wiederherstellen als neues Dokument** (`addTodo`-Weg, neue id). Wäre ohne die neue
  Repository-Methode gegangen, hätte aber `createdAt` verloren: Die Aufgabe wäre in der Sortierung
  nach oben gerutscht und beim Partner als anderer Eintrag angekommen. Verworfen — ein
  „Rückgängig", das den Zustand nicht wiederherstellt, ist falsch beschriftet.
- **Rückgängig nur im Dialog, nicht beim Wischen.** Hätte ADR 0016 unberührt gelassen, aber die
  Geste ist der Weg, auf dem ein Versehen wahrscheinlicher ist. Verworfen.
- **Papierkorb-Liste.** Das vollständige Sicherheitsnetz und beim Partner sichtbar, aber ein
  weiterer Ort, an dem Aufgaben liegen, plus eine Aufräumregel. Widerspricht „Einfachheit vor
  Vollständigkeit" aus `ROADMAP.md`.
