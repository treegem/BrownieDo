# 0025 – Titel und Priorität in einem Schreibvorgang

**Status:** akzeptiert · **Datum:** 2026-08-11

## Kontext

Mit Phase 9 ändert der Bearbeiten-Dialog nicht mehr nur den Titel einer Aufgabe, sondern auch ihre
Priorität. Damit stellt sich die Frage, was beim Speichern passiert.

Bisher schreibt das Repository gezielt einzelne Felder. Das ist kein Zufall, sondern die
Umsetzung der Konfliktstrategie aus
[ADR 0006](0006-server-zeitstempel-fuer-last-write-wins.md): Last-Write-Wins **auf Feldebene**. Wer
gerade den Titel ändert, überschreibt nicht den Erledigt-Zustand, den der Partner im selben Moment
setzt — die beiden Schreibvorgänge treffen verschiedene Felder und gehen aneinander vorbei.

Für zwei Felder, die derselbe Dialog bearbeitet, gibt es zwei Wege: beide zusammen schreiben, oder
vor dem Schreiben mit der aktuellen Aufgabe vergleichen und nur das tatsächlich Geänderte losschicken.

## Entscheidung

**Ein Speichern ist ein Schreibvorgang.** `TodoRepository.updateTodo` schreibt Titel und Priorität
zusammen in einer Map, dazu `updatedAt`. Die frühere Methode `setTitle` gibt es nicht mehr — sie
wäre eine Lüge, sobald sie auch die Priorität schreibt.

Der Ausschlag gab die Bedeutung der Geste: Der Dialog zeigt beide Werte an, und wer „Speichern"
tippt, bestätigt damit beide. Ein Vergleich mit dem aktuellen Stand würde das Verhalten von etwas
abhängig machen, das der Nutzer im Dialog gar nicht sieht.

## Konsequenzen

- Ein Speichern kostet einen Schreibvorgang statt zwei, und es gibt genau einen Fehlerpfad. Schlägt
  er fehl, bleibt der Dialog mit beidem offen.
- **Die Feldebene wird für dieses eine Paar aufgegeben.** Konkret: Beide öffnen denselben Eintrag.
  A setzt „hoch" und speichert. B korrigiert danach einen Tippfehler und speichert — B's Dialog
  wurde vor A's Änderung gefüllt und trägt die alte Priorität, A's Änderung ist damit still weg.
  Vorher konnte diese Abfolge nichts verlieren.
- Die Lücke ist so groß wie die Zeit, die ein Dialog offen steht, und sie trifft nur zwei Personen,
  die dieselbe Aufgabe gleichzeitig bearbeiten. Sollte das je vorkommen, ist der Ausweg bekannt und
  klein: vor dem Schreiben mit `uiState.todos` vergleichen und nur die geänderten Felder schicken.
- Der Rest der Schreibvorgänge bleibt feldweise. `setDone` schreibt weiterhin nur `done`,
  `completedBy` und `completedAt` — wer gleichzeitig den Titel ändert, verliert nichts.
- Der Kommentar an `setDone` in `FirestoreTodoRepository`, der die Feldebene erklärt, nennt diese
  Ausnahme jetzt ausdrücklich.

## Alternativen

- **Nur das Geänderte schreiben:** Behielte die Feldebene vollständig. Das ViewModel müsste beim
  Speichern die aktuelle Aufgabe aus `uiState.todos` heraussuchen, zwei Felder vergleichen und je
  nach Ergebnis keinen, einen oder zwei Schreibvorgänge auslösen — samt der Frage, was gilt, wenn
  der eine gelingt und der andere nicht. Das ist mehr Maschinerie, als der Schaden wiegt, und
  widerspricht „Einfachheit vor Vollständigkeit" aus der `ROADMAP.md`. Bewusst verworfen, mit dem
  oben beschriebenen Verlust als Preis.
- **`setTitle` behalten und `setPriority` daneben stellen:** Zwei Methoden, die der Dialog beide
  nacheinander aufruft. Erhält die Feldebene ebenfalls, verdoppelt aber die Schreibvorgänge bei
  jedem Speichern — auch wenn sich nur eines von beiden geändert hat — und lässt offen, was
  passieren soll, wenn der zweite Aufruf scheitert.
- **Das ganze Dokument schreiben:** Am einfachsten zu verstehen, würde aber auch `done`,
  `completedBy` und `completedAt` mit veralteten Werten überschreiben. Damit wäre Last-Write-Wins
  auf Feldebene ganz aufgegeben, nicht nur für ein Paar.
