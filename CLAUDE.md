# BrownieDo – Einstieg für Claude Code

BrownieDo ist eine private, geteilte ToDo-Liste für genau zwei Personen (Android, Kotlin, Jetpack
Compose, Firebase). Diese Datei ist nur ein Wegweiser — sie enthält selbst keine Regeln.

## Vor der Arbeit lesen

| Wo | Was |
|---|---|
| [`ROADMAP.md`](ROADMAP.md) | Vision, Stack, Fortschritt — das **Was** und **Warum überhaupt** |
| [`.github/instructions/`](.github/instructions/) | Verbindliche Coding-, Architektur- und Naming-Regeln — das **Wie** |
| [`docs/decisions/`](docs/decisions/README.md) | ADRs: warum eine Option gewonnen hat — das **Warum so** |

**Die Regeldateien werden nicht automatisch geladen.** GitHub Copilot zieht sie über die
`applyTo`-Globs in ihrem Frontmatter (beschrieben in
[`.github/copilot-instructions.md`](.github/copilot-instructions.md)); Claude Code tut das nicht.
Lies deshalb zu Beginn einer Aufgabe **alle** Dateien in `.github/instructions/`. Das Frontmatter
jeder Datei sagt, worauf sie zutrifft; die meisten gelten für jede Datei im Repo.

Die Regeln tragen Schweregrade (`MUST FIX`, `SHOULD FIX`, `CONSIDER`) — die sind ernst gemeint.

## Erwartungen an eine Änderung

- Passenden Punkt in `ROADMAP.md` nachziehen (`[ ]` offen · `[~]` in Arbeit · `[x]` erledigt ·
  `[-]` zurückgestellt). Steht die Arbeit noch nicht drin, einen Punkt ergänzen.
- Entscheidungen, die schwer rückgängig zu machen sind oder eine ernsthafte Alternative verwerfen,
  bekommen einen neuen ADR in `docs/decisions/` plus eine Zeile in dessen Übersichtstabelle.
- Jede Entscheidung wird genau einmal beschrieben und sonst verlinkt — nicht wiederholen.

## Sprache

Code, Bezeichner und Commit-Nachrichten auf Englisch. Kommentare, Dokumentation, ADRs und alle
Strings in `res/values/strings.xml` auf Deutsch.

## Bauen und testen

```bash
./gradlew.bat :app:testDebugUnitTest
```

```bash
./gradlew.bat :app:assembleDebug :app:lintDebug
```

Instrumentierte Tests brauchen ein Gerät oder einen laufenden Emulator:

```bash
./gradlew.bat :app:connectedDebugAndroidTest
```

## Niemals ins Repo

`app/google-services.json` und der Release-Keystore. Beide stehen in `.gitignore` — nicht
umgehen. `firestore.rules` im Repo-Root ist die Quelle der Wahrheit für die Security Rules;
veröffentlicht wird von Hand über die Firebase Console.
