# BrownieDo – Einstieg für Coding-Agents

BrownieDo ist eine private, geteilte ToDo-Liste für genau zwei Personen (Android, Kotlin, Jetpack
Compose, Firebase). Diese Datei ist der gemeinsame Einstieg für alle Coding-Agents — sie enthält
selbst keine Coding-Regeln.

## Vor der Arbeit lesen

| Wo | Was |
|---|---|
| [`ROADMAP.md`](ROADMAP.md) | Vision, Stack, Fortschritt — das **Was** und **Warum überhaupt** |
| [`.github/instructions/`](.github/instructions/) | Verbindliche Coding-, Architektur- und Naming-Regeln — das **Wie** |
| [`docs/decisions/`](docs/decisions/README.md) | ADRs: warum eine Option gewonnen hat — das **Warum so** |

Die Regeln tragen Schweregrade (`MUST FIX`, `SHOULD FIX`, `CONSIDER`) — die sind ernst gemeint.

Wie die Regeldateien in den Kontext kommen, hängt am Werkzeug und steht in
[ADR 0015](docs/decisions/0015-agents-md-als-gemeinsamer-einstieg.md). Alle Regeldateien gelten für
jede Datei im Repo ([ADR 0014](docs/decisions/0014-regeldateien-always-on.md)).

## Erwartungen an eine Änderung

- Passenden Punkt in `ROADMAP.md` nachziehen (`[ ]` offen · `[~]` in Arbeit · `[x]` erledigt ·
  `[-]` zurückgestellt). Steht die Arbeit noch nicht drin, einen Punkt ergänzen.
- Entscheidungen, die schwer rückgängig zu machen sind oder eine ernsthafte Alternative verwerfen,
  bekommen einen neuen ADR in `docs/decisions/` plus eine Zeile in dessen Übersichtstabelle.
- Jede Entscheidung wird genau einmal beschrieben und sonst verlinkt — nicht wiederholen.

## Sprache

Code, Bezeichner und Commit-Nachrichten auf Englisch. Kommentare, Dokumentation, ADRs und alle
Strings in `res/values/strings.xml` auf Deutsch.

## Erstes Setup nach einem frischen Klon

`app/google-services.json` und der Release-Keystore stehen in `.gitignore` und gehören **niemals**
ins Repo — nicht umgehen. Beide müssen deshalb lokal beschafft werden:

- `app/google-services.json` kommt aus der Firebase Console: Projekt öffnen → Projekteinstellungen →
  Android-App `eu.sweetgeorgie.browniedo` → Datei herunterladen und nach `app/` legen. Ohne sie baut
  das Projekt nicht.
- Der Release-Keystore existiert nur lokal. Ohne ihn lassen sich Debug-Builds bauen und testen, nur
  kein signiertes Release (Phase 7 der `ROADMAP.md`).

## Bauen und testen

| Geändert | Prüfen mit |
|---|---|
| ViewModel-, Repository-, Mapper-Logik | `./gradlew.bat :app:testDebugUnitTest` |
| Compose-UI, Ressourcen, Manifest, Gradle | `./gradlew.bat :app:assembleDebug :app:lintDebug` |
| UI-Verhalten auf dem Gerät | `./gradlew.bat :app:connectedDebugAndroidTest` (Gerät oder laufender Emulator nötig) |
| `firestore.rules` | **kein Befehl** — von Hand über die Firebase Console veröffentlichen |

Warum die Security Rules von Hand veröffentlicht werden und `firestore.rules` im Repo-Root trotzdem
die Quelle der Wahrheit ist, steht in `ROADMAP.md` unter „Projektspezifische Vorgaben".
