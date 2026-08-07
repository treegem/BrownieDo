# 0010 – Sortierung im Client statt `orderBy`

**Status:** akzeptiert · **Datum:** 2026-08-07

## Kontext

Die Aufgabenliste soll die neuesten Einträge oben zeigen, also absteigend nach `createdAt`.
Der naheliegende Weg wäre eine Firestore-Query mit `orderBy("createdAt", DESCENDING)`.

Das kollidiert mit ADR 0006: `createdAt` und `updatedAt` sind `@ServerTimestamp`-Felder. Zwischen
dem Schreiben und der Bestätigung durch den Server steht in dem Feld **kein Wert**. Firestore
liefert bei einer Query nur Dokumente, die das sortierte Feld auch besitzen — ein gerade
angelegter Eintrag würde also aus dem Ergebnis herausfallen.

Für BrownieDo ist das kein Randfall, sondern der Normalfall: Wer im Supermarkt ohne Empfang eine
Aufgabe hinzufügt, sähe sie erst nach der Synchronisation. Genau das Verhalten, das die App
vermeiden soll.

## Entscheidung

Die Query auf `lists/{listId}/todos` läuft **ohne `orderBy`**. Das Repository sortiert die bereits
abgebildeten `Todo`-Objekte absteigend nach `createdAt`.

Gelesen wird mit `ServerTimestampBehavior.ESTIMATE`, damit ein noch nicht bestätigter Eintrag
einen lokalen Schätzwert hat und dadurch oben einsortiert wird.

## Konsequenzen

- Ein offline angelegter Eintrag erscheint sofort an der richtigen Stelle.
- Die Sortierung ist erst nach dem Mapping möglich, also wird immer die vollständige Liste geladen.
  Serverseitiges Paginieren wäre so nicht umsetzbar. Für eine Haushaltsliste von zwei Personen ist
  das unkritisch; sollte die Liste je groß werden, ist das der Punkt, an dem neu entschieden wird.
- Es wird kein zusammengesetzter Firestore-Index gebraucht.
- Sobald weitere Sortierungen dazukommen (Phase 6: offen oben, erledigt unten), passieren auch die
  im Client — an einer Stelle, statt verteilt über Query und UI.

## Alternativen

- **Client-Zeit für `createdAt`:** Das Feld hätte sofort einen Wert und `orderBy` würde
  funktionieren. Widerspricht aber ADR 0006 und macht die Reihenfolge von der Geräteuhr abhängig.
- **Zusätzliches Sortierfeld mit Client-Zeit** neben dem Server-`createdAt`: Erlaubt `orderBy` und
  hält die Konfliktlösung sauber, verdoppelt aber die Zeitfelder und wirft die Frage auf, was gilt,
  wenn beide auseinanderlaufen. Zwei Wahrheiten für dieselbe Information.
- **`orderBy` beibehalten und den fehlenden Eintrag hinnehmen:** Verletzt den Kern der App —
  asynchrones Arbeiten ohne Empfang.
