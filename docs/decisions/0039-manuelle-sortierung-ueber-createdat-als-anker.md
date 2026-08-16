# 0039 – Manuelle Sortierung über `createdAt` als Anker, gezogen mit einer Bibliothek

**Status:** akzeptiert · **Datum:** 2026-08-16

## Kontext

Innerhalb einer Prioritätsstufe entschied bisher `createdAt` absteigend, was oben steht. Für den
wöchentlichen Rhythmus der App ist das die falsche Reihenfolge: Was zuerst dran ist, hat mit dem
Anlegedatum nichts zu tun. Phase 15 macht die Reihenfolge **innerhalb** einer Stufe von Hand
bestimmbar — erledigt bleibt am Ende, die Priorität bleibt über der Handsortierung.

Zwei Dinge waren zu entscheiden, und beide stehen hier: **woran** eine Position hängt und **womit**
gezogen wird.

## Entscheidung 1: Der Anker fällt auf `createdAt` zurück

`Todo` bekommt ein nullable `sortOrder: Double?`. Sortiert wird nicht danach, sondern nach:

```kotlin
val Todo.effectiveOrder: Double get() = sortOrder ?: createdAt.toEpochMilli().toDouble()
```

Größer heißt weiter oben — dieselbe Richtung wie das `createdAt`, das der Wert verallgemeinert.
`OPEN_ORDER` vergleicht damit Priorität, dann `effectiveOrder`, dann wie bisher `createdAt`.
`FINISHED_ORDER` und die äußere `isDone`-Unterscheidung bleiben unangetastet.

Beim Ablegen bekommt **nur die gezogene Aufgabe** einen neuen Wert: die Mitte zwischen den Ankern
ihrer neuen Nachbarn, am Rand einer Gruppe ein fester Abstand von einer Sekunde zum einzigen
Nachbarn. Geschrieben wird feldweise wie bei `setDone`.

### Warum nicht der ursprüngliche Entwurf (`nullsLast`)

Die `ROADMAP.md` sah zuerst `sortOrder` aufsteigend mit `nullsLast` vor, Bestandsaufgaben ohne Wert
ans Ende ihrer Gruppe. **Das ist beim ersten Ziehen in einer Gruppe nicht umsetzbar**, und das ist
kein Randfall, sondern der Normalfall nach dem Ausrollen: Eine Gruppe, in der noch niemand gezogen
hat, besteht ausschließlich aus `null`. Es gibt keinen einzigen Zahlenanker, zwischen den sich etwas
legen ließe — jeder Wert, den man der gezogenen Aufgabe gibt, hebt sie über *alle* Nullwerte, also
auf Platz 1, gleichgültig wohin gezogen wurde.

Den Nachbarn Werte zu geben, um das zu reparieren, hilft nicht: Nullwerte haben untereinander gar
keine Ordnung in dieser Dimension, sie sind **eine** Klasse. Wer einem von ihnen einen Wert gibt,
reißt ihn aus der Klasse heraus. Um jedes Paar richtig zu halten, müsste die ganze Gruppe neu
nummeriert werden — genau das, was derselbe Entwurf im nächsten Punkt ausschließt.

Der abgeleitete Anker löst vier Dinge auf einmal:

- **Kein sichtbarer Wechsel beim Ausrollen, und zwar paarweise dauerhaft.** `toEpochMilli()` ist
  monoton im Zeitpunkt, der Vergleich fällt also genauso aus wie bisher; bei Gleichstand entscheidet
  weiterhin `createdAt` selbst. Zwei nie gezogene Aufgaben behalten ihre Reihenfolge für immer, weil
  sie nur von diesen beiden abhängt. Das ist eine bessere Migrationsgeschichte, als Notiz und Menge
  sie hatten.
- **Eine frisch angelegte Aufgabe steht ohne jeden Schreibvorgang oben.** Der Entwurfspunkt
  „`addTodo` vergibt einen Wert" entfällt ersatzlos, samt der Signaturänderung, die er erzwungen
  hätte.
- **Das Zwischenrechnen funktioniert immer**, auch beim allerersten Zug.
- **Verschieben zwischen Listen bleibt sinnvoll.** `createdAt` wandert schon mit (ADR 0026), der
  Anker liegt also im selben Zahlenraum wie in der Zielliste: Eine alte, nie gezogene Aufgabe landet
  nach ihrem Alter — genau das, was der Geräteblick aus Phase 10 verlangt.

### Was der Preis ist

- **Der Wertebereich wandert mit der Uhr.** Jeder heute geschriebene Wert liegt unter jedem morgen
  abgeleiteten. Eine von Hand nach oben gezogene Aufgabe überholt neu angelegte also nur eine
  Sekunde lang. Das passt zur „das Neueste oben"-Neigung der App, ist damit aber festgelegt: „neue
  Aufgaben kommen ans Ende" ließe sich ohne einen Wert in `addTodo` nicht mehr ausdrücken.
