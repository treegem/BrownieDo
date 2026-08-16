# 0034 – Vorlagen sind Listen mit einem Flag

**Status:** akzeptiert · **Datum:** 2026-08-16

## Kontext

Manches wiederholt sich: Was für einen Urlaubstag eingepackt wird, was zu einem Wocheneinkauf gehört.
Eine **Vorlage** hält das einmal fest, daraus entsteht bei Bedarf eine konkrete Liste, in der abgehakt
wird — und beim nächsten Mal die nächste. Die Anforderung war ausdrücklich, dass **beide ganz normal
bearbeitbar** sind: eine Vorlage wie eine Liste, und die daraus entstandene Liste wie jede andere.

Das ist der Punkt, an dem sich die Frage entscheidet. „Ganz normal bearbeitbar" ist entweder etwas,
das man geschenkt bekommt, oder etwas, das man nachbaut — Zeile für Zeile, Dialog für Dialog.

Der Bestand, um den es geht: ein Bildschirm mit Scaffold, Listen-Auswahl in der TopAppBar
([ADR 0013](0013-eingabefeld-in-der-bottombar-statt-fab.md)), ein Bearbeiten-Dialog mit fünf Eingaben
([ADR 0033](0033-bearbeiten-bleibt-ein-dialog.md)), `lists/{listId}` mit `members` und
Sub-Collection `todos` ([ADR 0009](0009-listen-dokument-mit-todo-subcollection.md)), und Security
Rules, die von Hand in der Firebase Console veröffentlicht werden müssen.

## Entscheidung

**Eine Vorlage ist ein Dokument in `lists` wie jedes andere, mit einem zusätzlichen Feld
`isTemplate: true`.** Sie hat dieselbe `todos`-Sub-Collection, dieselben Mitglieder, dieselbe
Sortierung, denselben Bildschirm und denselben Bearbeiten-Dialog.

Daraus folgt eine Reihe von Entscheidungen, die alle in dieselbe Richtung zeigen — **so viel wie
möglich unverändert lassen:**

