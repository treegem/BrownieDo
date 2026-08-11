# Architecture Decision Records

Hier stehen die **Begründungen** hinter den technischen Entscheidungen von BrownieDo —
also das, was aus dem fertigen Code nicht mehr ablesbar ist.

## Abgrenzung zu den anderen Dokumenten

Welches Dokument was enthält, steht in [`AGENTS.md`](../../AGENTS.md).

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
| [0010](0010-sortierung-im-client-statt-orderby.md) | Sortierung im Client statt `orderBy` | akzeptiert |
| [0011](0011-schreibvorgaenge-nicht-abwarten.md) | Schreibvorgänge nicht auf den Server warten lassen | akzeptiert |
| [0012](0012-scaffold-pro-bildschirm.md) | Scaffold pro Bildschirm statt app-weit | akzeptiert |
| [0013](0013-eingabefeld-in-der-bottombar-statt-fab.md) | Eingabefeld in der `bottomBar` statt FAB mit Dialog | akzeptiert |
| [0014](0014-regeldateien-always-on.md) | Regeldateien always-on statt pfad-gebunden | akzeptiert |
| [0015](0015-agents-md-als-gemeinsamer-einstieg.md) | `AGENTS.md` als gemeinsamer Agenten-Einstieg | akzeptiert |
| [0016](0016-wischen-loescht-nur-erledigte-aufgaben.md) | Wischen löscht nur erledigte Aufgaben | akzeptiert |
| [0017](0017-signatur-zugangsdaten-aus-keystore-properties.md) | Signatur-Zugangsdaten aus `keystore.properties` | akzeptiert |
| [0018](0018-datastore-fuer-die-zuletzt-gewaehlte-liste.md) | DataStore für die zuletzt gewählte Liste | akzeptiert |
| [0019](0019-schreibrechte-auf-listen-dokumente.md) | Schreibrechte auf Listen-Dokumente | akzeptiert |
| [0020](0020-partner-aus-users-collection.md) | Partner aus einer handgepflegten `users`-Collection | akzeptiert |
| [0021](0021-eigene-farbpalette-statt-dynamic-color.md) | Eigene Farbpalette statt Dynamic Color | akzeptiert |
| [0022](0022-verschieben-im-bearbeiten-dialog.md) | Verschieben im Bearbeiten-Dialog statt per Wischgeste | akzeptiert |
| [0023](0023-prioritaet-migration-und-sortierung.md) | Priorität: Migration bestehender Aufgaben und Einfluss auf die Sortierung | akzeptiert |
| [0024](0024-verschieben-behaelt-zustand.md) | Verschieben behält alle Felder außer der Liste | akzeptiert |
| [0025](0025-titel-und-prioritaet-in-einem-schreibvorgang.md) | Titel und Priorität in einem Schreibvorgang | akzeptiert |
| [0026](0026-verschieben-schreibt-createdat-selbst.md) | Verschieben schreibt `createdAt` selbst und gibt die Feldebene auf | akzeptiert |
