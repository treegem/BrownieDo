# 0030 – Die Notiz erscheint als gekürzte zweite Zeile, nicht als Symbol

**Status:** akzeptiert · **Datum:** 2026-08-12

## Kontext

Phase 12 hängt eine optionale Notiz an eine Aufgabe: Ein Backlog-Eintrag lebt Wochen, und bis zur
wöchentlichen Besprechung hat der Titel allein oft verloren, was gemeint war („Fenster abdichten" —
welche, und was war der Plan?).

`ROADMAP.md` gab dafür eine Anforderung und ließ die Umsetzung offen: „In der Zeile nur andeuten,
nicht ausbreiten — ein Symbol oder eine gekürzte zweite Zeile (`supportingContent` von `ListItem`).
Die Liste bleibt eine Liste."

## Entscheidung

**Die Notiz steht als `supportingContent` unter dem Titel, einzeilig mit Auslassungspunkten**
(`maxLines = 1`, `overflow = TextOverflow.Ellipsis`). Nur Zeilen mit Notiz werden dadurch höher.

Ausschlaggebend war der Zweck der Notiz: Sie soll sagen, **was** gemeint war. Ein Symbol sagt nur,
**dass** es etwas gibt — in der wöchentlichen Besprechung müsste man dann jeden markierten Eintrag
einzeln öffnen, also genau das tun, was die Notiz ersparen soll.

Dazu kommen zwei Dinge, die man dem Ergebnis nicht ansieht:

- **Der `trailingContent`-Slot ist besetzt.** Dort sitzt seit Phase 9 der Prioritäts-Pfeil. Ein
  Notiz-Symbol müsste sich den Platz mit ihm teilen, also eine `Row` aus zwei Symbolen bilden —
  mehr Bauteile für weniger Aussage.
- **Keine eigene Farbe am inneren `Text`.** `ListItem` färbt den Slot bereits auf
  `onSurfaceVariant`; eine Farbe am Text würde davon überschrieben. Dieselbe Falle hält der
  Kommentar an `headlineColor` in `TodoRow` schon fest.

**Auch erledigte Aufgaben zeigen ihre Notiz.** Das spart eine Regel und einen Test, und der
abgeblendete `supportingContent` passt ohnehin zum durchgestrichenen Titel.

## Konsequenzen

- Die Zeilenhöhe ist nicht mehr einheitlich. Das ist der Preis und war die eigentliche Frage: Eine
  Liste aus zwei Zeilenhöhen liest sich unruhiger, dafür muss man zum Verstehen nichts antippen.
- Eine Notiz mit Zeilenumbrüchen wird in der Zeile zu einer Zeile zusammengezogen. Gewollt — der
  ganze Text steht im Bearbeiten-Dialog.
- Die Liste bleibt eine Liste: Die Notiz wächst nicht über eine Zeile hinaus, egal wie lang sie ist.
- Für TalkBack ergibt sich daraus von selbst das Richtige: `ListItem` liest Kopf- und Stützzeile
  zusammen vor, es braucht keine `contentDescription` — anders als beim Prioritäts-Pfeil, dessen
  Bedeutung allein in der Form steckt.

## Alternativen

- **Ein Notiz-Symbol am Zeilenende.** Hält alle Zeilen gleich hoch, sagt aber nur, dass es etwas
  gibt, und teilt sich den Platz mit dem Prioritäts-Pfeil. Verworfen — es verlagert die Arbeit in
  genau den Moment, in dem die Notiz helfen soll.
- **Symbol *und* zweite Zeile.** Das Symbol sagt nichts, was die Zeile nicht schon zeigt, und der
  geteilte `trailingContent` käme trotzdem dazu. Verworfen.
- **Die Notiz mehrzeilig in der Zeile zeigen.** Dann wäre die Liste keine Liste mehr, sondern eine
  Sammlung von Karten unterschiedlicher Höhe — und das Wischen auf erledigten Aufgaben
  ([ADR 0016](0016-wischen-loescht-nur-erledigte-aufgaben.md)) träfe unterschiedlich große Ziele.
  Verworfen.
- **Nur bei offenen Aufgaben zeigen.** Hielte den erledigten Block kompakt, kostet aber eine Regel
  im Zeilen-Composable und einen Test, der sie festhält — für einen Block, der unten steht und
  ohnehin weggewischt wird. Verworfen.
