# 0032 – Gefüllte Bestätigung in allen Dialogen, Löschen aus der Knopfzeile

**Status:** akzeptiert · **Datum:** 2026-08-12

## Kontext

Zwei Beobachtungen aus dem Best-Practice-Durchgang nach Phase 12, die dasselbe Bauteil betreffen:
die Knopfzeile der Dialoge.

**„Speichern" landete auf einer eigenen Zeile.** `AlertDialog` legt `dismissButton` und
`confirmButton` in eine gemeinsame `AlertDialogFlowRow`. Im Bearbeiten-Dialog steckte der
`dismissButton` aber ein selbstgebautes `Row` mit **zwei** Knöpfen (Löschen und Abbrechen) — für den
FlowRow ist das ein *unteilbares* Element. Passte `[Löschen Abbrechen] [Speichern]` nicht in eine
Zeile, brach er vor „Speichern" um. Kein Zufall und kein Fehler von Material, sondern die Folge des
eigenen `Row`.

**„Speichern" sah aus wie „Abbrechen".** In allen vier Dialogen waren Bestätigen und Abbrechen
derselbe `TextButton`; nur Löschen hob sich über `error` ab. Der Knopf mit der größten Tragweite trug
damit dasselbe Gewicht wie der harmloseste — und bei drei der vier Dialoge hängt an ihm ein
`enabled = …isNotBlank()`, dessen Zustand als zweite Grünnuance kaum zu sehen war.

Dazu kommt der Punkt, den [ADR 0031](0031-rueckgaengig-statt-rueckfrage-beim-loeschen.md) benannt und
offen gelassen hat: Löschen kostete „einen Tipp ohne Rückfrage, **direkt neben Speichern**". Das
Rückgängig hat die Umkehrbarkeit gelöst, nicht die Nachbarschaft.

**Der gefüllte Knopf war hier schon einmal abgelehnt.**
[ADR 0021](0021-eigene-farbpalette-statt-dynamic-color.md) verwarf „Nur den Bestätigungsknopf auf
`Button` umstellen" als „halben Fix, der die Ursache stehen lässt". Das war ein Argument gegen einen
**Ersatz für die Farbpalette**: Der gefüllte Knopf hätte damals ein Kontrastproblem an einer Stelle
geheilt und jeden anderen `TextButton` bei 2,12 : 1 gelassen. Dieses Problem ist seit ADR 0021
strukturell gelöst. Hier kommt die Frage aus einem anderen Grund zurück — **Gewichtung, nicht
Lesbarkeit** — und ADR 0021 bleibt in allen Punkten gültig.

## Entscheidung

1. **Die Knopfzeile trägt nur, was den Dialog beendet.** Löschen verlässt den `dismissButton` des
   Bearbeiten-Dialogs und wird eine zweite Aktionszeile im `text`-Slot, **unter** „Termin anlegen".
   Damit gilt allgemein, was der Kalender-Knopf bisher nur für sich beanspruchte
   ([ADR 0027](0027-termine-per-kalender-intent.md)): Aktionen, die sofort ausführen statt das
   Ergebnis des Dialogs zu bestätigen oder zu verwerfen, stehen im Inhalt.
2. **Das eigene `Row` im `dismissButton` entfällt.** Die Zeile ist wieder Material-Standard
   `[Abbrechen] [Speichern]` — zwei unteilbare Elemente statt einem großen, und der Umbruch
   verschwindet ohne Layout-Kunstgriff.
3. **Bestätigen ist ein gefüllter `Button`, Abbrechen bleibt `TextButton`** — in allen vier Dialogen
   gleich.
4. **`Button`, nicht `FilledTonalButton`.** Der tonale Knopf färbt `secondaryContainer` /
   `onSecondaryContainer` — genau die Rollen, die die *gewählte* Stufe der Segment-Auswahl im selben
   Dialog trägt. „Speichern" und die gewählte Priorität sähen gleich aus. Der gefüllte Knopf ist
   außerdem schon Hausstil (`LoginScreen`), „überall gleich" braucht dafür keine neue Festlegung.
5. **„Liste löschen?" bestätigt gefüllt in `error`/`onError`.** Dort *ist* das Löschen die Hauptaktion
   des Dialogs; die Bremse ist der Dialog mit seiner Anzahl
   ([ADR 0019](0019-schreibrechte-auf-listen-dokumente.md)), nicht ein leiser Knopf. Und ein
   Listen-Löschen hat kein Rückgängig — ADR 0031 gilt nur für Aufgaben.
6. **Löschen im Bearbeiten-Dialog bleibt ein `TextButton` in Fehlerfarbe.** Ein zweiter gefüllter
   Knopf würde mit „Speichern" um die Hauptaktion streiten. Die Farbe sitzt dabei über
   `ButtonDefaults.textButtonColors(contentColor = …)` am Knopf und nicht mehr am `Text` — nur so
   trägt auch das Symbol daneben die richtige Farbe.

Die beiden Aktionen im Inhalt bekommen **keinen Trenner und keinen zusätzlichen Abstand**: Sie
unterscheiden sich schon durch Symbol und Farbe, und eine `HorizontalDivider` im `text`-Slot erreicht
die Dialogkanten nicht — sie läse sich als verirrter Strich statt als Struktur.

## Konsequenzen