- **Die Formel ist abgeleitet, nicht gespeichert.** Sie später zu ändern — etwa auf `updatedAt` —
  sortierte jede nie gezogene Aufgabe im ganzen Bestand still um. Deshalb steht sie an genau einer
  Stelle und ist von Tests festgehalten.
- **Anker unsynchronisierter Aufgaben verschieben sich.** `ESTIMATE` füllt `createdAt` offline mit
  der lokalen Uhrzeit; nach dem Synchronisieren steht dort der Server-Zeitpunkt. Eine offline
  angelegte, nie gezogene Nachbaraufgabe kann danach also die Seite wechseln. Betroffen sind nur die
  neuesten Einträge, die ohnehin oben stehen, und die gezogene Aufgabe selbst ist durch ihren
  geschriebenen Wert festgenagelt.
- **Zwei Aufgaben können denselben Anker haben** (dieselbe Millisekunde, beide nie gezogen — möglich,
  wenn eine Offline-Warteschlange durchläuft), und dann passt zwischen sie nichts. Dasselbe gilt für
  eine Lücke, die durch wiederholtes Hineinziehen aufgebraucht ist: Beim Anker um 1,7 × 10¹² fasst
  eine Millisekunde 4096 `Double`-Schritte, nach zwölf Halbierungen ist sie leer. Beides erkennt
  `sortOrderBetween` an **einem** Prädikat und liefert `null`; die Gruppe wird dann in einem Batch
  neu nummeriert. Ohne diesen Zweig wäre der Zustand dauerhaft, unsichtbar und für die Nutzer nicht
  behebbar.

### Verworfene Alternativen

- **`nullsLast` mit Neunummerierung beim ersten Zug.** Die einzige korrekte Rettung des
  Null-Entwurfs, aber sie macht aus jedem ersten Zug einen Mehr-Dokument-Schreibvorgang, dessen
  Fehlerfall eine halb umnummerierte Gruppe ist — und zwei Partner, die gleichzeitig zum ersten Mal
  in derselben Gruppe ziehen, erzeugen zwei überlappende Nummerierungen, die feldweise gemischt
  werden.
- **Ganzzahlige Rangzahlen (1, 2, 3 …), bei jedem Zug neu durchnummeriert.** Schreibt bei jedem Zug
  die ganze Gruppe, und eine mitgebrachte 3.0 schleuderte jede verschobene Aufgabe ans Ende der
  Zielliste.
- **Gebrochene Indizes über Zeichenketten (LexoRank, wie bei Jira und Figma).** Erschöpft die
  Genauigkeit nie — dafür kann eine Zeichenkette nicht auf `createdAt` zurückfallen. Man bräuchte
  wieder einen Sentinel für Bestandsaufgaben und stünde damit erneut im Null-Problem. Genau die
  Rückfalleigenschaft ist der Grund für den `Double`.
- **Die Reihenfolge als Array am Listen-Dokument.** Ein einziges Feld, das jeder Zug neu schreibt —
  der Gegenentwurf zur feldweisen Konfliktlösung des Projekts, und bei zwei gleichzeitigen Zügen
  verliert einer vollständig.
- **`sortOrder` beim Ändern der Priorität löschen.** Naheliegend, weil der Wert gegen die alte Gruppe
  gerechnet war. Dagegen: Der Bearbeiten-Dialog besitzt das Feld nicht und zeigt es nicht, und ein
  stilles Löschen zerstörte den Zug, den der Partner eine Sekunde vorher gemacht hat — genau der
  Übergriff, den die feldweise Schreibweise verhindern soll. Weil der Wert zeitskaliert ist, ist ein
  mitgenommener Wert außerdem nie wilder als der Anker, den er ersetzt. **Der Wert bleibt.**

## Entscheidung 2: Gezogen wird mit `sh.calvin.reorderable`

Die Geste ist ein langer Druck auf die ganze Zeile, umgesetzt mit
`sh.calvin.reorderable:reorderable:3.1.0`.

Die drei schweren Teile einer Ziehgeste sind Auto-Scroll am Rand, **variable Zeilenhöhen** (die Notiz
als zweite Zeile, ADR 0030) und das Festkleben am Finger über einen Positionstausch hinweg. Selbst
gebaut wären das rund 300 Zeilen erster Gestencode in diesem Projekt — ohne Robolectric nur auf dem
Gerät zu debuggen und praktisch nicht regressionstestbar.

**Das widerspricht der Skepsis gegenüber Abhängigkeiten nicht, es schärft sie:** Hilt wurde abgelehnt
(ADR 0004), weil manuelle DI fünfzig Zeilen offensichtlicher Code sind; Robolectric, weil es eine
Fähigkeit verdoppelte, die das Projekt schon hatte. Hier ist die Alternative weder offensichtlich
noch vorhanden. „Einfachheit vor Vollständigkeit" gilt dem Produkt, und dort ist die Bibliothek die
einfachere Antwort.

