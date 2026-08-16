# 0038 – Instanziieren als schwebender Knopf statt Eintrag im Überlauf-Menü

**Status:** akzeptiert · **Datum:** 2026-08-16

## Kontext

Eine Vorlage ist der Stempel, nicht der Abdruck: Sie existiert, damit man Listen daraus erzeugt
([ADR 0034](0034-vorlagen-sind-listen-mit-einem-flag.md)). Genau diese Aktion lag seit Phase 14a am
unauffälligsten Ort des Bildschirms — als Textzeile im Überlauf-Menü der TopAppBar, hinter zwei
Tipps, ohne Symbol und ohne Hervorhebung. Wer eine Vorlage öffnet, sieht einen Bildschirm, der
aussieht wie eine Liste, in der sich nur nichts abhaken lässt, und muss den nächsten Schritt raten.

Das Menü selbst war dabei nicht falsch gebaut — der Eintrag stand dort bewusst an erster Stelle. Nur
ist „erster Eintrag in einem eingeklappten Menü" für die *eine* Aktion, um die es bei einer Vorlage
geht, die falsche Gewichtsklasse.

Der Bildschirm hat für eine so herausgehobene Aktion bislang keinen Platz gehabt.
[ADR 0013](0013-eingabefeld-in-der-bottombar-statt-fab.md) hat die Eingabeleiste in die `bottomBar`
gelegt und daraus die Folgerung gezogen: „Der FAB-Platz unten rechts ist damit belegt. Künftige
Primäraktionen müssen in die TopAppBar." Diese Folgerung wird hier geprüft und verworfen — sie hat
den FAB mit der Leiste verwechselt.

## Entscheidung

Eine Liste aus der offenen Vorlage zu erzeugen wird ein **`ExtendedFloatingActionButton`** unten
rechts, mit Plus-Symbol und der Beschriftung „Liste aus Vorlage …". Der Eintrag im Überlauf-Menü
**entfällt** — ein Weg, eine Stelle.

Der Knopf erscheint nur, solange eine Vorlage offen ist (`TodoListUiState.isTemplateOpen`). In einer
Arbeitsliste gibt es nichts zu instanziieren, dort bleibt der Slot leer und der Bildschirm sieht aus
wie bisher.

**Der FAB-Platz war nie belegt.** Das `Scaffold` legt seinen `floatingActionButton` von sich aus
*über* die `bottomBar`, nicht darüber hinweg — die Eingabeleiste bleibt vollständig sichtbar und
bedienbar, samt ihrer Inset-Behandlung. Was ADR 0013 zu Recht ausgeschlossen hat, war der FAB *als
Ersatz für die Eingabeleiste*, also der Weg „Aufgabe anlegen über FAB plus Dialog". Hier geht es um
eine andere Aktion, die keinen Platz in der Leiste beansprucht.

Die Farben sind die des Hinzufügen-Knopfs in der Eingabeleiste (`primary` auf `onPrimary`) statt der
FAB-Vorgabe `primaryContainer`/`onPrimaryContainer`. Zwei gefüllte Knöpfe auf einem Bildschirm sollen
gleich aussehen, und dieses Paar ist in `ColorSchemeContrastTest` bereits abgesichert
([ADR 0021](0021-eigene-farbpalette-statt-dynamic-color.md)) — die FAB-Vorgabe wäre ein neues Paar
gewesen.

## Konsequenzen

- Die Folgezeile aus ADR 0013 („der FAB-Platz ist belegt") gilt nicht mehr. **Was bleibt: Die
  Eingabeleiste ist unantastbar.** Wer künftig eine Primäraktion unterbringt, hat unten rechts einen
  Platz — aber nicht auf Kosten der Leiste.
- Das Überlauf-Menü trägt für Listen und Vorlagen dieselben drei Einträge (Umbenennen · Löschen ·
  Abmelden). Der Unterschied zwischen beiden liegt damit vollständig im Bildschirm selbst, nicht
  mehr in einem eingeklappten Menü.
- Der Knopf schwebt über der Liste und verdeckt beim Scrollen eine Zeile. Die `LazyColumn` bekommt
  deshalb bei offener Vorlage einen Platzhalter am Ende, sonst wäre der letzte Eintrag nicht
  antippbar.
- **Die Beschriftung muss zusätzlich als `contentDescription` gesetzt werden.** Der Extended FAB
  faltet seinen Text-Slot nicht in den zusammengefassten Semantik-Knoten — der trägt sonst nur
  `Role = Button`, weder `Text` noch `ContentDescription`, und TalkBack fände einen Knopf ohne
  Namen. Aufgefallen ist das erst, als der instrumentierte Test ihn über seinen sichtbaren Text
  nicht fand; ein gewöhnlicher `Button` verhält sich anders, deshalb war das bei den Dialogknöpfen
  nie ein Thema. Wer den Knopf anfasst, lässt die Zeile stehen.
- `onCreateListFromTemplateClick` verlässt `TodoListTopBarActions` und wird ein einzelner Parameter
  von `TodoListScreen` — der Knopf gehört wie der `SnackbarHost` dem Scaffold und keinem der
  sichtbaren Bereiche. Denselben Weg sind `onErrorShown` und `onMovedMessageShown` gegangen: einzeln,
  bis es genug für einen eigenen Halter waren ([ADR 0028](0028-rueckrufe-in-actions-haltern.md)).
- Der Bildschirm wächst damit von sieben auf acht Parameter. Kommt ein zweiter Scaffold-Rückruf
  dazu, ist der sechste Halter fällig.

## Alternativen

- **Alles lassen, wie es war.** Der billigste Weg, und der Grund für diese Änderung: Die Aktion, um
  die es bei einer Vorlage geht, war der am schlechtesten erreichbare Weg des Bildschirms.
- **Beide Wege behalten** — Knopf *und* Eintrag im Menü. Zwei Bedienstellen für dieselbe Aktion, die
  beide gepflegt und getestet werden wollen, ohne dass die zweite etwas kann, was die erste nicht
  kann.
- **Feststehender Knopf über der Liste**, volle Breite, direkt unter der TopAppBar. Verdeckt nichts
  und scrollt nicht weg, kostet aber dauerhaft Höhe und ist in Material kein etabliertes Muster für
  eine Bildschirm-Primäraktion.
- **Zweite Zeile in der `bottomBar`**, über dem Eingabefeld. Immer sichtbar und am Daumen — säße
  aber unmittelbar über dem Hinzufügen-Knopf für neue Einträge, also ein Fehlgriff zwischen „Eintrag
  anlegen" und „ganze Liste anlegen". Und sie fasst genau die Leiste an, die ADR 0013 geschützt
  wissen will.