- Der Umbruch ist weg, und der Grund dafür steht aufgeschrieben: Wer künftig wieder zwei Knöpfe in
  einen Slot legt, holt ihn zurück.
- **Der Fehlgriff neben „Speichern" ist nicht mehr möglich**, ohne dass Löschen einen Tipp mehr
  kostet. Damit ist der Faden aus ADR 0031 zu Ende geführt.
- Löschen bleibt mit TalkBack bedienbar — das verlangt
  [ADR 0016](0016-wischen-loescht-nur-erledigte-aufgaben.md) ausdrücklich, weil die Wischgeste dort
  nicht ausführbar ist und offene Aufgaben sich gar nicht wischen lassen. Ein `TextButton` mit
  sichtbarer Beschriftung ist per Wisch-Navigation erreichbar, und der Fokus scrollt den Inhalt bei
  Bedarf mit. „Aus dem Bild gescrollt" ist damit ein Problem für Sehende, nicht für TalkBack.
- **Der Inhalt des Bearbeiten-Dialogs wächst um eine Zeile.** Das `verticalScroll` aus Phase 12 trägt
  das, aber der offene Punkt „Bearbeiten als eigener Bildschirm" wird dadurch **dringlicher**: Dieser
  ADR nimmt ihm ein Argument (die Knopfzeile) und fügt ein anderes hinzu (die Höhe). Entschieden ist
  er damit nicht.
- **Ein neues geprüftes Farbpaar:** `onError` auf `error` steht jetzt in `ColorSchemeContrastTest`.
  `error` auf `surfaceContainerHigh` bleibt geprüft, weil Löschen im Bearbeiten-Dialog weiterhin Text
  in Fehlerfarbe auf dem Dialoghintergrund ist. Für die gefüllten Bestätigungen genügt das vorhandene
  `onPrimary` auf `primary`. Die Zahl „acht Paare" in ADR 0021 war schon vorher eine Momentaufnahme;
  es sind jetzt elf.
- **Ein neuer privater Helfer `DialogAction`** hält Symbolgröße, Abstand und `contentDescription = null`
  für beide Aktionen an einer Stelle. Die vier `dismissButton` bleiben dagegen bewusst ausgeschrieben:
  Ein Helfer dafür hätte als einzigen Parameter den Rückruf selbst und wäre der wertlose Wrapper, den
  `avoid-unnecessary-wrappers` verbietet.
- Die vier Dialoge sind ab jetzt gleich gebaut. Ein fünfter hält sich daran.
- **Sichtbar, aber unbestellt:** Der Druckeffekt des Löschen-Knopfs ist jetzt rot statt grün — das
  folgt aus `contentColor`.

## Alternativen

- **Löschen in der Knopfzeile lassen und nur „Speichern" füllen.** Der kleinste Eingriff, aber das
  unteilbare `Row` bliebe und mit ihm der Umbruch — bei größerer Schrift kommt er zurück. Und Löschen
  bliebe neben Speichern. Verworfen.
- **`FilledTonalButton` für die Bestätigung.** Von der `ROADMAP.md` als zweite Möglichkeit genannt.
  Leiser und näher am Rest der Oberfläche, aber der Unterschied zu „Abbrechen" wäre wieder eine
  Nuance — genau die Beschwerde. Dazu die Farbkollision mit der Segment-Auswahl. Verworfen.
- **Löschen als Mülleimer-Symbol in der Titelzeile.** Spart Höhe, aber `AlertDialog` hat dort keinen
  Slot (es wäre Eigenbau im `title`), und ein destruktiver Knopf ohne Beschriftung ist der falsche
  Ort zum Sparen. Verworfen.
- **Löschen neben „Termin anlegen" in eine Zeile.** Passt nicht: zwei Beschriftungen mit Symbol
  brauchen rund 270 dp, ein `AlertDialog` auf einem 360 dp breiten Gerät gibt etwa 256 dp Inhalt her,
  und `Row` bricht nicht um. Verworfen.
- **Löschen ganz aus dem Dialog nehmen, es gibt ja die Wischgeste.** Verstößt gegen ADR 0016 in zwei
  Punkten: Die Geste ist mit TalkBack nicht ausführbar, und offene Aufgaben lassen sich nicht wischen
  — sie wären unlöschbar. Verworfen.
- **Die destruktive Bestätigung in „Liste löschen?" als `TextButton` in Rot lassen.** Wäre die Regel
  „gefüllt = das Sichere, roter Text = das Gefährliche" und hätte den Vorzug, den folgenschwersten
  Knopf der App nicht zum größten Ziel zu machen. Verworfen, weil er in *diesem* Dialog die
  Hauptaktion ist und die Bremse der Dialog selbst ist — und weil „alle vier gleich" sonst in einem
  benannten Punkt bricht.
- **Nur den Bearbeiten-Dialog anfassen.** Billiger, ergäbe aber zwei Dialogstile in einer App mit
  vier Dialogen. Verworfen.
- **Auf den eigenen Bearbeiten-Bildschirm warten,** der die Knopfzeile ohnehin abschafft. Der ist
  unentschieden und wäre der erste Navigationsschritt des Projekts; der Umbruch war heute sichtbar und
  kostet wenige Zeilen. Die drei Listen-Dialoge bleiben außerdem Dialoge, wie der Bildschirm auch
  entschieden wird. Zurückgestellt, nicht verworfen.
