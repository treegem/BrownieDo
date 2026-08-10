# 0020 – Partner aus einer handgepflegten `users`-Collection

**Status:** akzeptiert · **Datum:** 2026-08-11

## Kontext

Eine geteilte Liste trägt beide uids in `members`. Um eine anzulegen, muss die App also die uid des
Partners kennen — und sie kannte sie nirgends. Es gab keine `users`-Collection, und `ListMapper`
verwarf `members` nach dem Zählen: Das Domänenmodell `TodoList` trägt nur `isShared`, weil rohe uids
außerhalb der Datenschicht nichts zu suchen haben (ADR 0009).

Der naheliegende Weg wäre gewesen, die uid aus einer **bestehenden** geteilten Liste abzuleiten —
die uid, die in einer meiner Listen steht und nicht meine ist, ist bei zwei Personen genau der
Partner. Das kostet keine neue Collection und keine Regeln.

Dabei fiel eine Sackgasse auf, die **erst durch Phase 8b erreichbar wird**: Sobald die App Listen
löschen kann, kann jemand seine letzte geteilte Liste löschen. Damit verschwindet die einzige Quelle
der Partner-uid, und es lassen sich nur noch private Listen anlegen — heraus käme man nur über die
Firebase Console. Vorher war dieser Zustand unerreichbar, weil Löschen Handarbeit war.

## Entscheidung

Eine Collection `users/{uid}` mit einem Feld `displayName`, **von Hand in der Console gepflegt** —
genau wie die Listen-Dokumente vor 8b und `lists/shared` in Phase 3.

Die Domäne bekommt dafür einen eigenen Begriff: `Partner(uid, displayName)` und ein
`PartnerRepository`. Die uid wandert damit **nicht** durch die Oberfläche —
`ListRepository.createList(name, shared)` löst die Mitglieder selbst auf. Das ViewModel liest den
Partner nur, um zu wissen, ob es „geteilt" überhaupt anbieten darf, und um den Namen anzuzeigen:
„Geteilt mit Anna" sagt mehr als „Geteilt".

Gelesen werden darf die Collection **nur von wem selbst darin steht**:

```
allow read: if request.auth != null
  && exists(/databases/$(database)/documents/users/$(request.auth.uid));
```

Das ist kein Übereifer. Firebase Auth mit Google-Anbieter lässt **jedes** Google-Konto sich anmelden,
nicht nur die beiden. Fremde sehen zwar keine Listen, weil sie in keinem `members` stehen — ohne
diese Bedingung könnten sie aber beide Namen auslesen. Geschrieben wird die Collection von niemandem
(`allow write: if false`).

Ein Lesefehler wird zu `null` statt zu einem Fehlerzustand: Für den Aufrufer ist ein nicht lesbarer
Partner dasselbe wie keiner — eine geteilte Liste lässt sich so oder so nicht anbieten, und die
Listen selbst sind davon unberührt.

## Konsequenzen

- **Eine geteilte Liste lässt sich immer anlegen**, auch aus einer privaten heraus und auch wenn
  gerade keine geteilte existiert. Genau das war mit der Ableitung nicht garantiert.
- Ein frisch aufgesetztes Projekt braucht **einen Handgriff mehr**: zwei Dokumente in `users`. Fehlen
  sie, funktioniert alles außer dem Anlegen geteilter Listen; die Oberfläche sagt, woran es liegt.
- Ein neues Konto wird nicht automatisch zum Partner. Das ist beabsichtigt — die App ist für zwei
  feste Personen, und wer dazukommt, wird bewusst eingetragen.
- `displayName` ist bislang die einzige Verwendung; die Collection lädt dazu ein, dort später mehr
  abzulegen. Wächst sie, gehört sie neu bewertet.

## Alternativen

- **Die uid aus bestehenden Listen ableiten:** Spart die Collection und die Regeln und war der
  ursprüngliche Plan. Öffnet aber die oben beschriebene Sackgasse, sobald die letzte geteilte Liste
  gelöscht wird, und braucht mehr Code als der gewählte Weg — eine Zwischenform, die `members` durch
  das Mapping trägt, plus einen zweiten Flow über dieselbe Query.
- **`users` von der App schreiben lassen**, beim ersten Login: Kein Aufsetz-Schritt und robust gegen
  neue Konten. Braucht dafür Schreibrechte auf die Collection und einen Schreibvorgang im
  Login-Pfad — mehr bewegliche Teile für einen Zustand, der sich zweimal im Leben des Projekts
  ändert.
- **Die uid im Dialog eintippen:** Ohne jede neue Infrastruktur. Ein 28-Zeichen-String aus der
  Console abzutippen, und das bei jeder neuen geteilten Liste.
- **Partner-uid als Build-Konstante** (wie die Signatur-Zugangsdaten in
  [ADR 0017](0017-signatur-zugangsdaten-aus-keystore-properties.md)): Passt zum Zwei-Personen-
  Charakter, müsste aber pro Gerät die uid des jeweils *anderen* tragen — also zwei Build-Varianten.
- **`completedBy` aus Todos ernten:** Die einzige andere Stelle, an der eine fremde uid im
  Datenbestand auftaucht. Erst befüllt, wenn der Partner mal etwas abgehakt hat — als Quelle
  unbrauchbar.
