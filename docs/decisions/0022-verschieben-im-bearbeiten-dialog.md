# 0022 – Verschieben im Bearbeiten-Dialog statt per Wischgeste

**Status:** akzeptiert · **Datum:** 2026-08-11

## Kontext

Aufgaben sollen sich zwischen Listen verschieben lassen (Phase 10 der `ROADMAP.md`), Ziel ist jede
Liste, in deren `members` die eigene uid steht. Offen war, worüber man das auslöst.

Naheliegend war die Wischgeste: Seit [ADR 0016](0016-wischen-loescht-nur-erledigte-aufgaben.md)
lassen sich **nur erledigte** Aufgaben wischen, offene sind über `gesturesEnabled` gar nicht erst
beweglich. Auf offenen Zeilen liegt also eine unbenutzte Geste. Verschieben ist außerdem umkehrbar —
falsch verschoben, schiebt man zurück —, der Schutzgedanke von ADR 0016 spricht nicht dagegen.

## Entscheidung

**Verschieben lebt im Bearbeiten-Dialog**, zusammen mit dem Ändern des Titels und dem Löschen. Die
Wischgeste bleibt unangetastet: rechts, nur auf erledigten Aufgaben, löschen.

Ausschlaggebend war, dass ein Wisch die Aktion gar nicht abschließen kann. Löschen ist am Ende der
Bewegung fertig; Verschieben braucht ein Ziel und damit eine Auswahl. Der Wisch würde also nur
fragen statt zu tun, und beim Abbrechen müsste die Zeile aktiv in ihren Ausgangszustand zurückgeholt
werden — derselbe schwebende Zustand, den ADR 0016 bei der Rückgängig-Snackbar als „zu viel
Maschinerie" verworfen hat. Der Auswahldialog wird ohnehin gebraucht; sobald er existiert, wäre eine
Geste eine Abkürzung und nicht der Mechanismus.

Dazu kommt: Die Geste ist mit TalkBack nicht ausführbar (ADR 0016). Verschieben bräuchte also so
oder so einen Weg über den Dialog, genau wie Löschen ihn behalten hat.

**Eine Geste ist zurückgestellt, nicht verworfen.** Zeigt der Alltag, dass Verschieben häufig genug
vorkommt, kommt sie als **Wischen nach links für jede Aufgabe** dazu — offen wie erledigt. Dann
bleibt die *Richtung* das Erkennungsmerkmal statt des Zustands der Zeile. Das braucht einen eigenen
ADR, weil es ADR 0016 erweitert.

## Konsequenzen

- Phase 10 wird kleiner: kein zweiter Auslöser, kein Zurücksetzen der Wischzeile, keine zweite
  Bedeutung für dieselbe Geste.
- Verschieben ist von Anfang an mit TalkBack bedienbar.
- Der Bearbeiten-Dialog wächst von zwei auf drei Aufgaben (Titel, Liste, Löschen). Wird er dadurch
  unübersichtlich, ist das der nächste Anlass, ihn zu überarbeiten.
- Verschieben kostet weiterhin mehrere Schritte — Zeile antippen, Ziel wählen, bestätigen. Das ist
  bewusst in Kauf genommen, weil es selten passiert. Für das häufige Aufräumen bleibt die Geste.
- ADR 0016 gilt unverändert weiter. Diese Entscheidung ändert nichts an der Wischgeste, sie
  verzichtet nur darauf, sie zu überladen.

## Alternativen

- **Wischen nach rechts auf offenen Aufgaben verschiebt** (der ursprüngliche Vorschlag). Nutzt die
  freie Geste und lässt den Schutz aus ADR 0016 intakt, hat aber drei Nachteile: Der Wisch schließt
  die Aktion nicht ab (siehe oben). Der Ausgang hängt am Zustand der Zeile, womit „was sich ziehen
  lässt, ist erledigt" als spürbarer Unterschied verloren geht — er wäre nur noch am
  Wisch-Hintergrund *während* der Bewegung ablesbar. Und die 85-%-Schwelle aus ADR 0016 gibt es,
  weil Löschen endgültig ist; für eine umkehrbare Aktion ist sie unnötig sperrig, zwei verschiedene
  Schwellen in derselben Geste wären schwer zu begründen.
- **Sofort Wischen nach links für jede Aufgabe.** Die sauberere Geste, weil die Richtung das
  Merkmal bleibt — aber sie löst dasselbe Abschluss-Problem nicht und kommt zu einem Zeitpunkt, an
  dem niemand weiß, ob Verschieben die prominenteste Geste der App verdient. Zurückgestellt statt
  verworfen.
- **Eigener Eintrag im Überlauf-Menü der Zeile.** Bräuchte ein Menü, das es pro Zeile noch nicht
  gibt, und stellt neben den Bearbeiten-Dialog einen zweiten Ort für dieselbe Sorte Änderung.
