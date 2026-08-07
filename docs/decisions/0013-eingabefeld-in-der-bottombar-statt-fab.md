# 0013 – Eingabefeld in der `bottomBar` statt FAB mit Dialog

**Status:** akzeptiert · **Datum:** 2026-08-07

## Kontext

Neue Aufgaben wurden bisher über eine Eingabezeile **über** der Liste angelegt. Beim
Material-3-Feinschliff stellte sich die Frage, wohin diese Zeile gehört. Das Standardmuster von
Material 3 für „etwas anlegen" ist ein FloatingActionButton unten rechts, der einen Dialog öffnet —
genau so, wie in BrownieDo bereits das Bearbeiten funktioniert.

Der typische Einsatz von BrownieDo spricht dagegen: Es ist eine Einkaufs- und Haushaltsliste. Wer
sie füllt, tippt selten einen einzelnen Eintrag, sondern mehrere hintereinander. Ein Dialog kostet
dabei pro Eintrag zwei zusätzliche Taps und je eine Ein- und Ausblendanimation.

## Entscheidung

Das Eingabefeld bleibt dauerhaft sichtbar und wandert als `bottomBar` an den unteren Bildschirmrand,
zusammen mit einem Hinzufügen-Button. Die IME-Aktion des Feldes ist `Done` und legt den Eintrag an,
ohne die Tastatur zu schließen — mehrere Aufgaben lassen sich also in einem Rutsch tippen.

Die Leiste polstert sich selbst gegen `WindowInsets.safeDrawing` (Seitenmaximum aus Systemleisten,
Tastatur und Display-Aussparung) und ist damit die einzige Stelle im Bildschirm, die sich um den
unteren Inset kümmert.

## Konsequenzen

- Keine Dialog-Runde pro Eintrag, und das Feld liegt am Daumen statt am oberen Bildschirmrand.
- Die Liste bekommt den gesamten Platz zwischen den beiden Leisten und scrollt hinter ihnen durch.
- **Der FAB-Platz unten rechts ist damit belegt.** Künftige Primäraktionen müssen in die TopAppBar —
  dort landet ohnehin die Listen-Auswahl aus Phase 8.
- Die Leiste ist immer sichtbar und kostet dauerhaft Höhe, auch wenn gerade nichts angelegt wird.
- Das Tastatur-Verhalten des ganzen Bildschirms hängt an dieser einen Leiste. Wer sie anfasst, muss
  die Insets erneut auf einem Gerät prüfen.

## Alternativen

- **FAB plus `AlertDialog`:** Das Material-3-Standardmuster und symmetrisch zum Bearbeiten-Dialog.
  Zwei Taps mehr pro Eintrag und eine zweite Tastatur-Oberfläche, die richtig sitzen muss.
- **Eingabezeile oben lassen:** Der kleinste Eingriff, aber auf einem großen Galaxy ist der obere
  Bildschirmrand die schlechteste Stelle für ein Feld, das oft benutzt wird.
