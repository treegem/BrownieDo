# 0006 – Server-Zeitstempel für Last-Write-Wins

**Status:** akzeptiert · **Datum:** 2026-08-07

## Kontext

Der Kern von BrownieDo ist asynchrone Synchronisation: Einer kann offline eine Aufgabe abhaken,
und sobald wieder Verbindung besteht, werden die Stände zusammengeführt. Als Konfliktstrategie
ist Last-Write-Wins auf Feldebene über `updatedAt` festgelegt.

Damit entscheidet die Herkunft dieses Zeitstempels, wer bei einem Konflikt gewinnt:

- **Client-Zeit** (Uhr des Handys): Erhält die tatsächliche Reihenfolge der Handlungen — hängt
  aber davon ab, dass beide Geräteuhren übereinstimmen. Eine falsch gestellte Uhr würde dazu
  führen, dass ein Gerät dauerhaft jeden Konflikt gewinnt oder verliert.
- **Server-Zeit** (`@ServerTimestamp`): Firestore setzt den Wert, wenn der Schreibvorgang beim
  Server ankommt. Eine einzige Uhr für beide Geräte, keine Drift.

## Entscheidung

`createdAt` und `updatedAt` werden von Firestore per `@ServerTimestamp` gesetzt. Die App schreibt
diese Felder nie selbst.

Beim Lesen kommt `ServerTimestampBehavior.ESTIMATE` zum Einsatz, damit lokal erzeugte Einträge
schon vor der Synchronisation einen brauchbaren Zeitwert haben.

## Konsequenzen

- Kein Konflikt hängt mehr von der Uhrzeiteinstellung eines Handys ab.
- **Die Reihenfolge der Handlungen bleibt nicht erhalten.** Wer offline um 10:00 abhakt und um
  11:00 synchronisiert, gewinnt gegen eine Änderung, die online um 10:30 erfolgte. Für zwei
  Personen, die selten dieselbe Aufgabe gleichzeitig ändern, ist das vertretbar — und die Regel
  „zuletzt synchronisiert gewinnt" lässt sich verständlich erklären.
- Zwischen Schreiben und Bestätigung durch den Server sind die Felder im Dokument leer. Deshalb
  sind sie in `TodoDocument` nullable, und ohne `ESTIMATE` würde ein offline erzeugter Eintrag
  ohne Zeitstempel dastehen.
- `TodoMapper` verwirft Dokumente ohne Zeitstempel, statt einen Ersatzwert zu erfinden. Ein
  fehlender Zeitstempel bedeutet, dass das Dokument nicht von dieser App stammt.

## Alternativen

- **Client-Zeit:** Erhält die Handlungsreihenfolge, verlagert die Korrektheit aber auf die
  Geräteuhren. Nicht überprüfbar und im Fehlerfall schwer zu diagnostizieren.
- **Beides speichern** (Client- und Serverzeit): Erlaubte feinere Auflösung, verdoppelt aber die
  Felder und verlangt eine Regel, wann welcher Wert zählt. Das wäre der Einstieg in genau die
  Komplexität, die die Roadmap mit dem Verzicht auf CRDTs vermeiden will.
