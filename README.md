# BrownieDo

Eine private, geteilte ToDo-Liste für genau zwei Personen. Beide können jederzeit Aufgaben anlegen,
abhaken und ändern — auch offline; sobald wieder Verbindung besteht, gleicht sich der Stand von
selbst ab.

Android · Kotlin · Jetpack Compose (Material 3) · Firebase Firestore & Auth · minSdk 24

```bash
./gradlew.bat :app:assembleDebug
```

Der Build braucht `app/google-services.json`. Die Datei liegt bewusst nicht im Repo — woher sie
kommt, steht in [`AGENTS.md`](AGENTS.md).

## Weiter lesen

| Wo | Was |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Einstieg: Setup, Build- und Testbefehle, Sprache, Secrets |
| [`ROADMAP.md`](ROADMAP.md) | Vision, Stack, Fortschritt |
| [`.github/instructions/`](.github/instructions/) | Verbindliche Coding-, Architektur- und Naming-Regeln |
| [`docs/decisions/`](docs/decisions/README.md) | ADRs: warum eine Option gewonnen hat |
