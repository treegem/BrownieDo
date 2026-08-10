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
- Der Release-Keystore und die zugehörige `keystore.properties` existieren nur lokal. Ohne sie baut
  und testet alles wie gewohnt — nur `assembleRelease` liefert dann eine unsignierte APK. Wie beides
  angelegt wird, steht unten unter „Signiertes Release bauen".

## Bauen und testen

| Geändert | Prüfen mit |
|---|---|
| ViewModel-, Repository-, Mapper-Logik | `./gradlew.bat :app:testDebugUnitTest` |
| Compose-UI, Ressourcen, Manifest, Gradle | `./gradlew.bat :app:assembleDebug :app:lintDebug` |
| UI-Verhalten auf dem Gerät | `./gradlew.bat :app:connectedDebugAndroidTest` (Gerät oder laufender Emulator nötig) |
| `firestore.rules` | **kein Befehl** — von Hand über die Firebase Console veröffentlichen |

Warum die Security Rules von Hand veröffentlicht werden und `firestore.rules` im Repo-Root trotzdem
die Quelle der Wahrheit ist, steht in `ROADMAP.md` unter „Projektspezifische Vorgaben".

## Signiertes Release bauen

Die Zugangsdaten liest der Build aus `keystore.properties` im Repo-Root — nicht eingecheckt, Vorlage
mit den erwarteten Schlüsseln in `keystore.properties.example`, Begründung in
[ADR 0017](docs/decisions/0017-signatur-zugangsdaten-aus-keystore-properties.md). Fehlt die Datei,
bleibt der Signatur-Block aus und `assembleRelease` liefert eine unsignierte APK.

Einmalig einzurichten:

1. Keystore erzeugen, **außerhalb** des Repos:
   `keytool -genkeypair -v -keystore <pfad>/browniedo-release.jks -alias browniedo -keyalg RSA -keysize 4096 -validity 10000`
2. `keystore.properties` nach der Vorlage ausfüllen. Pfade mit `/` schreiben — der Backslash ist in
   `.properties`-Dateien ein Escape-Zeichen.
3. SHA-1 auslesen: `keytool -list -v -keystore <pfad>/browniedo-release.jks -alias browniedo`
   (die `SHA1`-Zeile, nicht `SHA256`).
4. Den SHA-1 in der Firebase Console hinterlegen: Projekteinstellungen → Meine Apps → Android-App
   `eu.sweetgeorgie.browniedo` → Fingerabdruck hinzufügen. Den Debug-Fingerabdruck stehen lassen.
5. `google-services.json` neu herunterladen und `app/google-services.json` ersetzen. Danach müssen
   **zwei** `certificate_hash`-Einträge drin sein. Wird dieser Schritt vergessen, schlägt der
   Google-Login in der signierten APK mit einer nichtssagenden Meldung fehl, siehe
   [ADR 0002](docs/decisions/0002-google-login-als-einziger-anbieter.md).

Danach je Release:

| Schritt | Befehl |
|---|---|
| Bauen | `./gradlew.bat :app:assembleRelease` (Ergebnis: `app/build/outputs/apk/release/app-release.apk`) |
| Installieren | `adb install -r app/build/outputs/apk/release/app-release.apk` |
| Prüfen | Google-Login auf dem Gerät durchspielen — der Build sagt darüber nichts aus |

**Jede weitere verteilte Fassung braucht einen höheren `versionCode`** in `app/build.gradle.kts`.
Verteilt wird eine APK, kein App Bundle: Ein AAB lässt sich ohne `bundletool` nicht per Sideload
installieren.

**Der Keystore ist unersetzlich.** Android behandelt eine anders signierte APK als andere App — geht
er verloren, muss die App auf beiden Geräten deinstalliert und neu eingerichtet werden. Datei und
Passwörter entsprechend sichern.
