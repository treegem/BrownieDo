# 0009 – Listen-Dokument mit Todo-Sub-Collection

**Status:** akzeptiert · **Datum:** 2026-08-07

## Kontext

BrownieDo soll dauerhaft **mehrere Listen** tragen: geteilte Listen für beide Partner und private
Listen für je einen von beiden. Die ursprüngliche Roadmap sah nur eine einzige geteilte Liste vor
und führte mehrere Listen unter „spätere Erweiterungen".

Die Struktur in Firestore lässt sich nachträglich nur mit einer Datenmigration ändern — Dokumente
müssten umkopiert und alle Clients gleichzeitig umgestellt werden. Deshalb wird sie jetzt
festgelegt, obwohl die Listen-Verwaltung erst später gebaut wird.

Zusätzlich muss die Struktur zwei Dinge erlauben:

- Security Rules, die entscheiden können, wer eine Liste sehen darf.
- Einen Realtime-Listener, der genau die Aufgaben **einer** Liste liefert, ohne fremde Aufgaben
  zum Gerät zu übertragen.

## Entscheidung

```
lists/{listId}
  name: String
  members: [uid, …]        // 1 Eintrag = private Liste, 2 Einträge = geteilte Liste
  createdAt: Timestamp

lists/{listId}/todos/{todoId}
  title: String
  done: Boolean
  createdAt: Timestamp     // @ServerTimestamp, siehe ADR 0006
  updatedAt: Timestamp     // @ServerTimestamp, siehe ADR 0006
  completedBy: String?     // uid, null solange die Aufgabe offen ist
```

- Die Zugehörigkeit steckt **ausschließlich** im Listen-Dokument. Ein Todo-Dokument trägt keine
  Besitz- oder Listen-Information; seine Liste ergibt sich aus dem Pfad.
- „Privat" ist kein eigener Typ, sondern eine Liste mit genau einem Eintrag in `members`. Es gibt
  damit nur ein Datenmodell und keine Rechteverwaltung jenseits dieser Mitgliederliste.
- Todo-IDs sind von Firestore vergebene Auto-IDs und werden nicht als Feld gespeichert.
- Bis die Listen-Verwaltung existiert, arbeitet die App gegen die feste Liste `lists/shared`.
  Dieses Dokument wird einmalig von Hand in der Firebase Console angelegt.

## Konsequenzen

- Security Rules bleiben kurz: Der Zugriff auf `lists/{listId}/todos/**` hängt an einer einzigen
  Bedingung — `request.auth.uid` steht in `members` des übergeordneten Listen-Dokuments.
- Weil Listen-Dokumente ausschließlich von Hand in der Console entstehen, dürfen die Rules jedes
  Schreiben auf `lists/{listId}` verbieten. Niemand kann sich selbst in eine fremde Liste
  eintragen. Der Preis: Jede neue Liste ist vorerst Handarbeit, bis die Listen-Verwaltung kommt.
- Ein Listener holt immer genau eine Liste. Eine listenübergreifende Ansicht („alle offenen
  Aufgaben") bräuchte eine Collection-Group-Query samt zusätzlichem Index — die ist nicht geplant.
- Die Sub-Collection wird beim Löschen des Listen-Dokuments **nicht** mitgelöscht; Firestore kennt
  kein kaskadierendes Löschen. Sobald Listen löschbar werden, muss das explizit passieren.
- `TodoDocument`, `TodoMapper` und `Todo` bleiben unverändert — die Struktur wirkt sich nur auf
  den Pfad aus, nicht auf die Felder.

## Alternativen

- **Flache Top-Level-Collection `todos`:** Die einfachste Variante und ursprünglich in der Roadmap
  vorgesehen. Sie kennt aber nur eine einzige Liste; private Listen wären ohne Migration nicht
  nachrüstbar. Verworfen, weil mehrere Listen ein festes Ziel sind.
- **Flache `todos`-Collection mit `listId`-Feld:** Erlaubt mehrere Listen ohne Verschachtelung und
  ermöglicht listenübergreifende Abfragen. Jede Rule müsste dann aber pro Todo-Dokument das
  zugehörige Listen-Dokument nachladen, und jede Abfrage bräuchte einen zusammengesetzten Index.
  Mehr Aufwand für eine Funktion, die nicht gebraucht wird.
- **Getrennte Collections für private und geteilte Listen:** Macht die Trennung im Pfad sichtbar,
  verdoppelt aber Datenmodell, Rules und Repository-Code für einen Unterschied, der sich bereits
  aus der Länge von `members` ergibt.
- **`members` als Map `{uid: rolle}` statt Array:** Wäre die Grundlage für Rollen wie „nur lesen".
  Genau die Rechteverwaltung, die die Roadmap ausschließt.
