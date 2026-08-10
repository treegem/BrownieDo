# 0016 – Wischen löscht nur erledigte Aufgaben

**Status:** akzeptiert · **Datum:** 2026-08-10

## Kontext

Löschen ging bisher nur über den Bearbeiten-Dialog: Zeile antippen, Dialog abwarten, „Löschen"
tippen. Für den häufigsten Fall — die Einkaufsliste nach dem Einkauf aufräumen — sind das drei
Schritte pro Eintrag, und der Dialog ist dabei jedes Mal im Weg.

Das Plattform-Standardmuster dafür ist eine Wischgeste. Sie hat aber einen Preis, der hier schwerer
wiegt als anderswo: BrownieDo hat keine Papierkorb-Funktion, und Firestore kennt kein
Wiederherstellen. Was gewischt ist, ist weg — und zwar sofort auch auf dem Gerät des Partners.

Eine offene Aufgabe ist genau die Information, wegen der die App überhaupt existiert. Sie durch eine
Handbewegung zu verlieren, während man mit dem Handy in der einen Hand durch den Supermarkt läuft,
ist ein anderer Schaden als das Löschen von etwas, das ohnehin schon abgehakt ist.

## Entscheidung

Die Wischgeste unterscheidet zwischen Aufräumen und Wegwerfen:

- **Nur erledigte Aufgaben lassen sich wischen.** Offene Einträge sind über `gesturesEnabled` gar
  nicht erst beweglich. Was sich ziehen lässt, ist erledigt — der Unterschied ist damit spürbar
  statt erklärungsbedürftig.
- **Nur nach rechts** (`SwipeToDismissBoxValue.StartToEnd`). Die linke Richtung bleibt bewusst frei.
- **Die Schwelle liegt bei 85 % der Zeilenbreite** statt beim Material-Standard von 50 %. Ein
  Streifen im Vorbeiscrollen soll nichts auslösen; die Geste muss gewollt sein.
- **Kein Rückgängig.** Dafür die hohe Schwelle.
- **Die Regel steht zusätzlich im ViewModel.** `onTodoSwipedAway` prüft selbst auf `isDone` und tut
  sonst nichts, obwohl die Oberfläche das bereits verhindert.

Der Bearbeiten-Dialog behält seinen Löschen-Knopf. Die Geste kommt dazu, sie ersetzt nichts.

## Konsequenzen

- Aufräumen kostet eine Bewegung statt drei Schritte, und offene Aufgaben sind sicherer als vorher —
  sie waren über den Dialog ja ebenso löschbar.
- **Löschen hat jetzt zwei Einstiege**, die zusammen gepflegt werden müssen: `onDeleteTodoClick`
  (Dialog, schließt ihn) und `onTodoSwipedAway` (Geste, kein Dialog). Sie teilen sich bewusst keine
  Hilfsfunktion, weil sie sich im Erfolgsfall wirklich unterscheiden.
- **Die Geste ist mit TalkBack nicht ausführbar.** Deshalb bleibt der Dialog der barrierefreie Weg
  zum Löschen, und der Wisch-Hintergrund trägt bewusst keine `contentDescription` — er erscheint nur
  während einer Geste, die dort niemand macht.
- Die Regel im ViewModel ist ohne Gerät testbar. Die Geste selbst braucht einen instrumentierten
  Test und damit ein Gerät; die *Regel* dahinter tut das nicht.
- Schlägt der Schreibvorgang fehl, bleibt der Eintrag in der Liste. Die weggewischte Zeile muss dann
  aktiv zurückgeholt werden, sonst klafft an ihrer Stelle eine leere Fläche.
- Gelöscht ist endgültig. Wer sich vertut, tippt den Eintrag neu — bei einer Einkaufsliste
  verschmerzbar, bei anderen Inhalten wäre diese Entscheidung falsch.

## Alternativen

- **Wischen löscht jeden Eintrag,** wie in den meisten Listen-Apps. Erwartungskonform und ohne
  Sonderfall im Code — verliert aber genau den Schutz, um den es hier geht. Ausdrücklich verworfen.
- **Rückgängig-Snackbar mit verzögertem Schreiben:** Der Eintrag verschwindet sofort aus der Liste,
  gelöscht wird erst nach einigen Sekunden. Das echte Sicherheitsnetz, kostet aber schwebende
  Löschungen samt Timer und Abbruch im ViewModel — und wenn die App im Zeitfenster geschlossen wird,
  passiert am Ende gar nichts. Zu viel Maschinerie für einen Griff, der nur abgehakte Einträge
  trifft; widerspricht „Einfachheit vor Vollständigkeit" aus der `ROADMAP.md`.
- **Standardschwelle von 50 %:** Flüssiger zu bedienen, aber ohne Rückgängig zu leicht aus Versehen
  ausgelöst.
- **Wischen nach links:** Kollidiert mit der verbreiteten Erwartung, dass links „zurück" bedeutet.
- **Die `isDone`-Prüfung nur in der Oberfläche lassen:** Weniger Code, aber die eigentliche Regel
  hinge dann an einem Composable-Parameter und wäre nur mit Gerät prüfbar.
