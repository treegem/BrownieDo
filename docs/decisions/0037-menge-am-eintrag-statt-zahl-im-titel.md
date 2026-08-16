# 0037 – Menge am Eintrag statt Zahl im Titel

**Status:** akzeptiert · **Datum:** 2026-08-16

## Kontext

Der eigentliche Sinn einer Vorlage für eine Reise: Man schreibt sie für **einen Tag** und
instanziiert sie für drei. Aus „1 T-Shirt" soll dabei „3 T-Shirt" werden.

Zwei Dinge machen das weniger trivial, als es klingt:

- **Skaliert wird der Text eines Eintrags, nicht die Anzahl der Einträge.** Drei Zeilen „T-Shirt"
  wären beim Abhaken lästig — und mit Kommazahlen („0,5 Rolle pro Tag") gar nicht darstellbar.
- **Nicht alles skaliert.** Drei Tage heißen drei T-Shirts, aber nicht drei Shampoo. Die App muss
  also wissen, welche Einträge mitwachsen.

Der naheliegende Weg wäre, die führende Zahl aus dem Titel zu lesen. Er ist auch der, der in der
Praxis zerbricht.

## Entscheidung

**Der Eintrag bekommt ein eigenes Feld `quantity: Double?` — und sein Vorhandensein *ist* der
Schalter.** Ein Eintrag mit Menge skaliert, einer ohne bleibt, wie er ist. Damit braucht es kein
zweites Feld „skaliert" und keinen Umschalter im Dialog; die eine Eingabe trägt beide Informationen.

Daraus folgen drei Regeln, die alle in `domain/todo/TodoQuantity.kt` stehen — reine Funktionen ohne
Android-Typ und ohne Firestore, damit jede genau einmal existiert und für sich prüfbar ist:

1. **Lesen:** Komma **und** Punkt werden angenommen. Die deutsche Tastatur liefert das Komma,
   `toDouble()` versteht nur den Punkt. Alles, was keine positive Zahl ist, gilt als „nicht lesbar".
2. **Rechnen und Schreiben:** exakt, Nachkommastellen nur wenn nötig — „3" statt „3,0", aber „1,5"
   bleibt 1,5. **Gerechnet wird über `BigDecimal`**, gerundet auf zwei Stellen: `0.1 * 3` ist in
   `Double` 0,30000000000000004, und das darf nicht in einem Aufgabentitel landen.
3. **Skalieren:** Menge mal Faktor, das Ergebnis als Präfix vor den Titel. Ein Eintrag ohne Menge
   kommt unverändert zurück.

**Die gerechnete Menge landet im Titel und nicht als Feld in der erzeugten Liste.** Was entsteht, ist
eine ganz gewöhnliche Liste ohne Sonderregeln — „aus 3 mach 2" ist dort eine Textänderung wie jede
andere. Nur die Vorlage trägt das Feld.

**Formatiert wird von Hand mit Komma statt über `NumberFormat`.** Sonst hinge die Ausgabe an der
Locale des Geräts, und ein auf Englisch gestelltes Handy schriebe „1.5" in eine deutschsprachige App.

Für die Bedienung:

- **Das Mengenfeld steht direkt unter dem Titel und nur im Vorlagen-Modus.** Es gehört zum Titel: Aus
  „T-Shirt" mit Menge 1 wird „3 T-Shirt". Der Platz dafür ist da, weil in einer Vorlage „Termin
  anlegen" wegfällt ([ADR 0034](0034-vorlagen-sind-listen-mit-einem-flag.md)) — **kein Modus des
  Dialogs trägt damit mehr Eingaben als vor Phase 14**, und Auslöser 1 aus
  [ADR 0033](0033-bearbeiten-bleibt-ein-dialog.md) greift weiterhin nicht.
- **Die Vorlagen-Zeile zeigt die Menge als Präfix**, über dieselbe Formatierung. Ohne das wäre nicht
  zu sehen, welche Einträge überhaupt mitskalieren — und das ist die eine Frage, die man an eine
  Vorlage hat.
- **Unlesbare Eingabe blendet den Bestätigen-Knopf ab**, wie heute schon ein leerer Titel. **Ein
  leeres Mengenfeld bleibt gültig** — leer heißt „skaliert nicht", das ist ja der Schalter. Beim
  Faktor ist leer dagegen nicht in Ordnung: Dort muss eine Zahl stehen.
- Der Faktor steht im Instanziieren-Dialog, Vorgabe 1. Bei Faktor 1 kommt heraus, was in der Vorlage
  steht — der Normalfall bleibt der billigste.

## Konsequenzen

- **`updateTodo` bekommt ein Wertobjekt.** Mit der Menge wären es sechs Argumente geworden, zwei
  davon `String`; benannte Argumente tragen das nicht mehr. `TodoUpdate` bündelt, was der Dialog
  besitzt. Der offene Punkt unter „Code" in Phase 13 hat genau diesen Auslöser benannt und ist damit
  eingelöst. **Nicht zu verwechseln mit `TodoEdit`** in der UI-Schicht: Das ist der Tippstand des
  Dialogs, Textpuffer auch für Zahlen; `TodoUpdate` trägt das Ergebnis in den Typen der Domäne.
- **Die Menge muss beim Verschieben und beim Rückgängig mitwandern**
  ([ADR 0024](0024-verschieben-behaelt-zustand.md)), und sie hängt an **zwei** Stellen. `toDocument()`
  ist die offensichtliche, die der Hin-und-zurück-Test absichert. Die zweite ist der Fallstrick aus
  Phase 12: `onEditConfirm` muss die Menge aus dem Dialog auf den Snapshot überschreiben, sonst reist
  beim gleichzeitigen Verschieben und Ändern die *alte* mit. Kein Mapper-Test findet das, deshalb
  gibt es dafür einen eigenen ViewModel-Test.
- **Der Puffer wird unabhängig vom Modus vorbelegt.** Nur das *Feld* im Dialog ist moduspflichtig —
  sonst löschte ein Speichern in einer Arbeitsliste eine vorhandene Menge still weg.
- **Kein Nachziehen in der Console, keine Änderung an den Security Rules.** Fehlt das Feld, ist es
  null, und das ist die richtige Antwort — dieselbe Migration wie bei der Notiz in Phase 12. Beim
  Lesen wird zusätzlich alles ≤ 0 zu null, damit „skaliert nicht" nur eine Form hat.
- **`TODO_ORDER` bleibt unberührt.** Die Menge ordnet nichts.
- **Zwei Nachkommastellen sind die Obergrenze.** Für eine Packliste reicht das, und es schneidet die
  Fließkomma-Artefakte sicher ab. Wer je mehr braucht, ändert eine Konstante.

## Alternativen

- **Die führende Zahl aus dem Titel lesen, dazu ein Schalter „skaliert".** Spart das Mengenfeld,
  scheitert aber am Parsen: „T-Shirt für 2 Tage" und „2er-Pack Socken" tragen Zahlen, die niemand
  multipliziert haben will, und ein Titel ohne Zahl bräuchte trotzdem einen Platz für die Menge. Der
  Schalter wäre außerdem genau die zusätzliche Eingabe, die dieses Feld einspart.
- **Die Menge auch in der erzeugten Liste als Feld behalten.** Erlaubte ein späteres Neu-Skalieren
  und wäre sauberer editierbar. Verworfen: Dann trüge **jede** Aufgabe der App das Feld, Zeile und
  Bearbeiten-Dialog würden überall breiter — für einen Fall, den es im Alltag nicht gibt. Eine
  erzeugte Liste wird abgehakt, nicht nachjustiert.
- **Die Anzahl der Einträge vervielfachen** statt des Textes. Drei Zeilen „T-Shirt" wären beim
  Abhaken lästig, und Kommazahlen ließen sich gar nicht abbilden — womit der Sinn („0,5 Rolle pro
  Tag") wegfiele.
- **`NumberFormat` mit deutscher Locale** statt eigener Formatierung. Bequemer und
  gruppierungsfähig, aber es hinge an der Geräte-Locale und an einer Bibliotheksentscheidung, wo eine
  Zeile Code reicht.