- **Kein zweiter Bildschirm, sondern ein Modus.** Ein eigener Vorlagen-Bildschirm wäre Navigation und
  damit **Auslöser 7 aus ADR 0033** („die App bekommt aus einem anderen Grund Navigation"). Er zöge
  den Bearbeiten-Bildschirm mit, den ADR 0033 gerade erst zurückgestellt hat — eine Phase über
  Vorlagen hätte damit den teuersten offenen Umbau des Projekts ausgelöst. Der Modus kostet
  stattdessen ein abgeleitetes Feld im UiState.
- **Drei Unterschiede im Modus, mehr nicht.** Kein Abhaken (die Checkbox entfällt — abgehakt wird in
  der Liste, die aus der Vorlage entsteht), ein eigener Leerzustand-Text, und eine Markierung neben
  dem Namen in der TopAppBar. Die Wischgeste braucht keine eigene Regel: Sie greift nur bei
  erledigten Aufgaben ([ADR 0016](0016-wischen-loescht-nur-erledigte-aufgaben.md)), und die gibt es
  in einer Vorlage nicht. `TODO_ORDER` bleibt unberührt.
- **Kein „Termin anlegen" im Bearbeiten-Dialog einer Vorlage.** Ein Vorlagen-Eintrag hat keinen
  konkreten Tag, und ein geratenes Datum hat
  [ADR 0027](0027-termine-per-kalender-intent.md) ausdrücklich verworfen. Das ist zugleich der Platz,
  den Phase 14b für das Mengenfeld braucht: **So trägt kein Modus dieses Dialogs mehr Eingaben als
  vor Phase 14**, und Auslöser 1 aus ADR 0033 („ein sechstes Eingabefeld") greift nicht.
- **Verschieben trennt die beiden Welten.** Das Zielliste-Feld zeigt Gleichartiges: in einer Liste
  nur Listen, in einer Vorlage nur Vorlagen. Eine Aufgabe in eine Vorlage zu schieben wäre kein
  Ablegen, sondern ein Verlust aus der Wochenliste; umgekehrt fehlte der Vorlage danach ein Eintrag,
  den sie beim nächsten Mal wieder braucht. Die Regel steht im UiState und wird im ViewModel ein
  zweites Mal geprüft — dieselbe zweite Verteidigungslinie wie bei `isDone` und der Wischgeste.
- **Der Rückfall bevorzugt eine Arbeitsliste.** Wer eine Liste verloren hat, will weiterarbeiten und
  nicht in einer Vorlage landen. Erst wenn es gar keine Arbeitsliste gibt, greift die erste Vorlage —
  ein leerer Bildschirm neben einer vorhandenen Vorlage wäre die schlechtere Antwort. Die *gemerkte*
  Auswahl gewinnt in jedem Fall und darf eine Vorlage sein
  ([ADR 0018](0018-datastore-fuer-die-zuletzt-gewaehlte-liste.md)).
- **Der Ort in der Oberfläche ist die Listen-Auswahl in der TopAppBar**, als zweiter Abschnitt unter
  einem Trenner. Die Abschnitts-Überschriften erscheinen erst, wenn es überhaupt eine Vorlage gibt —
  wer keine anlegt, sieht dasselbe Menü wie vor Phase 14. „Liste aus Vorlage …" steht im
  Überlauf-Menü und nur, während eine Vorlage offen ist.
- **Ein Dialog für alle drei Anlegewege.** Neue Liste, neue Vorlage und Liste aus einer Vorlage haben
  dieselben zwei Eingaben — Name und geteilt/privat. Es wechselt allein die Überschrift.
- **Das Instanziieren geht als ein `WriteBatch` raus**, Listen-Dokument und alle Aufgaben zusammen,
  mit lokal vergebener id. Dasselbe Muster wie beim Verschieben
  ([ADR 0024](0024-verschieben-behaelt-zustand.md)) und aus demselben Grund: Eine halb angelegte
  Liste wäre schlimmer als gar keine. Auf den Server wird nicht gewartet
  ([ADR 0011](0011-schreibvorgaenge-nicht-abwarten.md)), das Instanziieren funktioniert also offline.
- **`createdAt` wird aus der Vorlage übernommen.** Sonst bekämen alle Aufgaben eines Batches
  praktisch denselben Zeitpunkt und die Reihenfolge der Vorlage ginge verloren — `TODO_ORDER`
  sortiert offene Einträge gleicher Priorität danach. Die Ausnahme aus
  [ADR 0026](0026-verschieben-schreibt-createdat-selbst.md) gilt damit auch hier. Der
  Erledigt-Zustand kommt dagegen **nicht** mit: Eine frische Liste ist offen.
- **Die neue Liste wird geöffnet** — als einziger Anlegeweg. Wer eine Vorlage instanziiert, will
  genau dort weiterarbeiten; wer eine leere Liste anlegt, oft noch nicht.

## Konsequenzen

- **Die Security Rules bleiben unberührt, und es gibt keinen Schritt in der Firebase Console.** Das
  ist kein Zufall, sondern der Prüfstein der Entscheidung: `create` auf `lists` prüft `members` und
  `name`, aber **keine Feldmenge**; `update` erlaubt alles außer einer Änderung an `members`; auf
  `todos` gilt `read, write` für alle Mitglieder ohne Feldprüfung
  ([ADR 0019](0019-schreibrechte-auf-listen-dokumente.md)). Ein zusätzliches Feld und ein weiteres
  Dokument in derselben Collection sind damit bereits erlaubt.
- **Kein Nachziehen bestehender Dokumente.** `ListDocument.isTemplate` ist nicht-nullable mit Vorgabe
  `false`, und Firestores `toObject` lässt ein fehlendes Feld genau darauf stehen. Dieselbe Migration
  wie bei der Notiz in Phase 12 — „fehlt" heißt hier richtigerweise „keine Vorlage".
- **Umbenennen und Löschen gelten für Vorlagen mit, ohne eine Zeile Logik.** Nur die Beschriftungen
  wechseln: Eine Vorlage *ist* eine Liste, aber niemand nennt sie so, und
  `consistent-domain-terminology` ist MUST FIX.
- **Eine Vorlage kann geteilt oder privat sein**, wie jede Liste. Eine geteilte Packliste ergibt bei
  der Instanziierung eine geteilte Reise — das ist die Vorbelegung, änderbar bleibt sie.
- **Der Preis:** Der Bildschirm trägt einen Modus, und `TodoListViewModel` wächst weiter. Der offene
  Punkt „drei Themen, 471 Zeilen" aus Phase 13 wird dadurch dringlicher — die ~600-Zeilen-Grenze aus
  ADR 0033 (Auslöser 6) rückt näher.
- **Was hier *nicht* entschieden ist:** Menge und Faktor. Die kommen in Phase 14b und bekommen einen
  eigenen ADR. Diese Entscheidung hält ihnen nur den Platz frei, den der Kalender-Knopf im
  Vorlagen-Modus räumt.

## Alternativen

- **Eine eigene `templates`-Collection.** Die saubere Trennung, und der Grund, warum sie verloren hat,
  ist die Anforderung selbst: „ganz normal bearbeitbar" hätte nachgebaut werden müssen. Konkret
  bedeutet die Alternative eigene Security Rules — und damit einen Schritt von Hand in der Firebase
  Console, den niemand vergessen darf, sonst scheitert jeder Schreibvorgang mit `PERMISSION_DENIED` —,
  ein zweites Repository, ein zweites Dokument- und Domänenmodell samt Mapper, und einen zweiten Weg
  durch Bildschirm und Bearbeiten-Dialog. Dafür bekäme man die Freiheit, dass Vorlagen sich später
  unabhängig von Aufgaben entwickeln. Diese Freiheit wird nicht gebraucht: Eine Vorlage ist eine
  Liste, deren Einträge man noch nicht abhakt. „Einfachheit vor Vollständigkeit."
- **Vorlagen als gewöhnliche Listen ohne jede Kennzeichnung**, nur mit einer Namenskonvention. Kostet
  nichts und trägt nichts: Die App könnte weder das Abhaken abschalten noch das Instanziieren
  anbieten, und der Name wäre Datenmodell — genau die Sorte impliziter Regel, die nach einem Jahr
  niemand mehr kennt.
- **Instanziieren als Kopie über zwei Schreibvorgänge** (Liste anlegen, dann Aufgaben nachschieben).
  Einfacher zu lesen, aber es gibt einen sichtbaren Zwischenzustand: eine leere Liste, die sich
  füllt — und wenn der zweite Schritt scheitert, eine leere Liste, die stehen bleibt.
