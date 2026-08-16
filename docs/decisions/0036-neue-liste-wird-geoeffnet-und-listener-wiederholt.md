# 0036 – Jeder Anlegeweg öffnet, was er angelegt hat – und was das am Listener nötig macht

**Status:** akzeptiert · **Datum:** 2026-08-16

Löst einen Punkt aus [ADR 0034](0034-vorlagen-sind-listen-mit-einem-flag.md) ab („Die neue Liste wird
geöffnet — als einziger Anlegeweg"). Alles Übrige dort bleibt gültig.

## Kontext

Zwei Beobachtungen auf dem Gerät, die zunächst nichts miteinander zu tun zu haben scheinen:

1. **Nach dem Anlegen einer Vorlage landet man nicht in ihr.** Man muss sie im Menü heraussuchen —
   obwohl man sie gerade angelegt hat, um sie zu füllen. ADR 0034 hatte das so entschieden („wer eine
   leere Liste anlegt, will oft noch nicht hinein"); im Alltag trägt die Begründung nicht.
2. **Nach dem Instanziieren erscheint manchmal „Die Liste konnte nicht geladen werden."** — während
   die Liste samt aller Einträge sichtbar danebensteht. Ein Wechsel hin und zurück räumt die Meldung
   ab.

Der zweite Punkt hat eine Ursache, die den ersten blockiert. Nach dem Anlegen springt die App sofort
in die neue Liste und hängt einen Snapshot-Listener an `lists/{neueId}/todos`. Dessen **Leseregel**
schlägt über `isListMember` die Mitglieder des Listen-Dokuments nach — und das ist zwar lokal schon
geschrieben, auf dem Server aber vielleicht noch nicht angekommen
([ADR 0011](0011-schreibvorgaenge-nicht-abwarten.md) wartet bewusst nicht). Der Server weist den
Listener dann mit `PERMISSION_DENIED` ab. Die Einträge sind trotzdem zu sehen, weil sie aus dem
lokalen Cache kommen; die Meldung ist schlicht falsch.

Das „manchmal" ist der Wettlauf: Ist die Bestätigung schneller als der Listener, passiert nichts.

**Und es ist nicht bloß kosmetisch.** Firestore baut einen abgewiesenen Listener ab. Bis man
wegwechselt und zurückkommt, aktualisiert sich die Liste nicht mehr — genau deshalb ist
`LOAD_FAILED` klebrig gebaut. Der falsche Alarm beschreibt also einen echten toten Listener.

Damit hängen die beiden Punkte zusammen: Würde man das Öffnen einfach auf alle Anlegewege ausdehnen,
träte der Fehlalarm auch beim Anlegen einer Liste und einer Vorlage auf — dort sogar auf einem leeren
Bildschirm, wo er noch weniger zu deuten wäre.

## Entscheidung

**Erstens: Ein abgewiesener Listener wird begrenzt neu aufgebaut, bevor daraus ein Fehler wird.**
`FirestoreTodoRepository.todos` meldet einen Listener-Fehler jetzt als Abbruch des Flows, wiederholt
ihn zweimal mit steigendem Abstand (700 ms, 1400 ms) und gibt erst danach `Result.failure` heraus.
Der Weg über einen Abbruch ist nötig, damit `retryWhen` überhaupt greifen kann — ein
`Result.failure` als Emission wäre für den Operator ein ganz normaler Wert.

**Zweitens: Jeder Anlegeweg öffnet, was er angelegt hat.** Neue Liste, neue Vorlage und Liste aus
einer Vorlage verhalten sich gleich. Dafür gibt `createList` die id der neuen Liste zurück, die —
wie beim Instanziieren — lokal von `document()` vergeben wird statt von `add()` samt
Server-Bestätigung.

## Konsequenzen

- **`createList` wartet nicht mehr auf den Server und funktioniert damit offline.** Das war vorher
  ein stiller Widerspruch zu ADR 0011: `add(...).await()` schließt erst nach Server-Bestätigung ab,
  im Flugmodus hing der Dialog also ohne Rückmeldung. `deleteList` hat dieselbe Falle weiterhin, dort
  wird sie separat geführt.
- **Die drei Anlegewege sind im ViewModel zu einem Pfad zusammengelaufen**, weil sie sich jetzt bis
  auf den Repository-Aufruf gleichen. `confirmListFromTemplate` ist entfallen.
- **Die Klebrigkeit von `LOAD_FAILED` bleibt richtig und wird sogar schärfer.** Vorher konnte die
  Meldung einen Listener meinen, der gleich von selbst wieder gutgegangen wäre; jetzt bedeutet sie,
  was sie sagt — nach drei Versuchen ist der Listener endgültig ab.
- **Ein echter Rechte-Fehler wird rund zwei Sekunden später gemeldet.** Nimmt der Partner einen aus
  einer Liste heraus, dauert die Meldung entsprechend länger. Für zwei Nutzer ist das kein Preis, der
  ins Gewicht fällt.
- **Kein Unit-Test deckt das ab**, aus demselben Grund wie bei
  [ADR 0035](0035-instanziieren-schreibt-die-liste-vor-ihren-aufgaben.md): Die Attrappen kennen keine
  Security Rules, und `FirestoreTodoRepository` braucht Firestore. Geprüft wird auf dem Gerät — der
  Fehlalarm trat ohnehin nur „manchmal" auf, ein einzelner grüner Durchlauf beweist also wenig;
  mehrmals hintereinander instanziieren ist die ehrlichere Probe.

## Alternativen

- **Auf die Server-Bestätigung des Listen-Dokuments warten, bevor die neue Liste geöffnet wird.**
  Beseitigt den Wettlauf an der Wurzel und bräuchte kein Wiederholen. Verworfen, weil ein
  Firestore-Schreib-Task offline nie abschließt: Der Dialog hinge im Flugmodus für immer — dieselbe
  Falle, die dieser ADR bei `createList` gerade beseitigt.
- **Den Fehler nach dem Anlegen für ein paar Sekunden unterdrücken.** Billiger, aber es kaschiert
  genau den Zustand, den `LOAD_FAILED` melden soll: einen toten Listener. Der Fehlalarm wäre weg, die
  nicht mehr aktualisierende Liste bliebe.
- **Die Leseregel auf `todos` lockern**, sodass sie kein Listen-Dokument braucht. Verworfen aus
  denselben Gründen wie in ADR 0035 — es wäre ein echtes Loch, dazu ein Schritt von Hand in der
  Firebase Console.
- **Nur Vorlagen öffnen, Listen nicht.** Genau das war gefragt, wurde aber verworfen: Anlegen
  verhielte sich je nach Art unterschiedlich, ohne dass jemand den Unterschied erklären könnte.
