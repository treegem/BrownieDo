# Architecture Decision Records

Hier stehen die **Begründungen** hinter den technischen Entscheidungen von BrownieDo —
also das, was aus dem fertigen Code nicht mehr ablesbar ist.

## Abgrenzung zu den anderen Dokumenten

| Dokument | Inhalt |
|---|---|
| `ROADMAP.md` | Vision, gewählter Stack, Fortschritt — das **Was** |
| `.github/instructions/` | Verbindliche Coding-, Architektur- und Naming-Regeln — das **Wie** |
| `docs/decisions/` | Warum eine Option gewonnen hat und was das kostet — das **Warum** |

Eine Entscheidung wird hier genau einmal beschrieben. Wiederhole sie nicht in `ROADMAP.md`,
sondern verlinke sie.

## Wann ein neuer ADR entsteht

Immer dann, wenn eine Entscheidung schwer rückgängig zu machen ist, eine ernsthafte Alternative
verworfen wurde oder jemand später fragen könnte „warum eigentlich so?".

## Format

Eine Datei pro Entscheidung, fortlaufend nummeriert: `NNNN-kurzer-titel.md`.
Aufbau: Status · Kontext · Entscheidung · Konsequenzen · Alternativen.
Ein bestehender ADR wird nicht umgeschrieben — bei einer Kehrtwende entsteht ein neuer,
der den alten als „abgelöst" markiert.

## Übersicht

| Nr. | Entscheidung | Status |
|---|---|---|
| [0001](0001-entscheidungen-als-adr-festhalten.md) | Entscheidungen als ADR festhalten | akzeptiert |
| [0002](0002-google-login-als-einziger-anbieter.md) | Google-Login als einziger Anbieter | akzeptiert |
| [0003](0003-credential-manager-statt-googlesignin.md) | Credential Manager statt GoogleSignIn-SDK | akzeptiert |
| [0004](0004-manuelle-dependency-injection-ohne-hilt.md) | Manuelle Dependency Injection ohne Hilt | akzeptiert |
| [0005](0005-web-client-id-aus-google-services-json.md) | Web-Client-ID aus `google-services.json` beziehen | akzeptiert |
| [0006](0006-server-zeitstempel-fuer-last-write-wins.md) | Server-Zeitstempel für Last-Write-Wins | akzeptiert |
| [0007](0007-java-time-per-core-library-desugaring.md) | `java.time` per Core Library Desugaring | akzeptiert |
| [0008](0008-compose-bom-in-zwei-konfigurationen.md) | Compose-BOM in zwei Konfigurationen deklarieren | akzeptiert |
| [0009](0009-listen-dokument-mit-todo-subcollection.md) | Listen-Dokument mit Todo-Sub-Collection | akzeptiert |