Ehrlich benannt: ein Einzelmaintainer, und ihr Toolchain hinkt der Compose-BOM des Projekts mehrere
Minor-Versionen hinterher (sie baut gegen Compose Multiplatform 1.7 / Kotlin 2.0.21, das Projekt
steht auf BOM 2026.06.01 / Kotlin 2.3.10). Gradle hebt ihre Foundation-Abhängigkeit auf 1.11.4; der
Rauchtest vor der Übernahme lief sauber durch.

### Kein Ziehgriff, sondern langer Druck

`trailingContent` trägt den Prioritätspfeil — aber nur bei hoch und niedrig, bei mittel nichts, und
erledigte Zeilen bekämen gar keinen Griff. Der rechte Rand fransete damit aus und **änderte sich beim
Abhaken**. Ein dauerhafter Preis auf jeder Zeile für die Auffindbarkeit einer Geste, die ein paar Mal
pro Woche gebraucht wird.

Als Ausgleich eine Haptik beim Anheben — dieselbe Abwägung, die ADR 0016 beim Wischen schon einmal so
entschieden hat („spürbar statt erklärungsbedürftig"). Sollte sich zeigen, dass niemand die Geste
findet, ist ein Griff später rein additiv: Rückruf, Datenmodell und der bedienbare Weg bleiben
unberührt.

### Die Gruppengrenze wird laufend verweigert

Wird über eine Prioritäts- oder Erledigt-Grenze gezogen, folgt die Zeile weiter dem Finger, nur der
Tausch unterbleibt. Eine Zeile, die stehen bleibt, liest sich wie eine abgerissene Geste; frei ziehen
und erst beim Ablegen verwerfen wäre noch schlechter, weil sich die ganze Liste sichtbar umordnete
und dann zurückschnappte.

## Entscheidung 3: Die Geste ist nicht der einzige Weg

ADR 0016 kam mit einer nicht bedienbaren Wischgeste durch, **weil Löschen einen zweiten, bedienbaren
Weg behielt.** Manuelles Sortieren hat keinen. Nur zu ziehen hieße: Mit TalkBack ist die Funktion
nicht umständlich, sondern abwesend.

Jede offene Zeile trägt deshalb zwei `CustomAccessibilityAction` — „Nach oben verschieben" und „Nach
unten verschieben" —, die durch **denselben** Rückruf laufen wie das Ziehen. Es gibt also keine
zweite Logik, die mitgepflegt werden müsste. An einer Gruppengrenze entfällt die jeweilige Aktion.

Nebeneffekt, der sich auszahlt: Die Aktionen sind ohne Zeitfenster und ohne Animationsuhr prüfbar.
Die instrumentierten Tests der Phase hängen deshalb an ihnen und nicht an der Geste.

## Konsequenzen

- `firestore.rules` bleibt unberührt — `todos` kennt keine Feldprüfung, wie schon bei Priorität,
  Notiz und Menge. **Kein Schritt in der Firebase Console, kein Nachziehen bestehender Dokumente.**
- Ein fehlgeschlagener Zug meldet `UPDATE_FAILED`, wie jeder andere feldweise Schreibvorgang. Ein
  eigener Fehlerwert brächte eine Meldung, die von der vorhandenen nicht zu unterscheiden wäre.
- Die Werte sehen in der Console aus wie Zeitstempel. Das ist Absicht und keine Verwechslung.
- Der `LazyListState` des Bildschirms ist jetzt hochgezogen, und die Zeilen liegen in einem
  `ReorderableItem`. **Dessen `animateItemModifier` trägt das getunte Ausblenden aus ADR 0031** —
  bliebe es an der Zeile stehen, hingen zwei `animateItem` am selben Eintrag und die
  Lösch-Animation wäre still wieder die Vorgabe.
- Während einer Ziehgeste hält der Bildschirm eine vorläufige Reihenfolge lokal und zeigt eingehende
  Änderungen des Partners nicht. Das ist gewollt und endet einen Frame nach dem Ablegen.
- **Drei Gesten teilen sich die Zeile, und ihre Reihenfolge in der Modifier-Kette ist Bedingung, nicht
  Geschmack.** Am `ListItem` steht `combinedClickable` mit leerem `onLongClick` — ohne das öffnet ein
  langer Druck beim Loslassen den Bearbeiten-Dialog. Dahinter, also weiter **innen**, steht der
  `longPressDraggableHandle`; innen liegende Modifier bekommen die Zeigerereignisse zuerst und
  gewinnen den langen Druck. Liegt die Geste dagegen weiter außen (etwa am `SwipeToDismissBox`),
  verschluckt das `combinedClickable` den Druck auf der ganzen Zeile und ziehen lässt sich nur noch
  an der Checkbox — die trägt einen eigenen Erkenner, kennt aber keinen langen Druck und reicht ihn
  nach oben durch. Beide Fehler sind einmal passiert und werden jetzt von je einem instrumentierten
  Test gehalten.
